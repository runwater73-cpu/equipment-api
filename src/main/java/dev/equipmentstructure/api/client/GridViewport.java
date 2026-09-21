package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.grid.GridCell;
import dev.equipmentstructure.api.grid.GridShape;

/** Shared screen-local transform for drawing, hit testing and snapping. */
final class GridViewport {
    static final double MIN_SCALE = 1.0, MAX_SCALE = 48.0;
    private final double x, y, width, height;
    private double originX, originY, scale = 12;

    GridViewport(double x, double y, double width, double height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    void fit(GridShape shape) {
        scale = Math.clamp(Math.min((width - 8) / shape.width(), (height - 8) / shape.height()), MIN_SCALE, 24);
        originX = x + (width - shape.width() * scale) / 2;
        originY = y + (height - shape.height() * scale) / 2;
    }

    boolean contains(double px, double py) {
        return px >= x && py >= y && px < x + width && py < y + height;
    }

    GridCell cell(double px, double py) {
        return new GridCell((int) Math.floor((px - originX) / scale), (int) Math.floor((py - originY) / scale));
    }

    double screenX(int column) { return originX + column * scale; }
    double screenY(int row) { return originY + row * scale; }
    double scale() { return scale; }
    void pan(double dx, double dy) { originX += dx; originY += dy; }

    void zoom(double px, double py, double steps) {
        double next = Math.clamp(scale * Math.pow(1.2, Math.clamp(steps, -10, 10)), MIN_SCALE, MAX_SCALE);
        originX = px - (px - originX) * next / scale;
        originY = py - (py - originY) * next / scale;
        scale = next;
    }
}
