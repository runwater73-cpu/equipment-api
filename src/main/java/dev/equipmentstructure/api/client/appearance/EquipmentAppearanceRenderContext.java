package dev.equipmentstructure.api.client.appearance;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.appearance.AppearancePlan;
import dev.equipmentstructure.api.appearance.AppearanceSupport;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Immutable input for one host-equipment appearance render call. */
public record EquipmentAppearanceRenderContext(
        ItemStack stack,
        EquipmentStructure structure,
        AppearancePlan plan,
        AppearanceSupport support,
        EquipmentAppearanceScene scene,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
) {
    public EquipmentAppearanceRenderContext {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(support, "support");
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(poseStack, "poseStack");
        Objects.requireNonNull(buffers, "buffers");
        stack = stack.copy();
    }

    @Override
    public ItemStack stack() {
        return stack.copy();
    }

    /** Draws all registered model assets in this plan using their calibrated transforms. */
    public int renderModelAssets() {
        return AppearanceModelAssetRegistry.render(plan, support, scene, poseStack, buffers,
                packedLight, packedOverlay, AppearanceRenderSubject.equipment(stack));
    }

    /** True when the resolved placement authoritatively hides this host element in REPLACE mode. */
    public boolean replaces(net.minecraft.resources.ResourceLocation element) {
        Objects.requireNonNull(element, "element");
        return support.canReplaceElements() && plan.hiddenOriginalElements().contains(element);
    }
}
