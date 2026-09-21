package dev.equipmentstructure.api.client;

/** Sidebar squares remain item-sized and scroll independently of the board camera. */
final class GridSidebarLayout {
    static final int X = 298, Y = 29, PITCH = 21, SIZE = 19, COLUMNS = 4, ROWS = 6;
    private static final EquipmentAssemblyUiLayout.Panel[] PANELS = new EquipmentAssemblyUiLayout.Panel[ROWS + 1];
    private static final EquipmentAssemblyUiLayout.Panel[] DETAILS = new EquipmentAssemblyUiLayout.Panel[ROWS + 1];
    static {
        // Only seven possible sizes, shared for the screen lifetime and resource reloads.
        for (int rows = 0; rows <= ROWS; rows++) {
            PANELS[rows] = new EquipmentAssemblyUiLayout.Panel(288, 2, 104,
                    Y - 2 + rows * PITCH + (rows == 0 ? 10 : 8));
            DETAILS[rows] = new EquipmentAssemblyUiLayout.Panel(288, PANELS[rows].bottom() + 7, 104, 86);
        }
    }
    private GridSidebarLayout() {}

    static int visibleRows(int count) { return Math.min(ROWS, (Math.max(0, count) + COLUMNS - 1) / COLUMNS); }
    static EquipmentAssemblyUiLayout.Panel panel(int count) { return PANELS[visibleRows(count)]; }
    static EquipmentAssemblyUiLayout.Panel details(int count) { return DETAILS[visibleRows(count)]; }
    static boolean contains(double x, double y, int count) {
        if (count <= 0) return false;
        var panel = panel(count);
        return x >= panel.x() && x < panel.right() && y >= panel.y() && y < panel.bottom();
    }
    static int maxScrollRow(int count) { return Math.max(0, (count + COLUMNS - 1) / COLUMNS - ROWS); }

    static int indexAt(double x, double y, int scrollRow, int count) {
        double dx = x - X, dy = y - Y;
        if (dx < 0 || dy < 0 || dx >= COLUMNS * PITCH || dy >= visibleRows(count) * PITCH) return -1;
        int column = (int) dx / PITCH, row = (int) dy / PITCH;
        if ((int) dx % PITCH >= SIZE || (int) dy % PITCH >= SIZE) return -1;
        int index = (Math.clamp(scrollRow, 0, maxScrollRow(count)) + row) * COLUMNS + column;
        return index < count ? index : -1;
    }
}
