package dev.equipmentstructure.api.client.appearance;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;
import java.util.Set;

/** Animated humanoid anchor, independent of the logical equipment component slot. */
public enum ArmorAttachment {
    HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG;

    public ModelPart part(HumanoidModel<?> model) {
        return switch (this) {
            case HEAD -> model.head;
            case BODY -> model.body;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_LEG -> model.leftLeg;
            case RIGHT_LEG -> model.rightLeg;
        };
    }

    /** Applies this humanoid bone's current animation to an attachment pose. */
    public void apply(HumanoidModel<?> model, PoseStack poseStack) {
        part(model).translateAndRotate(poseStack);
    }

    /** Disabling animation retains the model's rest anchor, including distinct left/right limbs. */
    public void apply(HumanoidModel<?> model, PoseStack poseStack, boolean followAnimation) {
        AppearanceBoneTransform.apply(part(model), poseStack, followAnimation);
    }

    public boolean supports(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> this == HEAD;
            case CHEST -> this == BODY || this == LEFT_ARM || this == RIGHT_ARM;
            case LEGS -> this == BODY || this == LEFT_LEG || this == RIGHT_LEG;
            case FEET -> this == LEFT_LEG || this == RIGHT_LEG;
            default -> false;
        };
    }

    /** Leg/foot defaults repeat the same local attachment on both legs, without mirroring. */
    public static Set<ArmorAttachment> defaults(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> Set.of(HEAD);
            case CHEST -> Set.of(BODY);
            case LEGS, FEET -> Set.of(LEFT_LEG, RIGHT_LEG);
            default -> Set.of();
        };
    }
}
