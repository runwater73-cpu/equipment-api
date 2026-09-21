package dev.equipmentstructure.api.client;

/** Sidebar squares remain item-sized and scroll independently of the board camera. */
final class GridSidebarLayout {
    static final int X = 298, Y = 29, PITCH = 21, SIZE = 19, COLUMNS = 4, ROWS = 6;
    private GridSidebarLayout() {}

    static int maxScrollRow(int count) { return Math.max(0, (count + COLUMNS - 1) / COLUMNS - ROWS); }

    static int indexAt(double x, double y, int scrollRow, int count) {
        double dx = x - X, dy = y - Y;
        if (dx < 0 || dy < 0 || dx >= COLUMNS * PITCH || dy >= ROWS * PITCH) return -1;
        int column = (int) dx / PITCH, row = (int) dy / PITCH;
        if ((int) dx % PITCH >= SIZE || (int) dy % PITCH >= SIZE) return -1;
        int index = (Math.clamp(scrollRow, 0, maxScrollRow(count)) + row) * COLUMNS + column;
        return index < count ? index : -1;
    }
}
