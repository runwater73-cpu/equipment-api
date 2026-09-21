package dev.equipmentstructure.api.grid;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static dev.equipmentstructure.api.grid.GridLayout.Failure.*;
import static org.junit.jupiter.api.Assertions.*;

class GridLayoutTest {
    private static final ResourceLocation FIRST = id("first");
    private static final ResourceLocation SECOND = id("second");
    private static final GridFootprint SINGLE = GridFootprint.SINGLE_CELL;

    @Test
    void defaultBoardAndEmptyInterfacesDoNotReserveComponentCells() {
        var board = GridBoard.defaultBoard(GridShape.rectangle(3, 3));
        var layout = GridLayout.empty(board);
        assertEquals(new GridPlacement(3, 3), board.bodyPlacement());
        assertEquals(81, board.area().area());
        assertEquals(72, layout.freeArea());
        assertTrue(layout.parts().isEmpty());
        assertSame(layout, layout.withoutComponent(FIRST));
    }

    @Test
    void bodyOccupancyIsAnEquipmentAuthorOptionAndKeepsReferenceGeometry() {
        var solid = GridBoard.defaultBoard(GridShape.rectangle(3, 3));
        var reference = solid.withBodyOccupancy(false);
        var point = new GridPlacement(4, 4);
        assertEquals(BODY_COLLISION, GridLayout.empty(solid).checkPlacement(FIRST, SINGLE, point).failure());
        var layout = GridLayout.empty(reference);
        assertTrue(layout.checkPlacement(FIRST, SINGLE, point).allowed());
        assertEquals(solid.bodyCells(), reference.bodyCells());
        assertEquals(9, reference.bodyCells().size());
        assertTrue(reference.blockedBodyCells().isEmpty());
        assertEquals(81, layout.freeArea());
        var occupied = layout.withComponent(FIRST, SINGLE, point);
        assertEquals(80, occupied.freeArea());
        assertEquals(81, occupied.withoutComponent(FIRST).freeArea());
        assertEquals(solid, reference.withBodyOccupancy(true));
    }

    @Test
    void ringCanSurroundBodyWithoutCollidingWithItsBoundingBox() {
        var board = new GridBoard(GridShape.rectangle(5, 5), GridShape.rectangle(1, 1), new GridPlacement(2, 2));
        var ring = GridFootprint.freelyRotating(GridShape.mask("###", "#.#", "###"));
        var layout = GridLayout.empty(board).withComponent(FIRST, ring, new GridPlacement(1, 1));
        assertTrue(layout.componentAt(new GridCell(2, 2)).isEmpty());
        assertEquals(FIRST, layout.componentAt(new GridCell(1, 2)).orElseThrow());
        assertEquals(16, layout.freeArea());
        assertEquals(BODY_COLLISION, layout.checkPlacement(SECOND, SINGLE, new GridPlacement(2, 2)).failure());
        var referenceLayout = GridLayout.empty(board.withBodyOccupancy(false))
                .withComponent(FIRST, ring, new GridPlacement(1, 1))
                .withComponent(SECOND, SINGLE, new GridPlacement(2, 2));
        assertEquals(SECOND, referenceLayout.componentAt(new GridCell(2, 2)).orElseThrow());
        assertEquals(16, referenceLayout.freeArea());
    }

    @Test
    void enoughFreeAreaDoesNotPermitPlacingAcrossAnInvalidBoardHole() {
        var area = GridShape.mask("#####", "##.##", "#####", "#####");
        var board = new GridBoard(area, GridShape.rectangle(1, 1), new GridPlacement(0, 0));
        var layout = GridLayout.empty(board);
        assertEquals(18, layout.freeArea());
        var line = GridFootprint.freelyRotating(GridShape.rectangle(3, 1));
        assertEquals(OUTSIDE_BOARD, layout.checkPlacement(FIRST, line, new GridPlacement(1, 1)).failure());
        assertTrue(layout.checkPlacement(FIRST, line, new GridPlacement(1, 2)).allowed());
    }

    @Test
    void movingIgnoresOwnOldCellsButPreservesOtherPartsAndStableIdentity() {
        var original = GridLayout.empty(board()).withComponent(FIRST,
                        GridFootprint.freelyRotating(GridShape.rectangle(2, 1)), new GridPlacement(0, 0))
                .withComponent(SECOND, SINGLE, new GridPlacement(3, 0));
        var moved = original.moved(FIRST, new GridPlacement(1, 0));
        assertEquals(2, moved.parts().size());
        assertEquals(new GridPlacement(0, 0), original.parts().get(FIRST).placement());
        assertEquals(new GridPlacement(1, 0), moved.parts().get(FIRST).placement());
        assertEquals(COMPONENT_COLLISION, moved.checkPlacement(FIRST,
                moved.parts().get(FIRST).footprint(), new GridPlacement(2, 0)).failure());
        assertEquals(SECOND, moved.checkPlacement(FIRST,
                moved.parts().get(FIRST).footprint(), new GridPlacement(2, 0)).conflictingSlot().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> moved.moved(FIRST, new GridPlacement(2, 0)));
        assertEquals(SECOND, moved.componentAt(new GridCell(3, 0)).orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> moved.parts().clear());
    }

    @Test
    void replacingChangesOnlyTheTargetFootprintAndFailureLeavesOldStateIntact() {
        var original = GridLayout.empty(board()).withComponent(FIRST, SINGLE, new GridPlacement(0, 0))
                .withComponent(SECOND, SINGLE, new GridPlacement(2, 0));
        var two = GridFootprint.freelyRotating(GridShape.rectangle(2, 1));
        var replacement = original.withComponent(FIRST, two, new GridPlacement(0, 0));
        assertEquals(1, original.parts().get(FIRST).footprint().shape().area());
        assertEquals(2, replacement.parts().get(FIRST).footprint().shape().area());
        var three = GridFootprint.freelyRotating(GridShape.rectangle(3, 1));
        assertThrows(IllegalArgumentException.class, () -> replacement.withComponent(FIRST, three, new GridPlacement(0, 0)));
        assertEquals(2, replacement.parts().get(FIRST).footprint().shape().area());
        var removed = replacement.withoutComponent(FIRST);
        assertEquals(replacement.freeArea() + 2, removed.freeArea());
        assertTrue(removed.componentAt(new GridCell(0, 0)).isEmpty());
        assertEquals(SECOND, removed.componentAt(new GridCell(2, 0)).orElseThrow());
    }

    @Test
    void rotationValidatesPermissionsAndNewBoundsWhileRetainingDirection() {
        var square = GridFootprint.fixed(GridShape.rectangle(2, 2));
        var layout = GridLayout.empty(board());
        assertEquals(ROTATION_NOT_ALLOWED, layout.checkPlacement(FIRST, square,
                new GridPlacement(0, 0, GridRotation.HALF_TURN)).failure());
        var line = GridFootprint.freelyRotating(GridShape.rectangle(3, 1));
        assertEquals(OUTSIDE_BOARD, layout.checkPlacement(FIRST, line, new GridPlacement(4, 0)).failure());
        var rotated = layout.withComponent(FIRST, line, new GridPlacement(4, 0, GridRotation.CLOCKWISE_90));
        assertEquals(GridRotation.CLOCKWISE_90, rotated.parts().get(FIRST).placement().rotation());
        assertEquals(FIRST, rotated.componentAt(new GridCell(4, 2)).orElseThrow());
        assertTrue(rotated.componentAt(new GridCell(3, 0)).isEmpty());
    }

    @Test
    void extremeOriginsAreRejectedWithoutWrappingIntoTheBoard() {
        var layout = GridLayout.empty(board());
        var line = GridFootprint.freelyRotating(GridShape.rectangle(2, 1));
        for (int coordinate : new int[]{Integer.MIN_VALUE, -1, Integer.MAX_VALUE}) {
            assertEquals(OUTSIDE_BOARD, layout.checkPlacement(FIRST, line, new GridPlacement(coordinate, 0)).failure());
            assertEquals(OUTSIDE_BOARD, layout.checkPlacement(FIRST, line, new GridPlacement(0, coordinate)).failure());
        }
        assertThrows(IllegalArgumentException.class, () -> new GridBoard(GridShape.rectangle(5, 5),
                GridShape.rectangle(2, 1), new GridPlacement(Integer.MAX_VALUE, 0)));
    }

    @Test
    void nonBlockingBodyStillRequiresAValidReferenceInsideThePanel() {
        assertThrows(IllegalArgumentException.class, () -> new GridBoard(GridShape.rectangle(3, 3),
                GridShape.rectangle(2, 2), new GridPlacement(2, 2), false));
        assertThrows(IllegalArgumentException.class, () -> GridBoard.defaultBoard(GridShape.rectangle(10, 1)));
    }

    private static GridBoard board() {
        return new GridBoard(GridShape.rectangle(5, 5), GridShape.rectangle(1, 1), new GridPlacement(4, 4));
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("grid_test", path); }
}
