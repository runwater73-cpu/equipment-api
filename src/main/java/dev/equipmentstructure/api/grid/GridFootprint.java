package dev.equipmentstructure.api.grid;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Component-owned shape and permitted absolute orientations. */
public record GridFootprint(GridShape shape, Set<GridRotation> rotations) {
    public static final GridFootprint SINGLE_CELL = freelyRotating(GridShape.rectangle(1, 1));

    public GridFootprint {
        Objects.requireNonNull(shape, "shape");
        rotations = Set.copyOf(Objects.requireNonNull(rotations, "rotations"));
        if (rotations.isEmpty()) throw new IllegalArgumentException("At least one orientation is required");
    }

    public static GridFootprint freelyRotating(GridShape shape) {
        return new GridFootprint(shape, EnumSet.allOf(GridRotation.class));
    }

    public static GridFootprint fixed(GridShape shape) {
        return new GridFootprint(shape, Set.of(GridRotation.NONE));
    }

    public GridShape oriented(GridRotation rotation) {
        if (!rotations.contains(Objects.requireNonNull(rotation, "rotation"))) {
            throw new IllegalArgumentException("Component does not allow orientation " + rotation);
        }
        return shape.rotated(rotation);
    }
}
