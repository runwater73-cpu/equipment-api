package dev.equipmentstructure.api.client.appearance;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.appearance.AppearanceSupport;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** One stable entry point for scene adapters implementing the hybrid strategy. */
public final class EquipmentAppearanceRenderPipeline {
    private EquipmentAppearanceRenderPipeline() {}

    /**
     * Renders an equipment appearance according to {@code mode}. Overlay mode
     * never suppresses the original model; replace mode returns whether a host
     * renderer rendered the complete model.
     */
    public static int render(ItemStack stack, AppearanceSupport support,
                             EquipmentAppearanceScene scene, EquipmentAppearanceRenderMode mode,
                             PoseStack poseStack, MultiBufferSource buffers,
                             int packedLight, int packedOverlay) {
        Objects.requireNonNull(mode, "mode");
        return switch (mode) {
            case OVERLAY -> EquipmentAppearanceRendererRegistry.renderOverlay(
                    stack, support, scene, poseStack, buffers, packedLight, packedOverlay);
            case REPLACE -> EquipmentAppearanceRendererRegistry.render(
                    stack, support, scene, poseStack, buffers, packedLight, packedOverlay) ? 1 : 0;
        };
    }
}
