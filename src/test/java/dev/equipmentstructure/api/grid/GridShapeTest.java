package dev.equipmentstructure.api.grid;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GridShapeTest {
    @Test
    void normalizationHasStableOrderAndDoesNotRetainMutableInput() {
        var source = new ArrayList<>(List.of(new GridCell(12, -3), new GridCell(11, -4), new GridCell(11, -3)));
        var shape = GridShape.of(source);
        source.clear();
        assertEquals(GridShape.mask("#.", "##"), shape);
        assertEquals(List.of(new GridCell(0, 0), new GridCell(0, 1), new GridCell(1, 1)), shape.cells());
        assertThrows(UnsupportedOperationException.class, () -> shape.cells().clear());
    }

    @Test
    void clockwiseRotationKeepsTheActualAsymmetricShape() {
        var shape = GridShape.mask("#.", "#.", "##");
        assertEquals(GridShape.mask("###", "#.."), shape.rotated(GridRotation.CLOCKWISE_90));
        assertEquals(GridShape.mask("##", ".#", ".#"), shape.rotated(GridRotation.HALF_TURN));
        assertEquals(GridShape.mask("..#", "###"), shape.rotated(GridRotation.COUNTERCLOCKWISE_90));
        var rotated = shape;
        for (int i = 0; i < 4; i++) rotated = rotated.rotated(GridRotation.CLOCKWISE_90);
        assertEquals(shape, rotated);
        assertEquals(4, shape.area());
    }

    @Test
    void holesAndDisconnectedCellsRemainEmptyAfterRotation() {
        var ring = GridShape.mask("###", "#.#", "###");
        for (GridRotation direction : GridRotation.values()) {
            assertEquals(8, ring.rotated(direction).area());
            assertFalse(ring.rotated(direction).contains(new GridCell(1, 1)));
        }
        var disconnected = GridShape.mask("#..", "..#");
        assertEquals(2, disconnected.area());
        assertEquals(GridShape.mask(".#", "..", "#."), disconnected.rotated(GridRotation.CLOCKWISE_90));
    }

    @Test
    void malformedAndOversizedShapesAreRejectedWithoutCoordinateOverflow() {
        assertThrows(IllegalArgumentException.class, () -> GridShape.of(List.of()));
        assertThrows(IllegalArgumentException.class, () -> GridShape.of(new GridCell(0, 0), new GridCell(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> GridShape.of(
                new GridCell(Integer.MIN_VALUE, 0), new GridCell(Integer.MAX_VALUE, 0)));
        assertThrows(IllegalArgumentException.class, () -> GridShape.rectangle(0, 2));
        assertThrows(IllegalArgumentException.class, () -> GridShape.rectangle(65, 1));
        assertThrows(IllegalArgumentException.class, () -> GridShape.mask("##", "#"));
        assertThrows(IllegalArgumentException.class, () -> GridShape.mask(".."));
        assertThrows(IllegalArgumentException.class, () -> GridShape.mask("#?"));
        assertEquals(4096, GridShape.rectangle(64, 64).area());
        assertEquals(GridShape.rectangle(1, 1), GridShape.of(new GridCell(Integer.MIN_VALUE, Integer.MAX_VALUE)));
    }

    @Test
    void allowedDirectionsAreIndependentOfGeometricSymmetry() {
        var square = GridFootprint.fixed(GridShape.rectangle(2, 2));
        assertEquals(square.shape(), square.shape().rotated(GridRotation.HALF_TURN));
        assertThrows(IllegalArgumentException.class, () -> square.oriented(GridRotation.HALF_TURN));
        assertThrows(IllegalArgumentException.class, () -> new GridFootprint(square.shape(), Set.of()));
        var directions = java.util.EnumSet.of(GridRotation.NONE, GridRotation.HALF_TURN);
        var footprint = new GridFootprint(square.shape(), directions);
        directions.clear();
        assertEquals(2, footprint.rotations().size());
        assertThrows(UnsupportedOperationException.class, () -> footprint.rotations().clear());
    }

    @Test
    void allNonEmptyThreeByThreeMasksRoundTripThroughQuarterTurns() {
        for (int mask = 1; mask < 512; mask++) {
            List<GridCell> cells = new ArrayList<>();
            for (int bit = 0; bit < 9; bit++) if ((mask & (1 << bit)) != 0) cells.add(new GridCell(bit % 3, bit / 3));
            GridShape original = GridShape.of(cells);
            GridShape rotated = original;
            for (int turn = 0; turn < 4; turn++) {
                rotated = rotated.rotated(GridRotation.CLOCKWISE_90);
                assertEquals(Integer.bitCount(mask), rotated.area());
            }
            assertEquals(original, rotated, "mask=" + mask);
        }
    }
}
