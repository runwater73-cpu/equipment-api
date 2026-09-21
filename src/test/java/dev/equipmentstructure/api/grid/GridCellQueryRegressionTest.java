package dev.equipmentstructure.api.grid;

import dev.equipmentstructure.api.grid.space.ComponentSpaceDefinition;
import dev.equipmentstructure.api.grid.synergy.GridRegion;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GridCellQueryRegressionTest {
    private static final ResourceLocation SOURCE = ResourceLocation.parse("test:source");
    private static final ResourceLocation OTHER = ResourceLocation.parse("test:other");
    private static final GridShape SHAPE = GridShape.mask("#.", "##");
    // Independent expected geometry for each quarter turn, including the signed attached offset.
    private static final int[][][] PART = {
            {{0, 0}, {0, 1}, {1, 1}}, {{0, 0}, {1, 0}, {0, 1}},
            {{0, 0}, {1, 0}, {1, 1}}, {{1, 0}, {0, 1}, {1, 1}}
    };
    private static final int[][][] SPACE = {
            {{2, 0}, {2, 1}}, {{1, 2}, {0, 2}}, {{-1, 1}, {-1, 0}}, {{0, -1}, {1, -1}}
    };

    @Test
    void repeatedRenderAndCollisionQueriesKeepExactRotatedCells() {
        var footprint = GridFootprint.freelyRotating(SHAPE);
        var attached = ComponentSpaceDefinition.attached(ResourceLocation.parse("test:supply"),
                GridShape.rectangle(1, 2), new GridCell(2, 0));
        var panel = ComponentSpaceDefinition.panel(ResourceLocation.parse("test:pocket"),
                GridShape.mask("###", "#.#", "###"));
        var spaces = List.of(attached, panel);
        var empty = GridLayout.empty(new GridBoard(GridShape.rectangle(32, 32),
                GridShape.rectangle(1, 1), new GridPlacement(31, 31)));
        var rotations = GridRotation.values();
        // Exercise the hot query path well beyond initial method compilation, without a client.
        for (int i = 0; i < 20_000; i++) {
            int turn = i & 3, x = 3 + ((i >> 2) & 7), y = 3 + ((i >> 5) & 7);
            var placement = new GridPlacement(x, y, rotations[turn]);
            var expectedPart = translated(PART[turn], x, y);
            var expectedSpace = translated(SPACE[turn], x, y);
            var expectedReserved = new HashSet<>(expectedPart);
            expectedReserved.addAll(expectedSpace);
            var layout = empty.withComponent(SOURCE, footprint, placement, spaces);
            var part = layout.parts().get(SOURCE);
            assertEquals(expectedPart, part.cells());
            assertEquals(expectedSpace, attached.boardCells(SHAPE, placement));
            assertEquals(expectedReserved, part.reservedCells());
            assertEquals(expectedPart, new GridRegion(SHAPE, placement).cells());
            assertEquals(expectedPart, new GridBoard(empty.board().area(), SHAPE, placement, false).bodyCells());
            assertEquals(1018, layout.freeArea());
            var reserved = expectedSpace.iterator().next();
            assertEquals(GridLayout.Failure.COMPONENT_COLLISION, layout.checkPlacement(OTHER,
                    GridFootprint.SINGLE_CELL, new GridPlacement(reserved.x(), reserved.y())).failure());
            assertEquals(1023, layout.withoutComponent(SOURCE).freeArea());
        }
    }

    @Test
    void queryResultsStayImmutableAndExtremeTranslationsStillFail() {
        var part = new GridLayout.Part(GridFootprint.freelyRotating(GridShape.mask("#.#", "...", "#.#")),
                new GridPlacement(-3, -2));
        var expected = Set.of(new GridCell(-3, -2), new GridCell(-1, -2), new GridCell(-3, 0), new GridCell(-1, 0));
        assertEquals(expected, part.cells());
        assertThrows(UnsupportedOperationException.class, () -> part.cells().clear());
        assertThrows(UnsupportedOperationException.class, () -> part.reservedCells().clear());
        var region = ComponentSpaceDefinition.attached(ResourceLocation.parse("test:region"),
                GridShape.rectangle(1, 2), new GridCell(-1, 0));
        assertThrows(UnsupportedOperationException.class,
                () -> region.boardCells(SHAPE, new GridPlacement(3, 3)).clear());
        var board = GridBoard.defaultBoard(SHAPE);
        assertThrows(UnsupportedOperationException.class, () -> board.bodyCells().clear());
        assertThrows(ArithmeticException.class, () -> new GridLayout.Part(
                GridFootprint.freelyRotating(SHAPE), new GridPlacement(Integer.MAX_VALUE, 0)).cells());
        assertThrows(ArithmeticException.class,
                () -> region.boardCells(SHAPE, new GridPlacement(Integer.MIN_VALUE, 0)));
        assertEquals(expected, part.cells());
    }

    private static Set<GridCell> translated(int[][] points, int x, int y) {
        var result = new HashSet<GridCell>();
        for (int[] point : points) result.add(new GridCell(point[0] + x, point[1] + y));
        return Set.copyOf(result);
    }
}
