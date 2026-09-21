package dev.equipmentstructure.api.appearance;

/** Finite model-space vector: right-handed, +X right, +Y up, +Z toward the sprite front. */
public record AppearanceVector(double x, double y, double z) {
    public static final AppearanceVector ZERO = new AppearanceVector(0, 0, 0);

    public AppearanceVector {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Appearance coordinates must be finite");
        }
        x = x == 0 ? 0 : x;
        y = y == 0 ? 0 : y;
        z = z == 0 ? 0 : z;
    }

    public AppearanceVector add(AppearanceVector other) {
        return new AppearanceVector(x + other.x, y + other.y, z + other.z);
    }

    public AppearanceVector negate() { return new AppearanceVector(-x, -y, -z); }
}
