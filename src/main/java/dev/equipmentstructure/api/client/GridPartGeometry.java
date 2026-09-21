package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.grid.GridCell;
import dev.equipmentstructure.api.grid.GridShape;
import java.util.ArrayList;
import java.util.List;

/** Visual union of one part's cells. Internal seams disappear; holes and separate islands remain. */
record GridPartGeometry(List<Run> runs, List<Edge> edges) {
    enum Side { TOP, RIGHT, BOTTOM, LEFT }
    record Run(int x, int y, int width) {}
    record Edge(int x, int y, Side side) {}
    record Area(int x, int y, int width, int height) {}

    GridPartGeometry {
        runs = List.copyOf(runs);
        edges = List.copyOf(edges);
    }

    static GridPartGeometry of(GridShape shape) {
        var runs = new ArrayList<Run>();
        var edges = new ArrayList<Edge>();
        int width = shape.width(), height = shape.height();
        boolean[][] filled = new boolean[height][width];
        for (var cell : shape.cells()) filled[cell.y()][cell.x()] = true;
        for (int y = 0; y < height; y++) {
            int start = -1;
            for (int x = 0; x <= width; x++) {
                boolean present = x < width && filled[y][x];
                if (present && start < 0) start = x;
                if (!present && start >= 0) { runs.add(new Run(start, y, x - start)); start = -1; }
            }
        }
        for (var cell : shape.cells()) {
            int x = cell.x(), y = cell.y();
            if (!shape.contains(new GridCell(x, y - 1))) edges.add(new Edge(x, y, Side.TOP));
            if (!shape.contains(new GridCell(x + 1, y))) edges.add(new Edge(x, y, Side.RIGHT));
            if (!shape.contains(new GridCell(x, y + 1))) edges.add(new Edge(x, y, Side.BOTTOM));
            if (!shape.contains(new GridCell(x - 1, y))) edges.add(new Edge(x, y, Side.LEFT));
        }
        return new GridPartGeometry(runs, edges);
    }

    static Area artwork(GridShape shape, double aspect) {
        int width = shape.width(), height = shape.height();
        boolean[][] filled = new boolean[height][width];
        for (var cell : shape.cells()) filled[cell.y()][cell.x()] = true;
        return artwork(filled, width, height, aspect);
    }

    /** Largest complete image fit, with extra space and centrality as deterministic tie breakers. */
    private static Area artwork(boolean[][] filled, int width, int height, double aspect) {
        Area best = null;
        for (int top = 0; top < height; top++) {
            boolean[] columns = new boolean[width];
            java.util.Arrays.fill(columns, true);
            for (int bottom = top; bottom < height; bottom++) {
                int start = -1;
                for (int x = 0; x <= width; x++) {
                    if (x < width) columns[x] &= filled[bottom][x];
                    boolean present = x < width && columns[x];
                    if (present && start < 0) start = x;
                    if (!present && start >= 0) {
                        Area candidate = new Area(start, top, x - start, bottom - top + 1);
                        if (better(candidate, best, width, height, aspect)) best = candidate;
                        start = -1;
                    }
                }
            }
        }
        return java.util.Objects.requireNonNull(best);
    }

    private static boolean better(Area a, Area b, int width, int height, double aspect) {
        if (b == null) return true;
        double sizeA = Math.min(a.width(), a.height() * aspect), sizeB = Math.min(b.width(), b.height() * aspect);
        if (sizeA != sizeB) return sizeA > sizeB;
        int areaA = a.width() * a.height(), areaB = b.width() * b.height();
        if (areaA != areaB) return areaA > areaB;
        int ax = 2 * a.x() + a.width() - width, ay = 2 * a.y() + a.height() - height;
        int bx = 2 * b.x() + b.width() - width, by = 2 * b.y() + b.height() - height;
        return ax * ax + ay * ay < bx * bx + by * by;
    }
}
