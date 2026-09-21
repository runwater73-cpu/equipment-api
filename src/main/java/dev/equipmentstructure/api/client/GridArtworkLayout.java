package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.grid.*;
import dev.equipmentstructure.api.ui.GridComponentDisplay;
import dev.equipmentstructure.api.ui.GridComponentDisplay.Box;

/** Fits the complete image in occupied space, then transforms its authored frame with the part. */
final class GridArtworkLayout {
    private GridArtworkLayout() {}
    record Key(GridShape shape, GridRotation rotation, GridComponentDisplay display, boolean textureAvailable) {}
    record Frame(double centerX, double centerY, double width, double height, int angle, boolean rejectedBox) {}

    static Frame resolve(Key key) {
        var display = key.display();
        double aspect = key.textureAvailable() && display.texture().isPresent()
                ? (double) display.texture().get().width() / display.texture().get().height() : 1;
        int turns = key.rotation().ordinal();
        boolean rotateImage = display.rotateWithPart();
        boolean swapBox = turns % 2 == 1 && !rotateImage;
        boolean validBox = display.box().filter(box -> contained(key.shape(), box)).isPresent();
        Box box = display.box().filter(ignored -> validBox).orElseGet(() -> {
            var automatic = GridPartGeometry.artwork(key.shape(), swapBox ? 1 / aspect : aspect);
            return new Box(automatic.x(), automatic.y(), automatic.width(), automatic.height());
        });
        double x = box.x() + box.width() / 2, y = box.y() + box.height() / 2;
        double shapeWidth = key.shape().width(), shapeHeight = key.shape().height();
        for (int n = 0; n < turns; n++) {
            double nextX = shapeHeight - y; y = x; x = nextX;
            double previousWidth = shapeWidth; shapeWidth = shapeHeight; shapeHeight = previousWidth;
        }
        double width = swapBox ? box.height() : box.width(), height = swapBox ? box.width() : box.height();
        double inset = Math.min(.12, Math.min(width, height) * .1);
        double imageWidth = Math.min(width - 2 * inset, (height - 2 * inset) * aspect) * display.scale();
        return new Frame(x, y, imageWidth, imageWidth / aspect, rotateImage ? turns * 90 : 0,
                display.box().isPresent() && !validBox);
    }

    static boolean contained(GridShape shape, Box box) {
        if (box.x() + box.width() > shape.width() || box.y() + box.height() > shape.height()) return false;
        for (int y = (int) Math.floor(box.y()); y < Math.ceil(box.y() + box.height()); y++)
            for (int x = (int) Math.floor(box.x()); x < Math.ceil(box.x() + box.width()); x++)
                if (!shape.contains(new GridCell(x, y))) return false;
        return true;
    }
}
