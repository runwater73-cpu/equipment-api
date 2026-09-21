package dev.equipmentstructure.api.appearance;

import dev.equipmentstructure.api.client.appearance.AppearanceOrbitGizmo;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppearanceOrbitGizmoTest {
    private static final AppearanceOrbitMotion MOTION = new AppearanceOrbitMotion(AppearanceVector.ZERO,
            new AppearanceVector(0, 1, 0), 90, AppearanceOrbitMotion.Direction.POSITIVE, 0, true);
    private static final AppearanceTransform BASE = new AppearanceTransform(new AppearanceVector(4, 3, 0), AppearanceRotation.IDENTITY);
    @Test void dragAtOppositeSidesExpandsRadiusInTheSameWayAndKeepsAxisHeight() {
        var gizmo = new AppearanceOrbitGizmo(new Matrix4f().scale(10), BASE, MOTION);
        var a = gizmo.radiusDelta(0, 10, 0); var b = gizmo.radiusDelta(180, -10, 0);
        assertEquals(1, a.x(), 1e-6); assertEquals(a.x(), b.x(), 1e-6);
        assertEquals(0, a.y(), 1e-6); assertEquals(0, a.z(), 1e-6);
    }
    @Test void tiltMovesPlaneAndBaseTogetherWithoutChangingRadiusOrCenter() {
        var gizmo = new AppearanceOrbitGizmo(new Matrix4f().rotateY(0.5f).scale(10, -10, 10), BASE, MOTION);
        var turn = gizmo.tilt(12, 20);
        var motion = MOTION.resolve(AppearanceTransform.IDENTITY,
                new AppearanceMotionSettings(AppearanceMotionSettings.Center.HOST, turn));
        var tilted = new AppearanceTransform(turn.apply(BASE.position()), BASE.rotation());
        var path = new AppearanceOrbitGizmo(new Matrix4f(), tilted, motion);
        var radial = tilted.position().add(path.center().negate());
        assertEquals(4, Math.sqrt(radial.x()*radial.x()+radial.y()*radial.y()+radial.z()*radial.z()), 1e-6);
        assertEquals(AppearanceVector.ZERO, motion.pivot());
        assertNotEquals(MOTION.axis(), motion.axis());
        var point = path.points().get(31);
        assertEquals(0, path.hit(point.x, point.y).distance(), 1e-6);
    }
}
