package dev.equipmentstructure.api.grid;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.grid.space.*;
import dev.equipmentstructure.api.grid.synergy.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Repeatable CPU probe; timings are diagnostic, never a machine-dependent pass threshold. */
class GridPerformanceProbe {
    private static ResourceLocation id(String name) { return ResourceLocation.parse("perf:" + name); }
    @Test void fullEquipmentQueriesAndNoRoomSearch() {
        var host = id("host"); var part = id("part"); var type = id("type"); var rule = id("rule");
        var slots = new ArrayList<EquipmentSlotDefinition>();
        var components = new HashMap<ResourceLocation, List<EquipmentComponentInstance>>();
        var positions = new HashMap<ResourceLocation, GridPlacement>();
        var footprint = GridFootprint.fixed(GridShape.rectangle(2, 1));
        var space = ComponentSpaceDefinition.attached(id("space"), GridShape.rectangle(1, 1), new GridCell(2, 0));
        var board = new GridBoard(GridShape.rectangle(9, 9), GridShape.rectangle(1, 1), new GridPlacement(8, 8), false);
        for (int i = 0; i < 20; i++) {
            var slot = id("slot_" + i);
            slots.add(EquipmentSlotDefinition.of(slot, type));
            components.put(slot, List.of(new EquipmentComponentInstance(part, type)));
            positions.put(slot, new GridPlacement((i % 3) * 3, i / 3));
        }
        var definitions = new GridDefinitions(Map.of(host, board), Map.of(part, footprint), Map.of(part, List.of(space)),
                Map.of(rule, new GridRuleDefinition(rule, GridSelector.any(), GridCondition.neighbors(GridSelector.any(), 1))), Map.of());
        var unsaved = new EquipmentStructure(host, host, slots, components);
        var structure = unsaved.withGrid(new GridState(GridState.FORMAT, 0, definitions.fingerprint(unsaved), positions));
        var layout = GridTransactions.resolve(structure, definitions).layout().orElseThrow();
        var oversized = GridFootprint.freelyRotating(GridShape.rectangle(4, 4));
        for (int i = 0; i < 3; i++) {
            GridRuleRegistry.evaluate(structure, definitions, true);
            GridTransactions.firstFit(layout, id("incoming"), oversized);
        }
        long started = System.nanoTime();
        for (int i = 0; i < 100; i++) assertEquals(20, GridRuleRegistry.evaluate(structure, definitions, true).size());
        long queries = System.nanoTime() - started;
        started = System.nanoTime();
        for (int i = 0; i < 100; i++) assertTrue(GridTransactions.firstFit(layout, id("incoming"), oversized).isEmpty());
        long searches = System.nanoTime() - started;
        System.out.printf(Locale.ROOT, "GRID_PERF full_20_part_queries_100_ms=%.3f no_room_search_100_ms=%.3f%n", queries / 1e6, searches / 1e6);
    }
}
