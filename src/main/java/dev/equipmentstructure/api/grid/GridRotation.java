package dev.equipmentstructure.api.grid;

/** Clockwise quarter turns in a grid where x points right and y points down. */
public enum GridRotation {
    NONE,
    CLOCKWISE_90,
    HALF_TURN,
    COUNTERCLOCKWISE_90;

    public GridRotation clockwise() {
        return values()[(ordinal() + 1) % 4];
    }
}
