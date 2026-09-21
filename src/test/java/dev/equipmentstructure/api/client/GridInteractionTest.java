package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.grid.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class GridInteractionTest {
    private static final ResourceLocation A = ResourceLocation.parse("test:a"), B = ResourceLocation.parse("test:b");
    private static GridViewport camera() {
        var view = new GridViewport(110, 26, 164, 114);
        view.fit(GridShape.rectangle(9, 9));
        return view;
    }

    @Test void drawAndHitUseSameCellsAfterPanAndZoom() {
        var view = camera();
        view.pan(-37.5, 81.25);
        view.zoom(187.2, 76.8, 3);
        for (int x = -5; x < 15; x++) for (int y = -5; y < 15; y++) {
            assertEquals(new GridCell(x, y), view.cell(view.screenX(x) + view.scale() * .5,
                    view.screenY(y) + view.scale() * .5));
        }
    }

    @Test void zoomKeepsPointUnderPointerFixed() {
        var view = camera();
        double x = view.screenX(3) + view.scale() * .27, y = view.screenY(6) + view.scale() * .81;
        view.zoom(x, y, 2);
        assertEquals(x, view.screenX(3) + view.scale() * .27, 1e-8);
        assertEquals(y, view.screenY(6) + view.scale() * .81, 1e-8);
    }

    @Test void negativeCoordinatesUseFloorAndViewportExcludesRightBottomEdges() {
        var view = camera();
        assertEquals(new GridCell(-1, -1), view.cell(view.screenX(0) - .01, view.screenY(0) - .01));
        assertTrue(view.contains(110, 26));
        assertFalse(view.contains(274, 60));
        assertFalse(view.contains(150, 140));
    }

    @Test void largeBoardFitsAndScaleIsBounded() {
        var view = camera();
        view.fit(GridShape.rectangle(64, 64));
        assertTrue(view.contains(view.screenX(0), view.screenY(0)));
        assertTrue(view.contains(view.screenX(64), view.screenY(64)));
        for (int n = 0; n < 10; n++) view.zoom(170, 80, 100);
        assertEquals(GridViewport.MAX_SCALE, view.scale());
        for (int n = 0; n < 10; n++) view.zoom(170, 80, -100);
        assertEquals(GridViewport.MIN_SCALE, view.scale());
    }

    @Test void holeIsNotAnOccupiedHitEvenAfterChangingCamera() {
        var layout = GridLayout.empty(GridBoard.defaultBoard(GridShape.rectangle(1, 1)).withBodyOccupancy(false))
                .withComponent(A, GridFootprint.freelyRotating(GridShape.mask("###", "#.#", "###")), new GridPlacement(2, 1));
        var view = camera();
        view.pan(11.5, -12);
        view.zoom(150, 72, 1);
        assertTrue(layout.componentAt(view.cell(view.screenX(3) + 1, view.screenY(2) + 1)).isEmpty());
        assertEquals(A, layout.componentAt(view.cell(view.screenX(2) + 1, view.screenY(2) + 1)).orElseThrow());
    }

    @Test void rotationKeepsGrabbedOccupiedCellAndCompletesRoundTrip() {
        var foot = GridFootprint.freelyRotating(GridShape.mask("#..", "###"));
        var original = new GridDragGeometry(foot, GridRotation.NONE, new GridCell(2, 1));
        var grip = original;
        var pointer = new GridCell(7, 5);
        for (int n = 0; n < 4; n++) {
            grip = grip.clockwise();
            assertTrue(foot.oriented(grip.rotation()).contains(grip.grab()));
            assertEquals(pointer, grip.at(pointer).translate(grip.grab()));
        }
        assertEquals(original, grip);
    }

    @Test void restrictedRotationSkipsDisallowedDirectionsWithoutLosingGrabPoint() {
        var foot = new GridFootprint(GridShape.mask("##.", ".##"), Set.of(GridRotation.NONE, GridRotation.HALF_TURN));
        var original = new GridDragGeometry(foot, GridRotation.NONE, new GridCell(0, 0));
        var rotated = original.clockwise();
        assertEquals(GridRotation.HALF_TURN, rotated.rotation());
        assertEquals(new GridCell(2, 1), rotated.grab());
        assertEquals(original, rotated.clockwise());
    }

    @Test void fixedRotationIsANoopAndHolesCannotBeGrabbed() {
        var foot = GridFootprint.fixed(GridShape.mask("###", "#.#", "###"));
        var grip = GridDragGeometry.start(foot);
        assertEquals(grip, grip.clockwise());
        assertThrows(IllegalArgumentException.class, () -> new GridDragGeometry(foot, GridRotation.NONE, new GridCell(1, 1)));
    }

    @Test void sidebarGapsAndMissingCellsAreNotItemTargets() {
        assertEquals(0, GridSidebarLayout.indexAt(298, 29, 0, 5));
        assertEquals(4, GridSidebarLayout.indexAt(298, 50, 0, 5));
        assertEquals(20, GridSidebarLayout.indexAt(298, 134, 0, 24));
        assertEquals(-1, GridSidebarLayout.indexAt(297.9, 29, 0, 5));
        assertEquals(-1, GridSidebarLayout.indexAt(317, 29, 0, 5));
        assertEquals(-1, GridSidebarLayout.indexAt(319, 50, 0, 5));
        assertEquals(-1, GridSidebarLayout.indexAt(298, 155, 0, 28));
        assertEquals(-1, GridSidebarLayout.indexAt(382, 29, 0, 20));
    }

    @Test void scrollingCanReachLastInterfaceAndClampsAfterHostChanges() {
        assertEquals(250, GridSidebarLayout.maxScrollRow(1024));
        assertEquals(1023, GridSidebarLayout.indexAt(362, 134, 250, 1024));
        assertEquals(0, GridSidebarLayout.indexAt(299, 30, 250, 1));
        assertEquals(-1, GridSidebarLayout.indexAt(299, 30, 0, 0));
    }

    @Test void sidebarInstallationAvoidsSolidBodyAndAllowsReferenceOverlap() {
        var board = new GridBoard(GridShape.rectangle(3, 1), GridShape.rectangle(1, 1), new GridPlacement(0, 0));
        assertEquals(new GridPlacement(1, 0), GridSidebarPlacement.find(GridLayout.empty(board), A, GridFootprint.SINGLE_CELL).orElseThrow());
        assertEquals(new GridPlacement(0, 0), GridSidebarPlacement.find(GridLayout.empty(board.withBodyOccupancy(false)), A,
                GridFootprint.SINGLE_CELL).orElseThrow());
    }

    @Test void replacementRetainsPositionWhenItFits() {
        var layout = GridLayout.empty(GridBoard.defaultBoard(GridShape.rectangle(1, 1)))
                .withComponent(A, GridFootprint.SINGLE_CELL, new GridPlacement(6, 3));
        assertEquals(new GridPlacement(6, 3), GridSidebarPlacement.find(layout, A,
                GridFootprint.freelyRotating(GridShape.rectangle(2, 1))).orElseThrow());
    }

    @Test void biggerReplacementFindsSpaceWithoutMovingOtherParts() {
        var layout = GridLayout.empty(GridBoard.defaultBoard(GridShape.rectangle(1, 1)))
                .withComponent(A, GridFootprint.SINGLE_CELL, new GridPlacement(8, 8))
                .withComponent(B, GridFootprint.SINGLE_CELL, new GridPlacement(0, 0));
        var position = GridSidebarPlacement.find(layout, A, GridFootprint.fixed(GridShape.rectangle(2, 2))).orElseThrow();
        assertEquals(new GridPlacement(1, 0), position);
        assertEquals(new GridPlacement(8, 8), layout.parts().get(A).placement());
        assertEquals(new GridPlacement(0, 0), layout.parts().get(B).placement());
    }

    @Test void fullBoardRejectsNewInstallationButReplacementReusesOwnCells() {
        var board = new GridBoard(GridShape.rectangle(2, 1), GridShape.rectangle(1, 1), new GridPlacement(0, 0));
        var layout = GridLayout.empty(board).withComponent(A, GridFootprint.SINGLE_CELL, new GridPlacement(1, 0));
        assertTrue(GridSidebarPlacement.find(layout, B, GridFootprint.SINGLE_CELL).isEmpty());
        assertEquals(new GridPlacement(1, 0), GridSidebarPlacement.find(layout, A, GridFootprint.SINGLE_CELL).orElseThrow());
    }
}
