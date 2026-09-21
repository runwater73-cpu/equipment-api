package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.grid.*;

/** The same occupied cell stays under the pointer, including restricted quarter turns. */
record GridDragGeometry(GridFootprint footprint, GridRotation rotation, GridCell grab) {
    GridDragGeometry {
        if (!footprint.rotations().contains(rotation) || !footprint.oriented(rotation).contains(grab))
            throw new IllegalArgumentException("The grab point must be an occupied cell in an allowed orientation");
    }

    static GridDragGeometry start(GridFootprint footprint) {
        var rotation = footprint.rotations().stream().sorted().findFirst().orElseThrow();
        return new GridDragGeometry(footprint, rotation, footprint.oriented(rotation).cells().getFirst());
    }

    GridPlacement at(GridCell pointer) {
        return new GridPlacement(pointer.x() - grab.x(), pointer.y() - grab.y(), rotation);
    }

    GridDragGeometry clockwise() {
        var next = rotation;
        var point = grab;
        var shape = footprint.oriented(rotation);
        do {
            point = new GridCell(shape.height() - 1 - point.y(), point.x());
            shape = shape.rotated(GridRotation.CLOCKWISE_90);
            next = next.clockwise();
        } while (!footprint.rotations().contains(next));
        return new GridDragGeometry(footprint, next, point);
    }
}
