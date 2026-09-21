package dev.equipmentstructure.api.client;

/** Bounded, presentation-only scrolling; content changes cannot leave the viewport past its end. */
final class EquipmentDetailsScroll {
    private int offset;
    private int maximum;

    int offset() { return offset; }
    int maximum() { return maximum; }
    void measure(int contentHeight, int viewportHeight) {
        if (contentHeight < 0 || viewportHeight <= 0) throw new IllegalArgumentException("Invalid scroll dimensions");
        maximum = Math.max(0, contentHeight - viewportHeight);
        offset = Math.min(offset, maximum);
    }
    void move(double pixels) {
        if (Double.isFinite(pixels)) offset = (int) Math.max(0, Math.min(maximum, offset + pixels));
    }
    void reset() { offset = 0; }
    void end() { offset = maximum; }
}
