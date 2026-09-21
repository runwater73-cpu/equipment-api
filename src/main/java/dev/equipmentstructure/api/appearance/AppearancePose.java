package dev.equipmentstructure.api.appearance;

import java.util.Objects;

/** Per-installed-slot visual adjustment in the host/interface local frame. */
public record AppearancePose(AppearanceTransform transform, double scale) {
    public static final AppearancePose IDENTITY = new AppearancePose(AppearanceTransform.IDENTITY, 1.0D);

    public AppearancePose {
        Objects.requireNonNull(transform, "transform");
        if (!Double.isFinite(scale) || scale < 0.05D || scale > 2.0D) {
            throw new IllegalArgumentException("Appearance pose scale must be finite and between 0.05 and 2");
        }
        AppearanceVector p = transform.position();
        if (Math.abs(p.x()) > 32 || Math.abs(p.y()) > 32 || Math.abs(p.z()) > 32) {
            throw new IllegalArgumentException("Appearance pose coordinates must be between -32 and 32");
        }
    }

    public AppearancePose(double x, double y, double z, AppearanceRotation rotation, double scale) {
        this(new AppearanceTransform(new AppearanceVector(x, y, z), rotation), scale);
    }
}
