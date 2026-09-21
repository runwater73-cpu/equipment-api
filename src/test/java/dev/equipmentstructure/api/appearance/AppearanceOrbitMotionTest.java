package dev.equipmentstructure.api.appearance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AppearanceOrbitMotionTest {
    @Test
    void diagonalOrbitAxisIsNormalizedAndRotatesPosition() {
        var motion = AppearanceOrbitMotion.of(
                AppearanceVector.ZERO, new AppearanceVector(1, 1, 0), 90,
                AppearanceOrbitMotion.Direction.POSITIVE, 0, true,
                new AppearanceVector(0, 1, 0), 0,
                AppearanceOrbitMotion.Direction.POSITIVE, 0);
        var result = motion.applyAngles(
                new AppearanceTransform(new AppearanceVector(1, 0, 0), AppearanceRotation.IDENTITY), 180, 0);

        assertEquals(0, result.position().x(), 1.0E-6);
        assertEquals(1, result.position().y(), 1.0E-6);
        assertEquals(0, result.position().z(), 1.0E-6);
        assertEquals(1.0, Math.hypot(Math.hypot(motion.axis().x(), motion.axis().y()), motion.axis().z()), 1.0E-9);
    }

    @Test
    void orbitAndSelfSpinCanBeEnabledIndependently() {
        var base = new AppearanceTransform(new AppearanceVector(2, 0, 0), AppearanceRotation.IDENTITY);
        var selfOnly = AppearanceOrbitMotion.of(
                AppearanceVector.ZERO, new AppearanceVector(0, 1, 0), 0,
                AppearanceOrbitMotion.Direction.POSITIVE, 0, true,
                new AppearanceVector(1, 1, 0), 90,
                AppearanceOrbitMotion.Direction.POSITIVE, 0);
        var result = selfOnly.applyAngles(base, 90, 90);
        assertEquals(base.position(), result.position());
        assertNotEquals(base.rotation(), result.rotation());

        var disabled = AppearanceOrbitMotion.of(
                AppearanceVector.ZERO, AppearanceVector.ZERO, 0,
                AppearanceOrbitMotion.Direction.POSITIVE, 0, true,
                AppearanceVector.ZERO, 0,
                AppearanceOrbitMotion.Direction.POSITIVE, 0);
        assertEquals(base, disabled.applyAngles(base, 90, 90));
    }

    @Test
    void selfSpinUsesTheComponentLocalAxis() {
        var baseRotation = new AppearanceRotation(0, 0, Math.sin(Math.PI / 4), Math.cos(Math.PI / 4));
        var motion = AppearanceOrbitMotion.of(
                AppearanceVector.ZERO, AppearanceVector.ZERO, 0,
                AppearanceOrbitMotion.Direction.POSITIVE, 0, false,
                new AppearanceVector(1, 0, 0), 90,
                AppearanceOrbitMotion.Direction.POSITIVE, 0);
        var result = motion.applyAngles(new AppearanceTransform(AppearanceVector.ZERO, baseRotation), 0, 90);

        // Local X spin turns local Y into Z, then the calibrated Z rotation is applied.
        var transformed = result.rotation().apply(new AppearanceVector(0, 1, 0));
        assertEquals(0, transformed.x(), 1.0E-6);
        assertEquals(0, transformed.y(), 1.0E-6);
        assertEquals(1, transformed.z(), 1.0E-6);
    }
}
