package dev.equipmentstructure.api.appearance;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AppearanceMathTest {
    private static final double EPSILON = 1.0e-9;

    @Test void nonCentralMountAndExplicitOffsetDoNotChangeShape() {
        var port = translated(0, 7, 0);
        var mount = translated(0, 1, 0);
        var transform = AppearanceTransform.attach(AppearanceTransform.IDENTITY, port,
                translated(0, -0.5, 0), mount);
        assertVector(new AppearanceVector(2, 9.5, 0), transform.apply(new AppearanceVector(2, 4, 0)));
        assertVector(new AppearanceVector(0, 6.5, 0), transform.apply(mount.position()));
    }

    @Test void spriteResolutionDoesNotChangePhysicalAttachment() {
        var small = new SpriteCalibration(8, 16, 1, 0);
        var large = new SpriteCalibration(16, 32, 2, 0);
        assertVector(new AppearanceVector(2, 4, 0), small.toModel(10, 12));
        assertVector(small.toModel(10, 12), large.toModel(20, 24));
        assertVector(new AppearanceVector(0.5, -0.5, 0), small.toModel(8.5, 16.5));
    }

    @Test void targetAndMountRotationsHaveDifferentRoles() {
        double q = Math.sqrt(0.5);
        var turn = new AppearanceRotation(0, 0, q, q);
        var target = new AppearanceTransform(new AppearanceVector(4, 8, 0), turn);
        var mounted = AppearanceTransform.attach(AppearanceTransform.IDENTITY, target,
                AppearanceTransform.IDENTITY, translated(0, 1, 0));
        assertVector(new AppearanceVector(1, 10, 0), mounted.apply(new AppearanceVector(2, 4, 0)));
        var mount = new AppearanceTransform(new AppearanceVector(1, 2, 0), turn);
        var inverse = AppearanceTransform.attach(AppearanceTransform.IDENTITY, translated(4, 8, 0),
                AppearanceTransform.IDENTITY, mount);
        assertVector(new AppearanceVector(4, 6, 0), inverse.apply(new AppearanceVector(3, 2, 0)));
    }

    @Test void virtualPivotPreservesFloatingGap() {
        var mounted = AppearanceTransform.attach(AppearanceTransform.IDENTITY, translated(0, 7, 0),
                AppearanceTransform.IDENTITY, translated(0, -2, 0));
        assertVector(new AppearanceVector(0, 9, 0), mounted.apply(AppearanceVector.ZERO));
    }

    @Test void rotationsNormalizeHugeAndSubnormalValuesAndCanonicalizeSign() {
        var q = new AppearanceRotation(0, 0, 1, 1);
        assertEquals(q, new AppearanceRotation(0, 0, -1, -1));
        assertEquals(q, new AppearanceRotation(0, 0, Double.MAX_VALUE, Double.MAX_VALUE));
        assertEquals(q, new AppearanceRotation(0, 0, Double.MIN_VALUE, Double.MIN_VALUE));
        assertEquals(new AppearanceRotation(1, 0, 0, 0), new AppearanceRotation(-1, -0.0, 0, -0.0));
        assertEquals(new AppearanceVector(0, 0, 0), new AppearanceVector(-0.0, 0, -0.0));
        var rounded = new AppearanceRotation(-1, -1, -1, Double.MIN_VALUE);
        assertTrue(rounded.w() > 0);
        assertEquals(rounded, new AppearanceRotation(1, 1, 1, -Double.MIN_VALUE));
    }

    @Test void invalidNumbersAndPixelScalesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AppearanceVector(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AppearanceRotation(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AppearanceRotation(0, 0, Double.POSITIVE_INFINITY, 1));
        assertThrows(IllegalArgumentException.class, () -> new SpriteCalibration(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SpriteCalibration(0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SpriteCalibration(Double.NaN, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SpriteCalibration(0, 0, 1, 0).toModel(Double.NaN, 2));
    }

    @Test void arithmeticOverflowDoesNotLeakNonfiniteTransforms() {
        var large = translated(Double.MAX_VALUE, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> large.compose(large));
        assertThrows(IllegalArgumentException.class,
                () -> new SpriteCalibration(0, 0, Double.MIN_VALUE, 0).toModel(10, 0));
    }

    @Test void randomizedCompositionInverseAndAttachmentPreserveRigidGeometry() {
        var random = new Random(731L);
        for (int index = 0; index < 500; index++) {
            var parent = randomTransform(random);
            var port = randomTransform(random);
            var joint = randomTransform(random);
            var mount = randomTransform(random);
            var point = randomVector(random);
            assertVector(parent.apply(port.apply(point)), parent.compose(port).apply(point));
            assertVector(point, parent.inverse().apply(parent.apply(point)));
            var placed = AppearanceTransform.attach(parent, port, joint, mount);
            var expected = parent.compose(port).compose(joint);
            // Origin and three basis points verify position AND all orientation axes.
            for (var local : new AppearanceVector[]{AppearanceVector.ZERO,
                    new AppearanceVector(1, 0, 0), new AppearanceVector(0, 1, 0), new AppearanceVector(0, 0, 1)}) {
                assertVector(expected.apply(local), placed.apply(mount.apply(local)));
            }
        }
    }

    static AppearanceTransform translated(double x, double y, double z) {
        return new AppearanceTransform(new AppearanceVector(x, y, z), AppearanceRotation.IDENTITY);
    }

    static void assertVector(AppearanceVector expected, AppearanceVector actual) {
        assertEquals(expected.x(), actual.x(), EPSILON);
        assertEquals(expected.y(), actual.y(), EPSILON);
        assertEquals(expected.z(), actual.z(), EPSILON);
    }

    private static AppearanceVector randomVector(Random random) {
        return new AppearanceVector(random.nextDouble() * 20 - 10,
                random.nextDouble() * 20 - 10, random.nextDouble() * 20 - 10);
    }

    private static AppearanceTransform randomTransform(Random random) {
        return new AppearanceTransform(randomVector(random), new AppearanceRotation(
                random.nextDouble() - 0.5, random.nextDouble() - 0.5,
                random.nextDouble() - 0.5, random.nextDouble() - 0.5));
    }
}
