package dev.equipmentstructure.api.grid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable occupied cells, normalized to a bounding origin of (0, 0).
 * Holes and disconnected cells are preserved. Area is the number of actual cells.
 */
public record GridShape(List<GridCell> cells) {
    public static final int MAX_SIDE = 64;
    public static final int MAX_CELLS = MAX_SIDE * MAX_SIDE;

    public GridShape {
        Objects.requireNonNull(cells, "cells");
        if (cells.isEmpty() || cells.size() > MAX_CELLS) {
            throw new IllegalArgumentException("A shape requires 1 to " + MAX_CELLS + " cells");
        }
        List<GridCell> copy = List.copyOf(cells);
        if (new HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException("Duplicate shape cell");
        }
        int minX = copy.stream().mapToInt(GridCell::x).min().orElseThrow();
        int minY = copy.stream().mapToInt(GridCell::y).min().orElseThrow();
        int maxX = copy.stream().mapToInt(GridCell::x).max().orElseThrow();
        int maxY = copy.stream().mapToInt(GridCell::y).max().orElseThrow();
        // Use longs before normalization: even hostile integer extremes must not wrap.
        if ((long) maxX - minX >= MAX_SIDE || (long) maxY - minY >= MAX_SIDE) {
            throw new IllegalArgumentException("Shape bounds exceed " + MAX_SIDE + " by " + MAX_SIDE);
        }
        cells = copy.stream().map(cell -> new GridCell(
                (int) ((long) cell.x() - minX), (int) ((long) cell.y() - minY))).sorted().toList();
    }

    public static GridShape of(GridCell... cells) {
        return new GridShape(List.of(cells));
    }

    public static GridShape of(Collection<GridCell> cells) {
        return new GridShape(List.copyOf(cells));
    }

    public static GridShape rectangle(int width, int height) {
        if (width < 1 || height < 1 || width > MAX_SIDE || height > MAX_SIDE) {
            throw new IllegalArgumentException("Rectangle dimensions must be between 1 and " + MAX_SIDE);
        }
        List<GridCell> cells = new ArrayList<>(width * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) cells.add(new GridCell(x, y));
        }
        return new GridShape(cells);
    }

    /** '#' or '■' occupies a cell; '.' or '·' leaves a hole. */
    public static GridShape mask(String... rows) {
        Objects.requireNonNull(rows, "rows");
        if (rows.length < 1 || rows.length > MAX_SIDE) {
            throw new IllegalArgumentException("Mask height must be between 1 and " + MAX_SIDE);
        }
        int width = Objects.requireNonNull(rows[0], "row").length();
        if (width < 1 || width > MAX_SIDE) throw new IllegalArgumentException("Invalid mask width");
        List<GridCell> cells = new ArrayList<>();
        for (int y = 0; y < rows.length; y++) {
            String row = Objects.requireNonNull(rows[y], "row");
            if (row.length() != width) throw new IllegalArgumentException("Mask rows must have equal width");
            for (int x = 0; x < width; x++) {
                char mark = row.charAt(x);
                if (mark == '#' || mark == '■') cells.add(new GridCell(x, y));
                else if (mark != '.' && mark != '·') throw new IllegalArgumentException("Unknown mask character: " + mark);
            }
        }
        return new GridShape(cells);
    }

    public int area() { return cells.size(); }

    public int width() { return cells.stream().mapToInt(GridCell::x).max().orElseThrow() + 1; }

    public int height() { return cells.getLast().y() + 1; }

    public boolean contains(GridCell cell) {
        return java.util.Collections.binarySearch(cells, Objects.requireNonNull(cell, "cell")) >= 0;
    }

    /** Returns normalized geometry; the caller keeps the semantic direction separately. */
    public GridShape rotated(GridRotation rotation) {
        Objects.requireNonNull(rotation, "rotation");
        if (rotation == GridRotation.NONE) return this;
        int width = width();
        int height = height();
        return new GridShape(cells.stream().map(cell -> switch (rotation) {
            case NONE -> cell;
            case CLOCKWISE_90 -> new GridCell(height - 1 - cell.y(), cell.x());
            case HALF_TURN -> new GridCell(width - 1 - cell.x(), height - 1 - cell.y());
            case COUNTERCLOCKWISE_90 -> new GridCell(cell.y(), width - 1 - cell.x());
        }).toList());
    }
}
