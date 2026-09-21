package dev.equipmentstructure.api.grid;

/** Integer grid coordinate. Screen pixels and three-dimensional poses are unrelated. */
public record GridCell(int x, int y) implements Comparable<GridCell> {
    /** Stable row-major order, also used when reporting the first invalid cell. */
    @Override
    public int compareTo(GridCell other) {
        int row = Integer.compare(y, other.y);
        return row != 0 ? row : Integer.compare(x, other.x);
    }
}
