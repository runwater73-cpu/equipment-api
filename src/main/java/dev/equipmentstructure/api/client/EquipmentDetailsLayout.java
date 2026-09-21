package dev.equipmentstructure.api.client;

/** GUI-local rectangles shared by detail drawing, clipping and input. No inventory coordinates. */
public final class EquipmentDetailsLayout {
    public static final Rect PANEL = new Rect(0, 2, 400, 254);
    public static final Rect HOME = new Rect(12, 12, 92, 22);
    public static final Rect ATTRIBUTES = new Rect(110, 12, 110, 22);
    public static final Rect COMPONENTS = new Rect(226, 12, 110, 22);
    public static final Rect ATTRIBUTE_TEXT = new Rect(16, 82, 364, 146);
    public static final Rect COMPONENT_LIST = new Rect(14, 82, 112, 146);
    public static final Rect COMPONENT_TEXT = new Rect(144, 116, 236, 112);
    public static final int COMPONENT_ROW_HEIGHT = 30;

    private EquipmentDetailsLayout() {}

    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Non-positive detail rectangle");
        }
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
        public EquipmentAssemblyUiLayout.Panel panel() {
            return new EquipmentAssemblyUiLayout.Panel(x, y, width, height);
        }
    }
}
