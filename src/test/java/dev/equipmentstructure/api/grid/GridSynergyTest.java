package dev.equipmentstructure.api.grid;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.grid.synergy.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GridSynergyTest {
    private static final ResourceLocation HOST = id("host"), A = id("a"), B = id("b"), C = id("c"), TYPE = id("type");
    @AfterEach void clear() { GridRuleRegistry.clear(); }
    static ResourceLocation id(String s) { return ResourceLocation.parse("test:" + s); }
    private static EquipmentStructure structure() { return new EquipmentHostDefinition(HOST, TYPE, List.of(
            EquipmentSlotDefinition.of(A, TYPE), EquipmentSlotDefinition.of(B, TYPE), EquipmentSlotDefinition.of(C, TYPE))).createStructure()
            .withComponent(A, new EquipmentComponentInstance(A, TYPE)).withComponent(B, new EquipmentComponentInstance(B, TYPE)).withComponent(C, new EquipmentComponentInstance(C, TYPE)); }
    private static GridLayout empty() { return GridLayout.empty(new GridBoard(GridShape.rectangle(10, 10), GridShape.rectangle(1, 1), new GridPlacement(9, 9), false)); }
    private static GridSpatialContext context(GridLayout layout) { return new GridSpatialContext(structure(), layout); }
    private static GridRuleResult eval(GridCondition condition, GridSpatialContext context) { return condition.evaluate(context, A, Map.of(), true); }

    @Test void ringsUseRealCellsAndNeighborCountDiffersFromContactLength() {
        var layout = empty().withComponent(A, GridFootprint.freelyRotating(GridShape.mask("###", "#.#", "###")), new GridPlacement(1, 1))
                .withComponent(B, GridFootprint.SINGLE_CELL, new GridPlacement(2, 2));
        var context = context(layout);
        assertEquals(1, GridSpatialContext.distance(context.require(A), context.require(B), false));
        assertEquals(4, GridSpatialContext.contact(context.require(A), context.require(B)));
        assertTrue(eval(GridCondition.neighbors(GridSelector.any(), 1), context).active());
        assertFalse(eval(GridCondition.neighbors(GridSelector.any(), 2), context).active());
        assertTrue(eval(GridCondition.count(GridCondition.Kind.CONTACT_LENGTH, GridSelector.any(), 4, 4), context).active());
    }
    @Test void diagonalAndOverlapAreExplicitAndReferenceBodyHasGeometry() {
        var layout = empty().withComponent(A, GridFootprint.SINGLE_CELL, new GridPlacement(8, 8))
                .withComponent(B, GridFootprint.SINGLE_CELL, new GridPlacement(9, 9));
        var context = context(layout);
        assertFalse(eval(GridCondition.neighbors(GridSelector.any(), 1), context).active());
        assertTrue(eval(GridCondition.count(GridCondition.Kind.DIAGONAL_NEIGHBORS, GridSelector.any(), 1, 1), context).active());
        assertTrue(GridSpatialContext.overlaps(context.require(B), context.require(GridSpatialContext.BODY)));
        assertTrue(context.neighbors(B, GridSelector.body(), false).isEmpty());
    }
    @Test void localPatternRotatesAndCannotReuseOnePartForTwoRoles() {
        var layout = empty().withComponent(A, GridFootprint.freelyRotating(GridShape.rectangle(2, 1)), new GridPlacement(3, 3, GridRotation.CLOCKWISE_90))
                .withComponent(B, GridFootprint.SINGLE_CELL, new GridPlacement(3, 5));
        var condition = GridCondition.pattern(true, new GridCondition.Role(new GridCell(2, 0), GridSelector.components(B)));
        assertTrue(eval(condition, context(layout)).active());
        assertFalse(eval(GridCondition.pattern(false, new GridCondition.Role(new GridCell(2, 0), GridSelector.any())), context(layout)).active());
        assertFalse(eval(GridCondition.pattern(true, new GridCondition.Role(new GridCell(2, 0), GridSelector.any()),
                new GridCondition.Role(new GridCell(2, 0), GridSelector.any())), context(layout)).active());
    }
    @Test void directionAndDistanceUseSourceRotationAndActualCells() {
        var layout = empty().withComponent(A, GridFootprint.freelyRotating(GridShape.rectangle(1, 1)), new GridPlacement(3, 3, GridRotation.CLOCKWISE_90))
                .withComponent(B, GridFootprint.SINGLE_CELL, new GridPlacement(5, 3));
        var context = context(layout);
        assertTrue(eval(GridCondition.direction(GridSelector.any(), 0, true), context).active());
        assertFalse(eval(GridCondition.direction(GridSelector.any(), 0, false), context).active());
        assertTrue(eval(GridCondition.distance(GridSelector.any(), 2, 2), context).active());
    }
    @Test void namedRegionsSupportAnyAllAndMissingRegionStaysUnknownUnderNegation() {
        var context = context(empty().withComponent(A, GridFootprint.freelyRotating(GridShape.rectangle(2, 1)), new GridPlacement(0, 0)));
        var regions = Map.of(id("edge"), Set.of(new GridCell(0, 0)));
        assertTrue(GridCondition.region(id("edge"), false).evaluate(context, A, regions, true).active());
        assertFalse(GridCondition.region(id("edge"), true).evaluate(context, A, regions, true).active());
        assertEquals(GridRuleResult.Status.UNKNOWN, eval(GridCondition.region(id("absent"), true).not(), context).status());
    }
    @Test void whitespaceDoesNotCountOutsideBoardAndConnectivityFollowsActualContact() {
        var layout = empty().withComponent(A, GridFootprint.SINGLE_CELL, new GridPlacement(0, 0))
                .withComponent(B, GridFootprint.SINGLE_CELL, new GridPlacement(1, 0)).withComponent(C, GridFootprint.SINGLE_CELL, new GridPlacement(2, 0));
        var context = context(layout);
        assertTrue(eval(GridCondition.connected(GridSelector.components(C), GridSelector.components(B)), context).active());
        assertFalse(eval(GridCondition.connected(GridSelector.components(C), GridSelector.components(id("other"))), context).active());
        assertTrue(eval(GridCondition.empty(false, 1, new GridCell(-1, 0), new GridCell(0, 1)), context).active());
        assertFalse(eval(GridCondition.empty(false, 2, new GridCell(-1, 0), new GridCell(0, 1)), context).active());
    }
    @Test void conditionsAndRulesRoundTripWithDeterministicSourceKeys() {
        var condition = GridCondition.all(GridCondition.neighbors(GridSelector.any(), 1), GridCondition.any(GridCondition.custom(id("custom")),
                GridCondition.pattern(true, new GridCondition.Role(new GridCell(-2, 1), GridSelector.slots(B)))).not());
        var rule = new GridRuleDefinition(id("rule"), GridSelector.components(A), condition).inHost(HOST);
        assertEquals(rule, GridRuleDefinition.CODEC.parse(JsonOps.INSTANCE, GridRuleDefinition.CODEC.encodeStart(JsonOps.INSTANCE, rule).getOrThrow()).getOrThrow());
    }
    @Test void customCallbacksAreNeverExecutedInPreviewsAndFailuresRemainUnknown() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        GridRuleRegistry.registerCustom(id("custom"), (c, s) -> { calls.incrementAndGet(); throw new IllegalStateException("fixture"); });
        var condition = GridCondition.custom(id("custom")); var context = context(empty().withComponent(A, GridFootprint.SINGLE_CELL, new GridPlacement(0, 0)));
        assertEquals(GridRuleResult.Status.UNKNOWN, eval(condition.not(), context).status()); assertEquals(0, calls.get());
        assertEquals(GridRuleResult.Status.UNKNOWN, condition.evaluate(context, A, Map.of(), false).status()); assertEquals(1, calls.get());
    }
    @Test void missingAndStaleLayoutsCannotActivateNegativeConditions() {
        var source = structure(); var rule = new GridRuleDefinition(id("negative"), GridSelector.components(A), GridCondition.neighbors(GridSelector.any(), 1).not());
        var definitions = new GridDefinitions(Map.of(), Map.of(), Map.of(), Map.of(rule.id(), rule), Map.of());
        assertEquals(GridRuleResult.Status.UNKNOWN, GridRuleRegistry.evaluate(source, definitions, true).get(new GridRuleRegistry.Key(rule.id(), A)).status());
    }
}
