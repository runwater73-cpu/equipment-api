package dev.equipmentstructure.api.grid;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Equipment-specific area and body reference, with author-controlled body occupancy. */
public record GridBoard(GridShape area, GridShape body, GridPlacement bodyPlacement, boolean bodyOccupiesCells) {
    public static final int DEFAULT_WIDTH = 9;
    public static final int DEFAULT_HEIGHT = 9;

    /** Bodies reserve cells unless their equipment author explicitly opts out. */
    public GridBoard(GridShape area, GridShape body, GridPlacement bodyPlacement) {
        this(area, body, bodyPlacement, true);
    }

    public GridBoard {
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(bodyPlacement, "bodyPlacement");
        int width = area.width();
        int height = area.height();
        for (GridCell local : body.rotated(bodyPlacement.rotation()).cells()) {
            long x = (long) bodyPlacement.x() + local.x();
            long y = (long) bodyPlacement.y() + local.y();
            if (x < 0 || y < 0 || x >= width || y >= height
                    || !area.contains(new GridCell((int) x, (int) y))) {
                throw new IllegalArgumentException("Equipment body must fit entirely inside the board");
            }
        }
    }

    public static GridBoard defaultBoard(GridShape body) {
        Objects.requireNonNull(body, "body");
        return new GridBoard(GridShape.rectangle(DEFAULT_WIDTH, DEFAULT_HEIGHT), body,
                new GridPlacement((DEFAULT_WIDTH - body.width()) / 2, (DEFAULT_HEIGHT - body.height()) / 2));
    }

    /** Keeps the body reference for rendering and spatial queries even when it reserves no cells. */
    public GridBoard withBodyOccupancy(boolean occupiesCells) {
        return new GridBoard(area, body, bodyPlacement, occupiesCells);
    }

    /** Reference geometry, including when body occupancy is disabled. */
    public Set<GridCell> bodyCells() {
        var result = new HashSet<GridCell>();
        for (GridCell cell : body.rotated(bodyPlacement.rotation()).cells()) {
            result.add(bodyPlacement.translate(cell));
        }
        return Set.copyOf(result);
    }

    public Set<GridCell> blockedBodyCells() { return bodyOccupiesCells ? bodyCells() : Set.of(); }

    public int usableArea() { return area.area() - (bodyOccupiesCells ? body.area() : 0); }
}
