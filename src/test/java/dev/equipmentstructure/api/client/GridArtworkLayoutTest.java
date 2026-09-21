package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.grid.*;
import dev.equipmentstructure.api.ui.GridComponentDisplay;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class GridArtworkLayoutTest {
    @Test void continuousPanelCoversCellsOnceAndKeepsHoleAndIslandBoundaries() {
        for (var shape : new GridShape[]{GridShape.rectangle(2, 2), GridShape.mask("###", "#.#", "###"),
                GridShape.mask("#.#", "...", "#.."), GridShape.mask("#.", "##")}) {
            var geometry = GridPartGeometry.of(shape);
            var painted = new HashSet<GridCell>();
            for (var run : geometry.runs()) for (int dx = 0; dx < run.width(); dx++)
                assertTrue(painted.add(new GridCell(run.x() + dx, run.y())), "duplicate fill");
            assertEquals(new HashSet<>(shape.cells()), painted);
            int expected = 0;
            for (var cell : shape.cells()) {
                int[][] offsets = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
                for (var offset : offsets) if (!shape.contains(new GridCell(cell.x() + offset[0], cell.y() + offset[1]))) expected++;
            }
            assertEquals(expected, geometry.edges().size());
            for (var edge : geometry.edges()) {
                var neighbor = switch (edge.side()) {
                    case TOP -> new GridCell(edge.x(), edge.y() - 1);
                    case RIGHT -> new GridCell(edge.x() + 1, edge.y());
                    case BOTTOM -> new GridCell(edge.x(), edge.y() + 1);
                    case LEFT -> new GridCell(edge.x() - 1, edge.y());
                };
                assertFalse(shape.contains(neighbor), "internal seam");
            }
        }
        assertEquals(8, GridPartGeometry.of(GridShape.rectangle(2, 2)).edges().size());
        assertEquals(16, GridPartGeometry.of(GridShape.mask("###", "#.#", "###")).edges().size());
    }

    @Test void squareUsesWholePanelAndWideArtworkUsesLongSide() {
        var square = resolve(GridShape.rectangle(2, 2), GridRotation.NONE, GridComponentDisplay.defaults());
        assertEquals(1, square.centerX()); assertEquals(1, square.centerY());
        assertTrue(square.width() > 1.5); assertEquals(square.width(), square.height());
        var wide = GridComponentDisplay.defaults().withTexture(texture(32, 16));
        var bar = resolve(GridShape.rectangle(2, 1), GridRotation.NONE, wide);
        assertEquals(2, bar.width() / bar.height(), 1e-9);
        assertTrue(bar.width() > 1.5);
    }

    @Test void authoredFractionalFrameRotatesItsCenterAndOptionallyItsImage() {
        var shape = GridShape.rectangle(3, 2);
        var display = GridComponentDisplay.defaults().withTexture(texture(32, 16))
                .withBox(new GridComponentDisplay.Box(1, .25, 1.5, 1)).withScale(.8);
        var base = resolve(shape, GridRotation.NONE, display);
        var turn = resolve(shape, GridRotation.CLOCKWISE_90, display.withRotation(true));
        assertEquals(1.25, turn.centerX()); assertEquals(1.75, turn.centerY());
        assertEquals(90, turn.angle()); assertEquals(base.width(), turn.width()); assertEquals(base.height(), turn.height());
        var upright = resolve(shape, GridRotation.CLOCKWISE_90, display);
        assertEquals(0, upright.angle()); assertEquals(2, upright.width() / upright.height(), 1e-9);
        assertTrue(upright.width() < turn.width());
    }

    @Test void rejectedBoxAndMissingTextureFallBackWithoutCoveringHoles() {
        var ring = GridShape.mask("###", "#.#", "###");
        var display = GridComponentDisplay.defaults().withBox(new GridComponentDisplay.Box(.5, .5, 2, 2))
                .withTexture(texture(32, 16));
        var frame = GridArtworkLayout.resolve(new GridArtworkLayout.Key(ring, GridRotation.NONE, display, false));
        assertTrue(frame.rejectedBox()); assertEquals(frame.width(), frame.height());
        assertContained(ring, frame);
    }

    @Test void uprightAutomaticFrameChoosesAreaForTheDisplayedOrientation() {
        var shape = GridShape.mask("###", "#..", "#..");
        var display = GridComponentDisplay.defaults().withTexture(texture(64, 16));
        var frame = resolve(shape, GridRotation.CLOCKWISE_90, display);
        assertTrue(frame.width() > 2, "upright wide image should fit the rotated horizontal arm");
        assertContained(shape.rotated(GridRotation.CLOCKWISE_90), frame);
    }

    @Test void completeArtworkFitsEverySmallShapeAspectAndRotation() {
        for (int mask = 1; mask < 512; mask++) {
            var cells = new ArrayList<GridCell>();
            for (int bit = 0; bit < 9; bit++) if ((mask & (1 << bit)) != 0) cells.add(new GridCell(bit % 3, bit / 3));
            var shape = GridShape.of(cells);
            for (var rotation : GridRotation.values()) for (int width : new int[]{8, 16, 32}) for (boolean rotate : new boolean[]{false, true}) {
                var display = GridComponentDisplay.defaults().withTexture(texture(width, 16)).withRotation(rotate);
                var frame = resolve(shape, rotation, display);
                assertEquals(width / 16.0, frame.width() / frame.height(), 1e-9);
                assertContained(shape.rotated(rotation), frame);
            }
        }
    }

    private static GridComponentDisplay.Texture texture(int w, int h) {
        return new GridComponentDisplay.Texture(ResourceLocation.parse("test:textures/grid.png"), w, h);
    }
    private static GridArtworkLayout.Frame resolve(GridShape shape, GridRotation rotation, GridComponentDisplay display) {
        return GridArtworkLayout.resolve(new GridArtworkLayout.Key(shape, rotation, display, display.texture().isPresent()));
    }
    private static void assertContained(GridShape shape, GridArtworkLayout.Frame frame) {
        boolean swap = frame.angle() % 180 != 0;
        double width = swap ? frame.height() : frame.width(), height = swap ? frame.width() : frame.height();
        assertTrue(width > 0 && height > 0);
        assertTrue(GridArtworkLayout.contained(shape, new GridComponentDisplay.Box(frame.centerX() - width / 2,
                frame.centerY() - height / 2, width, height)), () -> shape + " / " + frame);
    }
}
