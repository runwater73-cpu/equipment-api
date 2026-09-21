package dev.equipmentstructure.api.client.appearance;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;

/** Applies an author's model part frame without changing its animation state.
 * Call after the parent/ancestor transforms; coordinates use vanilla model units. */
public final class AppearanceBoneTransform {
    private AppearanceBoneTransform() {}

    public static void apply(ModelPart part, PoseStack poses, boolean followAnimation) {
        if (followAnimation) {
            part.translateAndRotate(poses);
        } else {
            var rest = part.getInitialPose();
            poses.translate(rest.x / 16F, rest.y / 16F, rest.z / 16F);
            poses.mulPose(new Quaternionf().rotationZYX(rest.zRot, rest.yRot, rest.xRot));
        }
    }
}
