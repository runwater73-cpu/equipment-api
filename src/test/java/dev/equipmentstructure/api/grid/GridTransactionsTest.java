package dev.equipmentstructure.api.grid;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructure;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static dev.equipmentstructure.api.grid.GridTransactions.Status.*;

class GridTransactionsTest {
    static final ResourceLocation HOST = id("host"), A = id("a"), B = id("b"), C = id("c"), TYPE = id("type"), PART = id("part"), LARGE = id("large");
    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("grid_transactions_test", path); }
    static EquipmentStructure empty() {
        return new EquipmentStructure(HOST, TYPE, List.of(EquipmentSlotDefinition.of(A, TYPE),
                EquipmentSlotDefinition.of(B, TYPE), EquipmentSlotDefinition.of(C, TYPE)), Map.of());
    }
    static EquipmentComponentInstance part(ResourceLocation id) { return new EquipmentComponentInstance(id, TYPE); }
    static GridDefinitions definitions(boolean body) {
        return new GridDefinitions(Map.of(HOST, new GridBoard(GridShape.rectangle(3, 2), GridShape.rectangle(1, 1),
                new GridPlacement(2, 1), body)), Map.of(PART, GridFootprint.SINGLE_CELL,
                LARGE, GridFootprint.freelyRotating(GridShape.rectangle(2, 1))));
    }
    static EquipmentStructure install(EquipmentStructure original, ResourceLocation slot, ResourceLocation part,
                                      GridPlacement p, GridDefinitions definitions) {
        var plan = GridTransactions.install(original, slot, part(part), Optional.ofNullable(p), definitions);
        assertTrue(plan.allowed(), plan.status().toString());
        return plan.structure();
    }

    @Test void explicitAndAutomaticInstallShareBoundariesAndBodyPolicy() {
        var solid = definitions(true);
        assertEquals(PLACEMENT_REJECTED, GridTransactions.install(empty(), A, part(PART),
                Optional.of(new GridPlacement(2, 1)), solid).status());
        var open = install(empty(), A, PART, new GridPlacement(2, 1), definitions(false));
        assertEquals(5, GridTransactions.resolve(open, definitions(false)).layout().orElseThrow().freeArea());
        var automatic = install(empty(), A, LARGE, null, solid);
        assertEquals(new GridPlacement(0, 0), automatic.grid().orElseThrow().placements().get(A));
        assertEquals(PLACEMENT_REJECTED, GridTransactions.install(automatic, B, part(PART),
                Optional.of(new GridPlacement(0, 0)), solid).status());
    }

    @Test void batchSwapValidatesFinalGeometryAndDoesNotChangeParts() {
        var defs = definitions(true);
        var original = install(install(empty(), A, PART, new GridPlacement(0, 0), defs), B, PART, new GridPlacement(1, 0), defs);
        assertEquals(PLACEMENT_REJECTED, GridTransactions.move(original, Map.of(A, new GridPlacement(1, 0)), defs).status());
        var plan = GridTransactions.move(original, Map.of(A, new GridPlacement(1, 0), B, new GridPlacement(0, 0)), defs);
        assertEquals(VALID, plan.status());
        assertEquals(original.components(), plan.structure().components());
        assertEquals(original.grid().orElseThrow().revision() + 1, plan.structure().grid().orElseThrow().revision());
        assertEquals(new GridPlacement(0, 0), original.grid().orElseThrow().placements().get(A));
        assertEquals(EMPTY_SLOT, GridTransactions.move(original, Map.of(C, new GridPlacement(0, 1)), defs).status());
        assertEquals(UNKNOWN_SLOT, GridTransactions.move(original, Map.of(id("unknown"), new GridPlacement(0, 1)), defs).status());
    }

    @Test void replacementRetainsOriginAndDoesNotDisplaceNeighbors() {
        var defs = definitions(true);
        var original = install(install(empty(), A, PART, new GridPlacement(0, 0), defs), B, PART, new GridPlacement(1, 0), defs);
        var failed = GridTransactions.install(original, A, part(LARGE), Optional.empty(), defs);
        assertEquals(PLACEMENT_REJECTED, failed.status());
        assertSame(original, failed.structure());
        var rotated = GridTransactions.install(original, A, part(LARGE), Optional.of(new GridPlacement(0, 0, GridRotation.CLOCKWISE_90)), defs);
        assertTrue(rotated.allowed());
        assertEquals(original.component(B), rotated.structure().component(B));
        assertEquals(new GridPlacement(1, 0), rotated.structure().grid().orElseThrow().placements().get(B));
    }

    @Test void populatedSnapshotsWithoutSavedCoordinatesAreRejected() {
        var malformed = empty().withComponent(B, part(LARGE)).withComponent(A, part(LARGE));
        var plan = GridTransactions.initializeOrRefresh(malformed, definitions(true));
        assertEquals(INVALID_LAYOUT, plan.status());
        assertSame(malformed, plan.structure());
        assertTrue(GridTransactions.initializeOrRefresh(empty(), definitions(true)).allowed());
    }

    @Test void definitionChangesRequireExplicitRefreshAndNeverEraseParts() {
        var defs = definitions(false);
        var original = install(empty(), A, PART, new GridPlacement(2, 1), defs);
        assertEquals(STALE_DEFINITIONS, GridTransactions.resolve(original, definitions(true)).status());
        assertEquals(INVALID_LAYOUT, GridTransactions.initializeOrRefresh(original, definitions(true)).status());
        var missing = new GridDefinitions(defs.hosts(), Map.of());
        assertEquals(MISSING_DEFINITION, GridTransactions.resolve(original, missing).status());
        assertEquals(MISSING_DEFINITION, GridTransactions.resolve(original, GridDefinitions.EMPTY).status());
        var recovered = GridTransactions.remove(original, A, missing);
        assertTrue(recovered.components().isEmpty());
        assertTrue(recovered.grid().orElseThrow().placements().isEmpty());
        assertTrue(GridTransactions.initializeOrRefresh(recovered, definitions(true)).allowed());
    }

    @Test void compatibleDefinitionRefreshKeepsSavedOrigins() {
        var defs = definitions(true);
        var original = install(empty(), A, PART, new GridPlacement(1, 0), defs);
        var changed = definitions(false);
        var plan = GridTransactions.initializeOrRefresh(original, changed);
        assertEquals(VALID, plan.status());
        assertEquals(original.grid().orElseThrow().placements(), plan.structure().grid().orElseThrow().placements());
        assertNotEquals(original.grid().orElseThrow().definitions(), plan.structure().grid().orElseThrow().definitions());
        assertEquals(VALID, GridTransactions.resolve(plan.structure(), changed).status());
    }

    @Test void orphanOrMissingPlacementsCannotBeSilentlyRepacked() {
        var original = install(empty(), A, PART, new GridPlacement(0, 0), definitions(true));
        var state = original.grid().orElseThrow();
        var missing = original.withGrid(new GridState(1, state.revision(), state.definitions(), Map.of()));
        assertEquals(INVALID_LAYOUT, GridTransactions.resolve(missing, definitions(true)).status());
        assertEquals(INVALID_LAYOUT, GridTransactions.initializeOrRefresh(missing, definitions(true)).status());
        var orphan = original.withGrid(new GridState(1, 1, state.definitions(), Map.of(A, new GridPlacement(0, 0), B, new GridPlacement(1, 0))));
        assertEquals(INVALID_LAYOUT, GridTransactions.resolve(orphan, definitions(true)).status());
    }

    @Test void unrelatedDefinitionsDoNotInvalidateSavedEquipmentButChangeRequestToken() {
        var defs = definitions(true);
        var changedParts = new java.util.HashMap<>(defs.components());
        changedParts.put(id("unrelated"), GridFootprint.SINGLE_CELL);
        var more = new GridDefinitions(defs.hosts(), changedParts);
        var original = install(empty(), A, PART, null, defs);
        assertEquals(VALID, GridTransactions.resolve(original, more).status());
        assertEquals(defs.fingerprint(original), more.fingerprint(original));
        assertNotEquals(defs.fingerprint(), more.fingerprint());
    }

    @Test void nonSpatialTemplatesDoNotAcquireLayouts() {
        var plan = GridTransactions.install(empty(), A, part(PART), Optional.empty(), GridDefinitions.EMPTY);
        assertEquals(DISABLED, plan.status());
        assertTrue(plan.structure().grid().isEmpty());
        assertEquals(part(PART), plan.structure().component(A).orElseThrow());
        assertEquals(PLACEMENT_REJECTED, GridTransactions.install(empty(), A, part(PART),
                Optional.of(new GridPlacement(0, 0)), GridDefinitions.EMPTY).status());
    }
}
