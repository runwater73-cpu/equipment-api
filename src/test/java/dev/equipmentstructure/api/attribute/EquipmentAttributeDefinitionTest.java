package dev.equipmentstructure.api.attribute;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentAttributeDefinitionTest {
    private static final String ENTRY = """
            {"attribute":"minecraft:generic.armor","id":"example:protection","amount":2.0,
             "operation":"add_value","equipment_slot":"armor"}
            """;

    @Test
    void minimalDefinitionAndEmptyOverrideDecode() {
        var definition = decode("{\"modifiers\":[" + ENTRY + "]}");
        assertEquals(2, definition.modifiers().getFirst().amount());
        assertEquals(EquipmentAttributeStacking.STACK, definition.modifiers().getFirst().stacking());
        assertTrue(decode("{\"modifiers\":[]}").modifiers().isEmpty());
    }

    @Test
    void everyStaticOptionSurvivesRoundTrip() {
        String entry = ENTRY.replace("\"armor\"", "\"body\"").replace("2.0", "-0.25")
                .replace("add_value", "add_multiplied_total").replace("}", """
                        ,"priority":12,"stacking":"replace","stacking_key":"example:mode",
                        "host_id":"example:host","slot_id":"example:core","interface_type":"example:socket"}
                        """);
        var original = decode("{\"modifiers\":[" + entry + "]}");
        var encoded = EquipmentAttributeDefinition.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        assertEquals(original, EquipmentAttributeDefinition.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"operation", "equipment_slot"})
    void unknownVanillaEnumsAreRejected(String field) {
        var json = JsonParser.parseString(ENTRY).getAsJsonObject();
        json.addProperty(field, "typo");
        assertTrue(EquipmentAttributeDefinition.Modifier.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    void unknownStackingAndMissingRequiredSlotAreRejected() {
        assertInvalid("{\"modifiers\":[" + ENTRY.replace("}", ",\"stacking\":\"typo\"}") + "]}");
        assertInvalid("{\"modifiers\":[" + ENTRY.replace(",\"equipment_slot\":\"armor\"", "") + "]}");
        assertInvalid("{}");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void nonFiniteAmountsAreRejected(double amount) {
        var json = JsonParser.parseString(ENTRY).getAsJsonObject();
        json.addProperty("amount", amount);
        assertTrue(EquipmentAttributeDefinition.Modifier.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    void duplicateKeysAreRejectedEvenWhenTheirEquipmentGroupsDiffer() {
        assertInvalid("{\"modifiers\":[" + ENTRY + "," + ENTRY.replace("\"equipment_slot\":\"armor\"", "\"equipment_slot\":\"mainhand\"") + "]}");
    }

    @Test
    void contributionCountIsBoundedAndInputsAreCopied() {
        var modifier = decode("{\"modifiers\":[" + ENTRY + "]}").modifiers().getFirst();
        var input = new ArrayList<>(Collections.singletonList(modifier));
        var value = new EquipmentAttributeDefinition(input);
        input.clear();
        assertEquals(1, value.modifiers().size());
        assertThrows(UnsupportedOperationException.class, () -> value.modifiers().clear());
        var oversized = new EquipmentAttributeDefinition(Collections.nCopies(257, modifier));
        assertTrue(EquipmentAttributeDefinition.CODEC.encodeStart(JsonOps.INSTANCE, oversized).error().isPresent());
    }

    private static EquipmentAttributeDefinition decode(String json) {
        return EquipmentAttributeDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static void assertInvalid(String json) {
        assertTrue(EquipmentAttributeDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent());
    }
}
