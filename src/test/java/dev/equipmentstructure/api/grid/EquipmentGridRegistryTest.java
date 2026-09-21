package dev.equipmentstructure.api.grid;

import dev.equipmentstructure.api.EquipmentComponentDefinition;
import dev.equipmentstructure.api.EquipmentComponentRegistry;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentGridRegistryTest {
    private static final ResourceLocation HOST = id("host");
    private static final ResourceLocation PART = id("part");

    @AfterEach
    void cleanup() {
        EquipmentGridRegistry.replaceTemplates(java.util.Map.of());
        EquipmentGridRegistry.unregisterHost(HOST);
        EquipmentComponentRegistry.unregister(PART);
    }

    @Test
    void equipmentOptsInExplicitlyIncludingItsBodyOccupancyPolicy() {
        assertTrue(EquipmentGridRegistry.host(HOST).isEmpty());
        var board = GridBoard.defaultBoard(GridShape.rectangle(2, 3)).withBodyOccupancy(false);
        EquipmentGridRegistry.registerHost(HOST, board);
        assertFalse(EquipmentGridRegistry.host(HOST).orElseThrow().bodyOccupiesCells());
        assertThrows(IllegalStateException.class, () -> EquipmentGridRegistry.registerHost(HOST, board.withBodyOccupancy(true)));
        assertFalse(EquipmentGridRegistry.host(HOST).orElseThrow().bodyOccupiesCells());
    }

    @Test
    void changingWorldTemplatesRemovesStaleBoardsAndRestoresCodeDefaults() {
        var code = GridBoard.defaultBoard(GridShape.rectangle(1, 2));
        var data = code.withBodyOccupancy(false);
        var otherHost = id("world_only");
        EquipmentGridRegistry.registerHost(HOST, code);
        var original = GridDefinitions.registered().fingerprint();
        EquipmentGridRegistry.replaceTemplates(java.util.Map.of(HOST, data, otherHost, data));
        assertEquals(data, EquipmentGridRegistry.host(HOST).orElseThrow());
        assertNotEquals(original, GridDefinitions.registered().fingerprint());
        EquipmentGridRegistry.replaceTemplates(java.util.Map.of());
        assertEquals(code, EquipmentGridRegistry.host(HOST).orElseThrow());
        assertTrue(EquipmentGridRegistry.host(otherHost).isEmpty());
        assertEquals(original, GridDefinitions.registered().fingerprint());
    }

    @Test
    void componentRequiresExplicitShapeAndShapeDoesNotChangeInterfaceCompatibility() {
        var type = id("type");
        var definition = EquipmentComponentDefinition.builder(PART, type, type)
                .suitableFor(HOST)
                .footprint(GridFootprint.SINGLE_CELL)
                .removable(false)
                .build();
        assertEquals(GridFootprint.SINGLE_CELL, definition.footprint());
        var large = definition.withFootprint(GridFootprint.fixed(GridShape.mask("###", "#.#", "###")));
        assertEquals(definition.id(), large.id());
        assertEquals(definition.itemFactory(), large.itemFactory());
        assertFalse(large.removable());
        EquipmentComponentRegistry.register(large);
        assertEquals(8, EquipmentComponentRegistry.get(PART).orElseThrow().footprint().shape().area());
        assertTrue(new EquipmentSlotDefinition(id("slot"), type).accepts(HOST, large.createInstance()));
        assertFalse(new EquipmentSlotDefinition(id("other_slot"), id("other_type"))
                .accepts(HOST, large.createInstance()));
        assertEquals(1, definition.footprint().shape().area());
        assertThrows(NullPointerException.class, () -> definition.withFootprint(null));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("grid_registry_test", path);
    }
}
