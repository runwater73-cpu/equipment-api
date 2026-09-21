package dev.equipmentstructure.api.client.appearance;

import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.appearance.AppearanceSupport;
import dev.equipmentstructure.api.internal.ExtensionGuard;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.Collection;

/**
 * Client registry for complete host-equipment appearance renderers.
 * Registration is keyed by the persisted host definition ID rather than an
 * Item instance, so one renderer can serve item variants sharing a structure.
 */
@OnlyIn(Dist.CLIENT)
public final class EquipmentAppearanceRendererRegistry {
    private static final Map<ResourceLocation, Supplier<EquipmentAppearanceRenderer>> PENDING =
            new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, EquipmentAppearanceRenderer> ACTIVE =
            new ConcurrentHashMap<>();
    private static final ExtensionGuard<ResourceLocation> GUARD =
            new ExtensionGuard<>("Equipment appearance renderer");
    private static final ExtensionGuard<ResourceLocation> FACTORY_GUARD =
            new ExtensionGuard<>("Equipment appearance renderer factory");

    private EquipmentAppearanceRendererRegistry() {}

    public static void register(ResourceLocation hostId,
                                Supplier<EquipmentAppearanceRenderer> rendererFactory) {
        Objects.requireNonNull(hostId, "hostId");
        Objects.requireNonNull(rendererFactory, "rendererFactory");
        Supplier<EquipmentAppearanceRenderer> previous = PENDING.putIfAbsent(hostId, rendererFactory);
        if (previous != null && previous != rendererFactory) {
            throw new IllegalStateException("Appearance renderer already registered for host: " + hostId);
        }
    }

    /** Registers one renderer factory for several compatible host templates. */
    public static void registerAll(Collection<ResourceLocation> hostIds,
                                   Supplier<EquipmentAppearanceRenderer> rendererFactory) {
        Objects.requireNonNull(hostIds, "hostIds");
        Objects.requireNonNull(rendererFactory, "rendererFactory");
        hostIds.forEach(hostId -> register(hostId, rendererFactory));
    }

    /** Materializes factories once during client setup. */
    public static void load() {
        PENDING.keySet().forEach(EquipmentAppearanceRendererRegistry::get);
    }

    public static Optional<EquipmentAppearanceRenderer> get(ResourceLocation hostId) {
        Objects.requireNonNull(hostId, "hostId");
        Supplier<EquipmentAppearanceRenderer> factory = PENDING.get(hostId);
        if (factory == null) return Optional.empty();
        return Optional.ofNullable(FACTORY_GUARD.call(hostId,
                () -> ACTIVE.computeIfAbsent(hostId, ignored -> Objects.requireNonNull(factory.get(),
                        "renderer factory returned null for " + hostId)), null));
    }

    /**
     * Attempts a complete host render. A false result means the caller must
     * continue with the original item model.
     */
    public static boolean render(ItemStack stack, AppearanceSupport support,
                                  EquipmentAppearanceScene scene, PoseStack poseStack,
                                  MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(support, "support");
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(poseStack, "poseStack");
        Objects.requireNonNull(buffers, "buffers");
        Optional<EquipmentStructure> structure = EquipmentStructureApi.structure(stack);
        if (structure.isEmpty()) return false;
        Optional<EquipmentAppearanceRenderer> renderer = get(structure.get().hostId());
        if (renderer.isEmpty()) return false;
        var plan = AppearanceRuntime.resolve(structure.get(), support);
        return GUARD.call(structure.get().hostId(), () -> {
            if (!renderer.get().supports(scene)) return false;
            PoseStack local = new PoseStack();
            local.mulPose(poseStack.last().pose());
            local.last().normal().set(poseStack.last().normal());
            return renderer.get().render(new EquipmentAppearanceRenderContext(stack, structure.get(), plan,
                    support, scene, local, buffers, packedLight, packedOverlay));
        }, false);
    }

    /**
     * Renders only registered component assets after the original item model.
     * Unlike {@link #render}, this method never replaces or suppresses the
     * vanilla/third-party model and therefore works as an additive equipment overlay.
     */
    public static int renderOverlay(ItemStack stack, AppearanceSupport support,
                                    EquipmentAppearanceScene scene, PoseStack poseStack,
                                    MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(support, "support");
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(poseStack, "poseStack");
        Objects.requireNonNull(buffers, "buffers");
        Optional<EquipmentStructure> structure = EquipmentStructureApi.structure(stack);
        if (structure.isEmpty()) return 0;
        var overlaySupport = new AppearanceSupport(support.generation(), support.preparedAssets(),
                support.capabilities(), false);
        var plan = AppearanceRuntime.resolve(structure.get(), overlaySupport);
        if (plan.generation() != overlaySupport.generation()) return 0;
        return AppearanceModelAssetRegistry.render(plan, overlaySupport, scene, poseStack, buffers,
                packedLight, packedOverlay, AppearanceRenderSubject.equipment(stack));
    }

    public static void clear() {
        PENDING.clear();
        ACTIVE.clear();
        GUARD.clear();
        FACTORY_GUARD.clear();
    }
}
