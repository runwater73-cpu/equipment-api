package dev.equipmentstructure.api.appearance;

import java.util.Objects;

/**
 * Deterministic orbit animation in host model coordinates. The renderer supplies time;
 * this value does not create a tick loop or mutable animation state.
 */
public record AppearanceOrbitMotion(AppearanceVector pivot, AppearanceVector axis,
                                    double speedDegreesPerSecond, Direction direction,
                                    double phaseDegrees, boolean rotateWithOrbit,
                                    AppearanceVector selfAxis, double selfSpeedDegreesPerSecond,
                                    Direction selfDirection, double selfPhaseDegrees, PivotSpace pivotSpace) {
    public static final double MAX_SPEED_DEGREES_PER_SECOND = 1440.0D;
    public enum PivotSpace { HOST, LOCAL }

    public enum Direction {
        POSITIVE(1.0D),
        NEGATIVE(-1.0D);

        private final double sign;

        Direction(double sign) { this.sign = sign; }
        double sign() { return sign; }
    }

    public AppearanceOrbitMotion {
        Objects.requireNonNull(pivot, "pivot");
        Objects.requireNonNull(axis, "axis");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(selfAxis, "selfAxis");
        Objects.requireNonNull(selfDirection, "selfDirection");
        Objects.requireNonNull(pivotSpace, "pivotSpace");
        double length = Math.hypot(Math.hypot(axis.x(), axis.y()), axis.z());
        double selfLength = Math.hypot(Math.hypot(selfAxis.x(), selfAxis.y()), selfAxis.z());
        if (speedDegreesPerSecond > 0 && (!Double.isFinite(length) || length < 1.0E-6D)) {
            throw new IllegalArgumentException("Appearance orbit axis must be nonzero when orbit is enabled");
        }
        if (selfSpeedDegreesPerSecond > 0 && (!Double.isFinite(selfLength) || selfLength < 1.0E-6D)) {
            throw new IllegalArgumentException("Appearance self-spin axis must be nonzero when self-spin is enabled");
        }
        axis = length < 1.0E-6D ? AppearanceVector.ZERO
                : new AppearanceVector(axis.x() / length, axis.y() / length, axis.z() / length);
        selfAxis = selfLength < 1.0E-6D ? AppearanceVector.ZERO
                : new AppearanceVector(selfAxis.x() / selfLength, selfAxis.y() / selfLength, selfAxis.z() / selfLength);
        if (!Double.isFinite(speedDegreesPerSecond) || speedDegreesPerSecond < 0
                || speedDegreesPerSecond > MAX_SPEED_DEGREES_PER_SECOND) {
            throw new IllegalArgumentException("Appearance orbit speed must be in [0, 1440]");
        }
        if (!Double.isFinite(selfSpeedDegreesPerSecond) || selfSpeedDegreesPerSecond < 0
                || selfSpeedDegreesPerSecond > MAX_SPEED_DEGREES_PER_SECOND) {
            throw new IllegalArgumentException("Appearance self-spin speed must be in [0, 1440]");
        }
        if (!Double.isFinite(phaseDegrees)) {
            throw new IllegalArgumentException("Appearance orbit phase must be finite");
        }
        phaseDegrees = Math.floorMod((long) Math.floor(phaseDegrees * 1_000_000.0D), 360_000_000L)
                / 1_000_000.0D;
        if (!Double.isFinite(selfPhaseDegrees)) throw new IllegalArgumentException("Appearance self-spin phase must be finite");
        selfPhaseDegrees = Math.floorMod((long) Math.floor(selfPhaseDegrees * 1_000_000.0D), 360_000_000L)
                / 1_000_000.0D;
    }

    /** Creates orbit motion without self-spin. */
    public AppearanceOrbitMotion(AppearanceVector pivot, AppearanceVector axis, double speedDegreesPerSecond,
                                 Direction direction, double phaseDegrees, boolean rotateWithOrbit) {
        this(pivot, axis, speedDegreesPerSecond, direction, phaseDegrees, rotateWithOrbit,
                new AppearanceVector(0, 1, 0), 0, Direction.POSITIVE, 0);
    }

    /** Uses the host frame as the orbit origin. */
    public AppearanceOrbitMotion(AppearanceVector pivot, AppearanceVector axis, double speedDegreesPerSecond,
                                 Direction direction, double phaseDegrees, boolean rotateWithOrbit,
                                 AppearanceVector selfAxis, double selfSpeedDegreesPerSecond,
                                 Direction selfDirection, double selfPhaseDegrees) {
        this(pivot, axis, speedDegreesPerSecond, direction, phaseDegrees, rotateWithOrbit,
                selfAxis, selfSpeedDegreesPerSecond, selfDirection, selfPhaseDegrees, PivotSpace.HOST);
    }

    public AppearanceOrbitMotion withPivotSpace(PivotSpace space) {
        return new AppearanceOrbitMotion(pivot, axis, speedDegreesPerSecond, direction, phaseDegrees, rotateWithOrbit,
                selfAxis, selfSpeedDegreesPerSecond, selfDirection, selfPhaseDegrees, space);
    }

    /** Resolve once in the placement plan. Runtime rendering and editor paths use the same host frame. */
    public AppearanceOrbitMotion resolve(AppearanceTransform anchor, AppearanceMotionSettings settings) {
        boolean local = settings.center() == AppearanceMotionSettings.Center.LOCAL
                || settings.center() == AppearanceMotionSettings.Center.AUTHOR && pivotSpace == PivotSpace.LOCAL;
        var center = local ? anchor.apply(pivot) : pivot;
        var resolvedAxis = settings.tilt().apply(local ? anchor.rotation().apply(axis) : axis);
        return new AppearanceOrbitMotion(center, resolvedAxis, speedDegreesPerSecond, direction, phaseDegrees,
                rotateWithOrbit, selfAxis, selfSpeedDegreesPerSecond, selfDirection, selfPhaseDegrees);
    }

    /** Creates an independently configurable orbit and self-spin motion. Zero speeds disable a channel. */
    public static AppearanceOrbitMotion of(AppearanceVector pivot, AppearanceVector axis, double orbitSpeed,
                                           Direction orbitDirection, double orbitPhase, boolean rotateWithOrbit,
                                           AppearanceVector selfAxis, double selfSpeed,
                                           Direction selfDirection, double selfPhase) {
        return new AppearanceOrbitMotion(pivot, axis, orbitSpeed, orbitDirection, orbitPhase, rotateWithOrbit,
                selfAxis, selfSpeed, selfDirection, selfPhase);
    }

    /** Applies the orbit before the calibrated placement, so the host origin remains the reference frame. */
    public AppearanceTransform apply(AppearanceTransform base, double elapsedSeconds) {
        Objects.requireNonNull(base, "base");
        if (!Double.isFinite(elapsedSeconds)) throw new IllegalArgumentException("Appearance animation time must be finite");
        return applyAngles(base,
                phaseDegrees + direction.sign() * speedDegreesPerSecond * elapsedSeconds,
                selfPhaseDegrees + selfDirection.sign() * selfSpeedDegreesPerSecond * elapsedSeconds);
    }

    /** Used by editor trajectory sampling without inventing a separate curve implementation. */
    public AppearanceTransform applyAngle(AppearanceTransform base, double angleDegrees) {
        return applyAngles(base, angleDegrees, selfPhaseDegrees);
    }

    /** Applies orbit first, then the component-local self-spin. */
    public AppearanceTransform applyAngles(AppearanceTransform base, double orbitAngleDegrees, double selfAngleDegrees) {
        Objects.requireNonNull(base, "base");
        if (!Double.isFinite(orbitAngleDegrees) || !Double.isFinite(selfAngleDegrees))
            throw new IllegalArgumentException("Appearance motion angles must be finite");
        var orbit = rotation(axis, orbitAngleDegrees);
        var position = speedDegreesPerSecond <= 0 ? base.position()
                : orbit.apply(base.position().add(pivot.negate())).add(pivot);
        var rotation = speedDegreesPerSecond > 0 && rotateWithOrbit ? orbit.compose(base.rotation()) : base.rotation();
        // Self-spin is expressed in the component's local frame. Compose it
        // inside the calibrated/base rotation instead of around a host axis.
        if (selfSpeedDegreesPerSecond > 0) rotation = rotation.compose(rotation(selfAxis, selfAngleDegrees));
        return new AppearanceTransform(position, rotation);
    }

    private static AppearanceRotation rotation(AppearanceVector axis, double angleDegrees) {
        double half = Math.toRadians(Math.IEEEremainder(angleDegrees, 360.0D)) * 0.5D;
        double sine = Math.sin(half);
        return new AppearanceRotation(axis.x() * sine, axis.y() * sine, axis.z() * sine, Math.cos(half));
    }
}
