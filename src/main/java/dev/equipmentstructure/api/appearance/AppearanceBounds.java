package dev.equipmentstructure.api.appearance;

import java.util.Objects;

/** Optional author-declared limits for an installed part's saved placement. */
public record AppearanceBounds(AppearanceVector min, AppearanceVector max,
                               double minScale, double maxScale) {
    public AppearanceBounds {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            throw new IllegalArgumentException("Appearance bounds minimum must not exceed maximum");
        }
        if (min.x() < -32 || min.y() < -32 || min.z() < -32
                || max.x() > 32 || max.y() > 32 || max.z() > 32) {
            throw new IllegalArgumentException("Appearance bounds must fit the supported pose range [-32, 32]");
        }
        if (!Double.isFinite(minScale) || !Double.isFinite(maxScale)
                || minScale < 0.05D || maxScale > 2.0D || minScale > maxScale) {
            throw new IllegalArgumentException("Appearance scale bounds must be finite and between 0.05 and 2");
        }
    }

    public AppearanceBounds(AppearanceVector min, AppearanceVector max) {
        this(min, max, 0.05D, 2.0D);
    }

    public AppearancePose clamp(AppearancePose pose) {
        Objects.requireNonNull(pose, "pose");
        AppearanceVector p = pose.transform().position();
        AppearanceVector bounded = new AppearanceVector(
                Math.clamp(p.x(), min.x(), max.x()),
                Math.clamp(p.y(), min.y(), max.y()),
                Math.clamp(p.z(), min.z(), max.z()));
        return new AppearancePose(new AppearanceTransform(bounded, pose.transform().rotation()),
                Math.clamp(pose.scale(), minScale, maxScale));
    }
}
