package dev.equipmentstructure.api.client.appearance;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AppearanceBoneTransformTest {
    @Test void restPoseKeepsLeftAndRightAnchorsWhileIgnoringAnimationAndScale() {
        for (int side : new int[]{-1, 1}) {
            var part = new ModelPart(List.of(), Map.of());
            var rest = PartPose.offsetAndRotation(side * 5, 2, 1, .1f, .2f, .3f);
            part.setInitialPose(rest);
            part.loadPose(rest);
            var expectedRest = new PoseStack();
            part.translateAndRotate(expectedRest);
            part.x += 9; part.yRot += .8f; part.xScale = 2;
            var saved = part.storePose();
            var animated = new PoseStack();
            part.translateAndRotate(animated);
            var actual = new PoseStack();
            AppearanceBoneTransform.apply(part, actual, true);
            assertTrue(animated.last().pose().equals(actual.last().pose(), 1e-6f));
            assertTrue(animated.last().normal().equals(actual.last().normal(), 1e-6f));
            actual = new PoseStack();
            AppearanceBoneTransform.apply(part, actual, false);
            assertTrue(expectedRest.last().pose().equals(actual.last().pose(), 1e-6f));
            assertTrue(expectedRest.last().normal().equals(actual.last().normal(), 1e-6f));
            assertEquals(saved.x, part.x); assertEquals(saved.yRot, part.yRot); assertEquals(2, part.xScale);
        }
    }
}
