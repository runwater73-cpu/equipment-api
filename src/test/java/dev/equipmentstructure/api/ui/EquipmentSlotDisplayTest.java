package dev.equipmentstructure.api.ui;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentSlotDisplayTest {
    private static final ResourceLocation HOST = id("host"), SLOT = id("mount"), ICON = id("slot/core");
    @AfterEach void clear() { EquipmentStructureUiRegistry.clear(); }

    @Test void codecsRoundTripAuthoredMetadataAndDefaults() {
        var display = new EquipmentSlotDisplay(Optional.of("example.mount"), Optional.of(ICON), List.of("example.help"));
        var encoded = EquipmentSlotDisplay.CODEC.encodeStart(JsonOps.INSTANCE, display).getOrThrow();
        assertEquals(display, EquipmentSlotDisplay.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertEquals(EquipmentSlotDisplay.defaults(), parse("{}"));
        assertEquals("slot.example.mount", parse("{}").nameKey(SLOT));
        assertEquals("example:mount", parse("{}").name(SLOT).getString());
        assertEquals(2, display.tooltip(SLOT).size());
    }

    @Test void badKeysAndOversizedDescriptionsAreRejected() {
        for (var json : List.of("{\"name\":\" \"}", "{\"empty_icon\":\"Bad:Path\"}",
                "{\"description\":[\"\"]}", "{\"description\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\",\"h\",\"i\"]}")) {
            assertTrue(EquipmentSlotDisplay.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent(), json);
        }
        assertThrows(IllegalArgumentException.class, () -> new EquipmentSlotDisplay(Optional.of(""), Optional.empty(), List.of()));
    }

    @Test void authorInputAndReturnedCollectionsCannotChangeMetadata() {
        var lines = new ArrayList<>(List.of("example.help"));
        var display = new EquipmentSlotDisplay(Optional.empty(), Optional.empty(), lines);
        var slots = new HashMap<ResourceLocation, EquipmentSlotDisplay>(); slots.put(SLOT, display);
        var ui = new EquipmentStructureUiDefinition(0, EquipmentStructureUiDefinition.Texts.defaults(), slots);
        lines.clear(); slots.clear();
        assertEquals(List.of("example.help"), ui.slot(SLOT).descriptionKeys());
        assertThrows(UnsupportedOperationException.class, () -> ui.slots().clear());
        assertThrows(UnsupportedOperationException.class, () -> display.descriptionKeys().clear());
        var tooltip = display.tooltip(SLOT);
        tooltip.getFirst().copy().append("changed");
        assertEquals("example:mount", display.tooltip(SLOT).getFirst().getString());
    }

    @Test void oldConstructorAndUnknownSlotsKeepDefaultPresentation() {
        var old = new EquipmentStructureUiDefinition(0x778899, EquipmentStructureUiDefinition.Texts.defaults(), Map.of());
        assertEquals(0xFF778899, old.accentColor());
        assertEquals(EquipmentSlotDisplay.defaults(), old.slot(SLOT));
        var ui = EquipmentStructureUiDefinition.builder().slot(SLOT, parse("{\"name\":\"custom\"}")).build();
        assertTrue(ui.validateForHost(List.of(EquipmentSlotDefinition.of(SLOT, id("type")))).isEmpty());
        assertTrue(ui.validateForHost(List.of(EquipmentSlotDefinition.of(id("different"), id("type"))))
                .stream().anyMatch(message -> message.contains(SLOT.toString())));
    }

    @Test void hostOverridesAreIsolatedAndDoNotReorderLayoutOrReplaceOtherDefaults() {
        var first = EquipmentStructureUiDefinition.builder().accentColor(0xFF123456)
                .slot(SLOT, parse("{\"name\":\"java.first\"}")).slot(id("other"), parse("{\"name\":\"java.other\"}")).build();
        var second = EquipmentStructureUiDefinition.builder().slot(SLOT, parse("{\"name\":\"java.second\"}")).build();
        EquipmentStructureUiRegistry.register(HOST, first);
        EquipmentStructureUiRegistry.register(id("another_host"), second);
        var overrides = new HashMap<ResourceLocation, EquipmentSlotDisplay>(); overrides.put(SLOT, parse("{\"name\":\"resource.first\"}"));
        EquipmentStructureUiRegistry.replaceResourceSlots(Map.of(HOST, overrides)); overrides.clear();
        var active = EquipmentStructureUiRegistry.get(HOST);
        assertEquals("resource.first", active.slot(SLOT).nameKey(SLOT));
        assertEquals("java.second", EquipmentStructureUiRegistry.get(id("another_host")).slot(SLOT).nameKey(SLOT));
        assertEquals(first.texts(), active.texts()); assertEquals(first.accentColor(), active.accentColor());
        assertEquals(first.slot(id("other")), active.slot(id("other")));
        assertEquals("java.first", first.slot(SLOT).nameKey(SLOT));
    }

    @Test void emptyOverrideDisablesAuthoredDecorationAndDeletedResourceRestoresJava() {
        var authored = parse("{\"name\":\"java.mount\",\"empty_icon\":\"example:icon\",\"description\":[\"java.help\"]}");
        EquipmentStructureUiRegistry.register(HOST, EquipmentStructureUiDefinition.builder().slot(SLOT, authored).build());
        EquipmentStructureUiRegistry.replaceResourceSlots(Map.of(HOST, Map.of(SLOT, EquipmentSlotDisplay.defaults())));
        assertEquals(EquipmentSlotDisplay.defaults(), EquipmentStructureUiRegistry.get(HOST).slot(SLOT));
        EquipmentStructureUiRegistry.replaceResourceSlots(Map.of());
        assertEquals(authored, EquipmentStructureUiRegistry.get(HOST).slot(SLOT));
    }

    private static EquipmentSlotDisplay parse(String json) {
        return EquipmentSlotDisplay.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test void typeDefaultsFollowActualSlotTypesAcrossHostsWithoutAddingSlotsOrBroadeningCompatibility() {
        var type = id("connector");
        var shared = parse("{\"name\":\"type.connector\",\"empty_icon\":\"example:slot/connector\"}");
        EquipmentStructureUiRegistry.registerInterface(type, shared);
        var slots = List.of(EquipmentSlotDefinition.of(SLOT, type),
                EquipmentSlotDefinition.of(id("second"), type),
                EquipmentSlotDefinition.of(id("unrelated"), id("other_type")));
        var first = EquipmentStructureUiRegistry.get(HOST, slots);
        var second = EquipmentStructureUiRegistry.get(id("another_host"), slots);
        assertEquals(shared, first.slot(SLOT));
        assertEquals(shared, second.slot(id("second")));
        assertEquals(EquipmentSlotDisplay.defaults(), first.slot(id("unrelated")));
        assertEquals(java.util.Set.of(SLOT, id("second")), first.slots().keySet());
        assertTrue(EquipmentStructureUiRegistry.get(HOST).slots().isEmpty());
        assertFalse(slots.getFirst().accepts(HOST,
                new dev.equipmentstructure.api.EquipmentComponentInstance(id("part"), id("other_type"))));
        var template = new dev.equipmentstructure.api.EquipmentHostDefinition(HOST, HOST, slots);
        assertEquals(first, EquipmentStructureUiRegistry.definitions(Map.of(HOST, template)).get(HOST));
    }

    @Test void specificHostWinsOverTypeResourcesAndEmptyHostOverrideSuppressesTypeDefault() {
        var type = id("connector");
        var slots = List.of(EquipmentSlotDefinition.of(SLOT, type));
        var shared = parse("{\"name\":\"java.type\"}");
        var resource = parse("{\"name\":\"resource.type\"}");
        var specific = parse("{\"name\":\"java.host\"}");
        EquipmentStructureUiRegistry.registerInterface(type, shared);
        EquipmentStructureUiRegistry.replaceResourceDisplays(Map.of(), Map.of(type, resource));
        assertEquals(resource, EquipmentStructureUiRegistry.get(HOST, slots).slot(SLOT));
        EquipmentStructureUiRegistry.register(HOST, EquipmentStructureUiDefinition.builder().slot(SLOT, specific).build());
        assertEquals(specific, EquipmentStructureUiRegistry.get(HOST, slots).slot(SLOT));
        EquipmentStructureUiRegistry.replaceResourceDisplays(Map.of(HOST, Map.of(SLOT, EquipmentSlotDisplay.defaults())), Map.of(type, resource));
        assertEquals(EquipmentSlotDisplay.defaults(), EquipmentStructureUiRegistry.get(HOST, slots).slot(SLOT));
        EquipmentStructureUiRegistry.replaceResourceDisplays(Map.of(), Map.of());
        assertEquals(specific, EquipmentStructureUiRegistry.get(HOST, slots).slot(SLOT));
        EquipmentStructureUiRegistry.unregister(HOST);
        assertEquals(shared, EquipmentStructureUiRegistry.get(HOST, slots).slot(SLOT));
        EquipmentStructureUiRegistry.unregisterInterface(type);
        assertEquals(EquipmentSlotDisplay.defaults(), EquipmentStructureUiRegistry.get(HOST, slots).slot(SLOT));
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("example", path); }
}
