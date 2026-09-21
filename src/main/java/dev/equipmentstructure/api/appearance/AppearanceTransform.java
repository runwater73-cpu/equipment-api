package dev.equipmentstructure.api.appearance;

import java.util.Objects;

/** Rigid local frame. Composition uses column-vector order; implicit scale/mirroring is absent. */
public record AppearanceTransform(AppearanceVector position, AppearanceRotation rotation) {
    public static final AppearanceTransform IDENTITY = new AppearanceTransform(AppearanceVector.ZERO, AppearanceRotation.IDENTITY);

    public AppearanceTransform {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");
    }

    public AppearanceVector apply(AppearanceVector point) { return rotation.apply(point).add(position); }

    /** Apply inner first, then this. */
    public AppearanceTransform compose(AppearanceTransform inner) {
        return new AppearanceTransform(apply(inner.position), rotation.compose(inner.rotation));
    }

    public AppearanceTransform inverse() {
        var inverse = rotation.inverse();
        return new AppearanceTransform(inverse.apply(position.negate()), inverse);
    }

    /** Parent * Port * Joint * inverse(Mount); source assets must already be calibrated. */
    public static AppearanceTransform attach(AppearanceTransform parent, AppearanceTransform port,
                                             AppearanceTransform joint, AppearanceTransform mount) {
        return parent.compose(port).compose(joint).compose(mount.inverse());
    }
}
