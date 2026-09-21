package dev.equipmentstructure.api.client.appearance;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.appearance.AppearancePlan;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.Objects;

/** Immutable input for one prepared Blockbench/model asset render call. */
public record AppearanceModelAssetRenderContext(
        AppearancePlan.Placement placement,
        EquipmentAppearanceScene scene,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay,
        AppearanceRenderSubject subject
) {
    public AppearanceModelAssetRenderContext {
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(poseStack, "poseStack");
        Objects.requireNonNull(buffers, "buffers");
        Objects.requireNonNull(subject, "subject");
    }

    /** Existing custom adapters may omit runtime source information. */
    public AppearanceModelAssetRenderContext(AppearancePlan.Placement placement, EquipmentAppearanceScene scene,
                                           PoseStack poseStack, MultiBufferSource buffers,
                                           int packedLight, int packedOverlay) {
        this(placement, scene, poseStack, buffers, packedLight, packedOverlay, AppearanceRenderSubject.empty());
    }

    public java.util.Optional<dev.equipmentstructure.api.EquipmentComponentInstance> component() {
        return subject.component(placement.slotId()).filter(value -> value.id().equals(placement.componentId()));
    }
}
