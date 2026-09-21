package dev.equipmentstructure.api.appearance;

/** Pixel-boundary coordinates, top-left origin, converted to author-sized model units (1/16 block). */
public record SpriteCalibration(double originU, double originV, double pixelsPerUnit, double planeDepth) {
    public SpriteCalibration {
        if (!Double.isFinite(originU) || !Double.isFinite(originV) || !Double.isFinite(planeDepth)
                || !Double.isFinite(pixelsPerUnit) || pixelsPerUnit <= 0) {
            throw new IllegalArgumentException("Sprite calibration must be finite with positive pixelsPerUnit");
        }
    }

    /** For pixel centers supply u+0.5/v+0.5 explicitly. No automatic snap or shape-dependent offset. */
    public AppearanceVector toModel(double u, double v) {
        if (!Double.isFinite(u) || !Double.isFinite(v)) throw new IllegalArgumentException("Pixel coordinates must be finite");
        return new AppearanceVector((u - originU) / pixelsPerUnit, (originV - v) / pixelsPerUnit, planeDepth);
    }
}
