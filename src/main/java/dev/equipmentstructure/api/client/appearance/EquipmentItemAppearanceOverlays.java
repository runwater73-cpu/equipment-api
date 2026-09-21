package dev.equipmentstructure.api.client.appearance;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.appearance.AppearanceSupport;
import dev.equipmentstructure.api.appearance.AppearanceVisibilityStorage;
import dev.equipmentstructure.api.internal.ExtensionGuard;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.BiConsumer;
import org.joml.Matrix4f;
import dev.equipmentstructure.api.appearance.AppearancePlan;
import dev.equipmentstructure.api.appearance.AppearanceTransform;
import org.joml.Quaternionf;

/** Item overlays and saved visibility; vanilla still owns camera transforms and model selection. */
public final class EquipmentItemAppearanceOverlays {
    public static final Set<ItemDisplayContext> ITEM_CONTEXTS = Set.of(
            ItemDisplayContext.GUI, ItemDisplayContext.GROUND, ItemDisplayContext.FIXED,
            ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
            ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
    private static final Map<ResourceLocation, Binding> BINDINGS = new ConcurrentHashMap<>();
    private static final ExtensionGuard<ResourceLocation> GUARD = new ExtensionGuard<>("Item appearance overlay");
    private static final Set<ResourceLocation> AUTOMATIC_DISABLED = ConcurrentHashMap.newKeySet();
    private static final Binding DEFAULT_BINDING = new Binding(defaultFrames(),
            () -> AppearanceModelAssetRegistry.support(Set.of(), false));
    private static final Map<ResourceLocation, Long> DIAGNOSTIC_LOGS = new ConcurrentHashMap<>();
    private static final long DIAGNOSTIC_INTERVAL_NANOS = 5_000_000_000L;
    private static ItemStack previewStack;
    private static BiConsumer<Matrix4f, AppearancePlan> previewCapture;

    private EquipmentItemAppearanceOverlays() {}

    /** Client render-thread scope, matched by stack identity and restored even when rendering fails. */
    public static void capturePreview(ItemStack stack, BiConsumer<Matrix4f, AppearancePlan> capture, Runnable render) {
        ItemStack previousStack = previewStack;
        var previousCapture = previewCapture;
        previewStack = stack;
        previewCapture = capture;
        try { render.run(); }
        finally { previewStack = previousStack; previewCapture = previousCapture; }
    }

    /** Register only for hosts whose attachment coordinates match their item model. */
    public static void register(ResourceLocation host, Set<ItemDisplayContext> contexts,
                                float modelUnitsPerBlock, Supplier<AppearanceSupport> support) {
        Objects.requireNonNull(host, "host");
        var frame = new Frame(modelUnitsPerBlock, AppearanceTransform.IDENTITY);
        var frames = new java.util.EnumMap<ItemDisplayContext, Frame>(ItemDisplayContext.class);
        contexts.forEach(context -> frames.put(context, frame));
        registerFrames(host, frames, support);
    }

    /** Explicit scene calibration for hosts that switch model origins (e.g. held tridents). */
    public static void registerFrames(ResourceLocation host, Map<ItemDisplayContext, Frame> frames,
                                      Supplier<AppearanceSupport> support) {
        Objects.requireNonNull(host, "host");
        var binding = new Binding(frames, support);
        if (BINDINGS.putIfAbsent(host, binding) != null) {
            throw new IllegalStateException("Item overlay already registered for " + host);
        }
    }

    public static boolean unregister(ResourceLocation host) {
        Objects.requireNonNull(host, "host");
        GUARD.forget(host);
        return BINDINGS.remove(host) != null;
    }

    /** Opt out when the host already draws attachments itself. Explicit registrations still take priority. */
    public static void setAutomaticEnabled(ResourceLocation host, boolean enabled) {
        Objects.requireNonNull(host, "host");
        if (enabled) AUTOMATIC_DISABLED.remove(host);
        else AUTOMATIC_DISABLED.add(host);
    }

    /** Read-only readiness check, not permission to skip a draw. Use renderBeforeItem's result for actual hiding. */
    public static boolean hidesOriginal(ItemStack stack, ItemDisplayContext context) {
        if (stack.isEmpty() || AppearanceVisibilityStorage.originalVisible(stack)) return false;
        var structure = EquipmentStructureApi.structure(stack).orElse(null);
        if (structure == null || structure.components().isEmpty()) return false;
        var binding = BINDINGS.get(structure.hostId());
        if (binding == null || !binding.frames().containsKey(context)) return false;
        return GUARD.call(structure.hostId(), () -> {
            var support = overlaySupport(binding);
            var plan = AppearanceRuntime.resolve(structure, support);
            return complete(plan, support.generation(), 0, false);
        }, false);
    }

    /** True only after all visible callbacks completed. A hidden part is an intentional omission. */
    static boolean complete(AppearancePlan plan, long generation, int rendered, boolean checkDraws) {
        return plan.generation() == generation
                && generation == AppearanceResourceReloadListener.generation()
                && !plan.placements().isEmpty()
                && plan.outcomes().stream().allMatch(outcome -> outcome.status() == AppearancePlan.Status.READY)
                && plan.placements().stream().allMatch(part -> AppearanceModelAssetRegistry.get(part.asset()).isPresent())
                && (!checkDraws || rendered == plan.placements().stream().filter(AppearancePlan.Placement::visible).count());
    }

    /**
     * Called before vanilla acquires any vertex consumers, only for explicit whole-item hiding.
     * Even if a callback fails, the original draw continues and attachments are not retried this draw.
     */
    public static RenderResult renderBeforeItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                                                MultiBufferSource buffers, int light, int overlay) {
        if (stack.isEmpty() || AppearanceVisibilityStorage.originalVisible(stack)) return null;
        var structure = EquipmentStructureApi.structure(stack).orElse(null);
        if (structure == null || structure.components().isEmpty()) return null;
        var binding = BINDINGS.get(structure.hostId());
        if (binding == null || !binding.frames().containsKey(context)) return null;
        // A partial plan must use the normal post-item overlay path. Drawing it before the
        // original model would let the original geometry cover the ready attachments, while
        // the non-null pre-render result would also suppress the post-item retry.
        if (!hidesOriginal(stack, context)) return null;
        return render(stack, context, poses, buffers, light, overlay, binding);
    }

    public record RenderResult(int rendered, boolean hideOriginal) {}

    private static AppearanceSupport overlaySupport(Binding binding) {
        var prepared = Objects.requireNonNull(binding.support().get(), "support");
        // Whole-item visibility does not grant permission to replace named elements of other adapters.
        return prepared.canReplaceElements()
                ? new AppearanceSupport(prepared.generation(), prepared.preparedAssets(), prepared.capabilities(), false)
                : prepared;
    }

    /** Called once after all vanilla item render passes, before the item-local pose is popped. */
    public static int renderAfterItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                                      MultiBufferSource buffers, int light, int overlay) {
        // Manual callers must opt into standard-model coordinates explicitly via the overload.
        return renderAfterItem(stack, context, poses, buffers, light, overlay, false);
    }

    public static int renderAfterItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                                      MultiBufferSource buffers, int light, int overlay, boolean standardBakedModel) {
        if (stack.isEmpty()) return 0;
        var structure = EquipmentStructureApi.structure(stack).orElse(null);
        if (structure == null || structure.components().isEmpty()) return 0;
        var binding = binding(structure.hostId(), standardBakedModel);
        if (binding == null || !binding.frames().containsKey(context)) return 0;
        return render(stack, context, poses, buffers, light, overlay, binding).rendered();
    }

    private static RenderResult render(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                                       MultiBufferSource buffers, int light, int overlay, Binding binding) {
        var structure = EquipmentStructureApi.structure(stack).orElseThrow();
        return GUARD.call(structure.hostId(), () -> {
            AppearanceSupport support = overlaySupport(binding);
            var plan = AppearanceRuntime.resolve(structure, support);
            if (plan.placements().isEmpty()) {
                diagnostic(structure.hostId(), "no ready placements; outcomes=" + plan.outcomes().size());
                return new RenderResult(0, false);
            }
            PoseStack local = new PoseStack();
            local.mulPose(poses.last().pose());
            local.last().normal().set(poses.last().normal());
            // Vanilla has already translated from its model centre to the [0,1] block cube.
            local.translate(0.5, 0.5, 0.5);
            var frame = binding.frames().get(context);
            float scale = 1.0F / frame.modelUnitsPerBlock();
            local.scale(scale, scale, scale);
            var offset = frame.offset();
            local.translate(offset.position().x(), offset.position().y(), offset.position().z());
            var rotation = offset.rotation();
            local.mulPose(new Quaternionf((float) rotation.x(), (float) rotation.y(), (float) rotation.z(), (float) rotation.w()));
            if (stack == previewStack && previewCapture != null) {
                previewCapture.accept(new Matrix4f(local.last().pose()), plan);
            }
            var subject = stack == previewStack
                    ? AppearanceRenderSubject.editor(stack)
                    : AppearanceRenderSubject.item(stack, context, currentPartialTick());
            int rendered = AppearanceModelAssetRegistry.render(plan, support, EquipmentAppearanceScene.from(context),
                    local, buffers, light, overlay, subject);
            diagnostic(structure.hostId(), "placements=" + plan.placements().size() + ", rendered=" + rendered
                    + ", motion=" + plan.placements().stream().filter(value -> value.motion().isPresent()).count());
            return new RenderResult(rendered, binding != DEFAULT_BINDING
                    && !AppearanceVisibilityStorage.originalVisible(stack)
                    && complete(plan, support.generation(), rendered, true));
        }, new RenderResult(0, false));
    }

    private static float currentPartialTick() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getTimer().getGameTimeDeltaPartialTick(true);
    }

    private static void diagnostic(ResourceLocation host, String message) {
        if (!Boolean.getBoolean("equipmentStructureApi.debugAppearance")) return;
        long now = System.nanoTime();
        Long previous = DIAGNOSTIC_LOGS.putIfAbsent(host, now);
        if (previous != null && now - previous < DIAGNOSTIC_INTERVAL_NANOS) return;
        DIAGNOSTIC_LOGS.put(host, now);
        EquipmentStructureApiMod.LOGGER.debug("Appearance item render {}: {}", host, message);
    }

    public static void clear() {
        BINDINGS.clear();
        AUTOMATIC_DISABLED.clear();
        DIAGNOSTIC_LOGS.clear();
        GUARD.clear();
    }

    static Binding binding(ResourceLocation host, boolean standardBakedModel) {
        Binding registered = BINDINGS.get(host);
        if (registered != null) return registered;
        return standardBakedModel && !AUTOMATIC_DISABLED.contains(host) ? DEFAULT_BINDING : null;
    }

    private static Map<ItemDisplayContext, Frame> defaultFrames() {
        var frames = new java.util.EnumMap<ItemDisplayContext, Frame>(ItemDisplayContext.class);
        var frame = new Frame(16, AppearanceTransform.IDENTITY);
        ITEM_CONTEXTS.forEach(context -> frames.put(context, frame));
        return Map.copyOf(frames);
    }

    /** Offset is expressed in model units, applied before the placement and after model scaling. */
    public record Frame(float modelUnitsPerBlock, AppearanceTransform offset) {
        public Frame {
            Objects.requireNonNull(offset, "offset");
            if (!Float.isFinite(modelUnitsPerBlock) || modelUnitsPerBlock <= 0
                    || !Float.isFinite(1.0F / modelUnitsPerBlock)) {
                throw new IllegalArgumentException("modelUnitsPerBlock must have a finite positive reciprocal");
            }
        }
    }

    record Binding(Map<ItemDisplayContext, Frame> frames, Supplier<AppearanceSupport> support) {
        Binding {
            frames = Map.copyOf(frames);
            if (frames.isEmpty() || !ITEM_CONTEXTS.containsAll(frames.keySet())) {
                throw new IllegalArgumentException("Only explicit item display contexts are supported");
            }
            Objects.requireNonNull(support, "support");
        }
    }
}
