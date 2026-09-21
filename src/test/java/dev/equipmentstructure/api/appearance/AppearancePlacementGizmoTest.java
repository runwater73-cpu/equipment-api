package dev.equipmentstructure.api.appearance;

import dev.equipmentstructure.api.client.appearance.AppearancePlacementGizmo;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppearancePlacementGizmoTest {
    @Test void projectedMovementMatchesLocalUnitsAcrossCamerasAndZooms() {
        for (float zoom : new float[]{0.5F, 2, 8}) {
            for (float yaw : new float[]{0.3F, 0.8F, 1.4F}) {
                var matrix = new Matrix4f().translation(160, 110, 0).scale(zoom, -zoom, zoom).rotateY(yaw).rotateX(0.4F);
                var gizmo = new AppearancePlacementGizmo();
                gizmo.update(matrix, 2, 3, -1);
                Vector3f expected = matrix.transformPosition(2, 3, -1, new Vector3f());
                assertTrue(expected.equals(gizmo.origin(), 1e-5F));
                for (int axis = 0; axis < 3; axis++) {
                    if (!gizmo.visible(axis)) continue;
                    var step = matrix.transformDirection(new Vector3f().setComponent(axis, 4));
                    assertEquals(4, gizmo.amount(axis, step.x, step.y), 1e-5);
                    var end = gizmo.end(axis);
                    assertEquals(axis, gizmo.hit(end.x, end.y));
                }
            }
        }
    }

    @Test void endOnAxesAndClearedFramesCannotBeDragged() {
        var gizmo = new AppearancePlacementGizmo();
        gizmo.update(new Matrix4f().scale(2), 0, 0, 0);
        assertFalse(gizmo.visible(2));
        assertEquals(0, gizmo.amount(2, 50, 50));
        assertEquals(-1, gizmo.hit(1000, 1000));
        gizmo.clear();
        assertFalse(gizmo.valid());
        assertEquals(-1, gizmo.hit(34, 0));
        assertEquals(0, gizmo.amount(0, 5, 0));
    }

    @Test void freeDragTracksScreenPixelsWithoutChangingPreviewDepth() {
        for (float zoom : new float[]{0.5F, 2, 8}) {
            for (float yaw : new float[]{0, 0.8F, 1.4F}) {
                var frame = new Matrix4f().translation(160, 110, 150).scale(zoom, -zoom, zoom)
                        .rotateY(yaw).rotateX(0.4F).scale(1.5F, 0.7F, 2);
                var gizmo = new AppearancePlacementGizmo();
                gizmo.update(frame, 2, 3, -1);
                var offset = gizmo.planeDelta(12, -7);
                var moved = frame.transformPosition(new Vector3f(2, 3, -1).add(offset));
                assertTrue(moved.equals(gizmo.origin().add(12, -7, 0), 1e-4F));
            }
        }
        var gizmo = new AppearancePlacementGizmo();
        gizmo.update(new Matrix4f().scale(0), 0, 0, 0);
        assertFalse(gizmo.valid());
        assertEquals(new Vector3f(), gizmo.planeDelta(12, -7));
        gizmo.update(new Matrix4f(), 0, 0, 0);
        assertEquals(new Vector3f(), gizmo.planeDelta(Double.NaN, 0));
        gizmo.clear();
        assertEquals(new Vector3f(), gizmo.planeDelta(12, -7));
    }
}
