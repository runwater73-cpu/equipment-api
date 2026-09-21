package dev.equipmentstructure.api.client.appearance;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.appearance.AppearancePlan;
import dev.equipmentstructure.api.appearance.AppearanceSupport;
import dev.equipmentstructure.api.internal.ExtensionGuard;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import org.joml.Quaternionf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit registry for calibrated appearance assets. Assets may be data-driven
 * 2D sprites or custom 3D renderers; render mode remains placement metadata. */
@OnlyIn(Dist.CLIENT)
public final class AppearanceModelAssetRegistry {
    private static final Map<ResourceLocation, AppearanceModelAssetRenderer> RENDERERS =
            new ConcurrentHashMap<>();
    private static final ExtensionGuard<ResourceLocation> GUARD =
            new ExtensionGuard<>("3D appearance asset");

    private AppearanceModelAssetRegistry() {}

    public static void register(ResourceLocation asset, AppearanceModelAssetRenderer renderer) {
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(renderer, "renderer");
        var previous = RENDERERS.putIfAbsent(asset, renderer);
        if (previous != null && previous != renderer) {
            throw new IllegalStateException("3D appearance asset already registered: " + asset);
        }
    }

    public static Optional<AppearanceModelAssetRenderer> get(ResourceLocation asset) {
        Objects.requireNonNull(asset, "asset");
        return Optional.ofNullable(RENDERERS.get(asset));
    }

    /** Returns a stable snapshot of registered model asset IDs for support builders. */
    public static Set<ResourceLocation> assets() {
        return Set.copyOf(RENDERERS.keySet());
    }

    public static boolean unregister(ResourceLocation asset) {
        Objects.requireNonNull(asset, "asset");
        var removed = RENDERERS.remove(asset);
        if (removed != null) {
            GUARD.clear();
            AppearanceRuntime.clearCache();
            return true;
        }
        return false;
    }

    /** Removes a resource-owned renderer only if that exact registration is still active. */
    static boolean unregister(ResourceLocation asset, AppearanceModelAssetRenderer owner) {
        if (!RENDERERS.remove(asset, owner)) return false;
        GUARD.clear();
        AppearanceRuntime.clearCache();
        return true;
    }

    /** Builds a support snapshot for adapters using this registry's assets. */
    public static AppearanceSupport support(Set<ResourceLocation> capabilities,
                                             boolean canReplaceElements) {
        return new AppearanceSupport(AppearanceResourceReloadListener.generation(),
                Set.copyOf(RENDERERS.keySet()), capabilities, canReplaceElements);
    }

    /** Renders all ready placements, applying each placement's calibrated transform. */
    public static int render(AppearancePlan plan, EquipmentAppearanceScene scene,
                             PoseStack poseStack, MultiBufferSource buffers,
                             int packedLight, int packedOverlay) {
        return render(plan, null, scene, poseStack, buffers, packedLight, packedOverlay);
    }

    /**
     * Resource-aware variant. When a support snapshot is supplied, stale plans
     * and assets not prepared by the current adapter are skipped safely.
     */
    public static int render(AppearancePlan plan, AppearanceSupport support,
                             EquipmentAppearanceScene scene, PoseStack poseStack,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        return render(plan, support, scene, poseStack, buffers, packedLight, packedOverlay, AppearanceRenderSubject.empty());
    }

    public static int render(AppearancePlan plan, AppearanceSupport support,
                             EquipmentAppearanceScene scene, PoseStack poseStack,
                             MultiBufferSource buffers, int packedLight, int packedOverlay,
                             AppearanceRenderSubject subject) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(poseStack, "poseStack");
        Objects.requireNonNull(buffers, "buffers");
        Objects.requireNonNull(subject, "subject");
        if (plan.generation() != AppearanceResourceReloadListener.generation()
                || support != null && plan.generation() != support.generation()) return 0;
        int rendered = 0;
        var placements = plan.orderedPlacements();
        for (var placement : placements) {
            if (renderPlacement(placement, support, scene, poseStack, buffers, packedLight, packedOverlay, subject)) rendered++;
        }
        return rendered;
    }

    /** The owning adapter validates the plan generation before selecting individual bone frames. */
    static boolean renderPlacement(AppearancePlan.Placement placement, AppearanceSupport support,
                                   EquipmentAppearanceScene scene, PoseStack poseStack,
                                   MultiBufferSource buffers, int packedLight, int packedOverlay) {
        return renderPlacement(placement, support, scene, poseStack, buffers, packedLight, packedOverlay, AppearanceRenderSubject.empty());
    }

    static boolean renderPlacement(AppearancePlan.Placement placement, AppearanceSupport support,
                                   EquipmentAppearanceScene scene, PoseStack poseStack,
                                   MultiBufferSource buffers, int packedLight, int packedOverlay,
                                   AppearanceRenderSubject subject) {
        if (!placement.visible()) return false;
        if (support != null && !support.canReplaceElements() && !placement.replacedElements().isEmpty()) return false;
        var renderer = RENDERERS.get(placement.asset());
        if (renderer == null) return false;
        if (support != null && !support.preparedAssets().contains(placement.asset())) return false;
        return GUARD.call(placement.asset(), () -> {
            // An addon may throw or leave an unbalanced stack. Never expose the caller's stack.
            PoseStack local = new PoseStack();
            local.mulPose(poseStack.last().pose());
            local.last().normal().set(poseStack.last().normal());
            applyTransform(local, placement, subject);
            MultiBufferSource targetBuffers = buffers instanceof AppearancePreviewSelection preview
                    ? preview.forPart(placement.slotId()) : buffers;
            renderer.render(new AppearanceModelAssetRenderContext(
                    placement, scene, local, targetBuffers, packedLight, packedOverlay, subject));
            return true;
        }, false);
    }

    public static void clear() {
        RENDERERS.clear();
        GUARD.clear();
        AppearanceRuntime.clearCache();
    }

    private static void applyTransform(PoseStack poseStack, AppearancePlan.Placement placement,
                                       AppearanceRenderSubject subject) {
        var transform = placement.transform();
        var motion = placement.motion();
        if (motion.isPresent() && !subject.editorPreview()) {
            double elapsedSeconds = subject.wearer()
                    .map(entity -> (entity.level().getGameTime() + subject.partialTick()) / 20.0D)
                    .orElseGet(() -> {
                        var level = net.minecraft.client.Minecraft.getInstance().level;
                        return level == null ? Util.getMillis() / 1000.0D
                                : (level.getGameTime() + subject.partialTick()) / 20.0D;
                    });
            transform = motion.get().apply(transform, elapsedSeconds);
        }
        var position = transform.position();
        var rotation = transform.rotation();
        poseStack.translate(position.x(), position.y(), position.z());
        poseStack.mulPose(new Quaternionf((float) rotation.x(), (float) rotation.y(),
                (float) rotation.z(), (float) rotation.w()));
        float scale = (float) placement.scale();
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0F, 0.0F, placement.renderProfile().depthBias());
    }
}
