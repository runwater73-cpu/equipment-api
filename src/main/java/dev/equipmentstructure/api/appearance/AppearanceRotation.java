package dev.equipmentstructure.api.appearance;

/** Normalized quaternion in [x,y,z,w] order. Equivalent signs have a canonical representation. */
public record AppearanceRotation(double x, double y, double z, double w) {
    public static final AppearanceRotation IDENTITY = new AppearanceRotation(0, 0, 0, 1);

    public AppearanceRotation {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(w)) {
            throw new IllegalArgumentException("Appearance rotation must be finite");
        }
        // Scale first so both near-Double.MAX_VALUE and subnormal inputs can normalize safely.
        double largest = Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.max(Math.abs(z), Math.abs(w)));
        if (largest == 0) throw new IllegalArgumentException("Appearance rotation must be nonzero");
        x /= largest;
        y /= largest;
        z /= largest;
        w /= largest;
        double length = Math.sqrt(x * x + y * y + z * z + w * w);
        x /= length;
        y /= length;
        z /= length;
        w /= length;
        // Choose the canonical sign from the final normalized coordinates.
        double first = w != 0 ? w : x != 0 ? x : y != 0 ? y : z;
        double sign = first < 0 ? -1 : 1;
        x = x == 0 ? 0 : x * sign;
        y = y == 0 ? 0 : y * sign;
        z = z == 0 ? 0 : z * sign;
        w = w == 0 ? 0 : w * sign;
    }

    /** this * inner: apply inner first, then this. */
    public AppearanceRotation compose(AppearanceRotation inner) {
        return new AppearanceRotation(
                w * inner.x + x * inner.w + y * inner.z - z * inner.y,
                w * inner.y - x * inner.z + y * inner.w + z * inner.x,
                w * inner.z + x * inner.y - y * inner.x + z * inner.w,
                w * inner.w - x * inner.x - y * inner.y - z * inner.z);
    }

    public AppearanceRotation inverse() { return new AppearanceRotation(-x, -y, -z, w); }

    public AppearanceVector apply(AppearanceVector value) {
        // Matrix form avoids unnecessarily doubling a potentially very large input vector.
        return new AppearanceVector(
                (1 - 2 * (y * y + z * z)) * value.x() + 2 * (x * y - w * z) * value.y() + 2 * (x * z + w * y) * value.z(),
                2 * (x * y + w * z) * value.x() + (1 - 2 * (x * x + z * z)) * value.y() + 2 * (y * z - w * x) * value.z(),
                2 * (x * z - w * y) * value.x() + 2 * (y * z + w * x) * value.y() + (1 - 2 * (x * x + y * y)) * value.z());
    }
}
