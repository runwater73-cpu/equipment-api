package dev.equipmentstructure.api.grid;

import java.util.Objects;

/** Bounding origin and semantic direction of one object on the assembly board. */
public record GridPlacement(int x, int y, GridRotation rotation) {
    public GridPlacement {
        Objects.requireNonNull(rotation, "rotation");
    }

    public GridPlacement(int x, int y) {
        this(x, y, GridRotation.NONE);
    }

    /** Exact translation; oversized coordinates are rejected instead of wrapping. */
    public GridCell translate(GridCell local) {
        return new GridCell(Math.addExact(x, local.x()), Math.addExact(y, local.y()));
    }
}
