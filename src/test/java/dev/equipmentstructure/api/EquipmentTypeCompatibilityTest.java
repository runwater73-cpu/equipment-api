package dev.equipmentstructure.api;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.equipmentstructure.api.grid.GridFootprint;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentTypeCompatibilityTest {
    private static final ResourceLocation HOST = id("host");
    private static final ResourceLocation SLOT = id("blade_slot");
    private static final ResourceLocation BLADE = id("blade");
    private static final ResourceLocation HANDLE = id("handle");
    private static final ResourceLocation TANG = id("tang");
    private static final ResourceLocation OTHER_INTERFACE = id("other_interface");
    private static final ResourceLocation SWORD_PART = id("sword_part");
    private static final EquipmentSlotDefinition BLADE_SLOT =
            EquipmentSlotDefinition.of(SLOT, TANG, BLADE);

    @AfterEach
    void clearRegistrations() {
        EquipmentComponentRegistry.clear();
    }

    @Test
    void swordOnlyComponentIsAcceptedBySwordAndRejectedByAxe() {
        var definition = register(SWORD_PART, BuiltinEquipmentTypes.SWORD);

        assertTrue(BLADE_SLOT.accepts(BuiltinEquipmentTypes.SWORD, definition.createInstance()));
        assertFalse(BLADE_SLOT.accepts(BuiltinEquipmentTypes.AXE, definition.createInstance()));
    }

    @Test
    void componentCanExplicitlySupportSeveralEquipmentTypes() {
        var definition = register(SWORD_PART, BuiltinEquipmentTypes.SWORD, BuiltinEquipmentTypes.AXE);

        assertTrue(BLADE_SLOT.accepts(BuiltinEquipmentTypes.SWORD, definition.createInstance()));
        assertTrue(BLADE_SLOT.accepts(BuiltinEquipmentTypes.AXE, definition.createInstance()));
        assertFalse(BLADE_SLOT.accepts(BuiltinEquipmentTypes.PICKAXE, definition.createInstance()));
    }

    @Test
    void unregisteredAndIdentityForgedComponentsAreRejected() {
        var definition = register(SWORD_PART, BuiltinEquipmentTypes.SWORD);

        assertFalse(BLADE_SLOT.accepts(BuiltinEquipmentTypes.SWORD,
                new EquipmentComponentInstance(id("unregistered"), BLADE, TANG)));
        assertFalse(BLADE_SLOT.accepts(BuiltinEquipmentTypes.SWORD,
                new EquipmentComponentInstance(definition.id(), HANDLE, TANG)));
        assertFalse(BLADE_SLOT.accepts(BuiltinEquipmentTypes.SWORD,
                new EquipmentComponentInstance(definition.id(), BLADE, OTHER_INTERFACE)));
    }

    @Test
    void matchingEquipmentTypeStillRequiresSlotComponentAndInterfaceCompatibility() {
        var wrongComponentType = EquipmentComponentDefinition.builder(id("wrong_component"), HANDLE, TANG)
                .suitableFor(BuiltinEquipmentTypes.SWORD)
                .footprint(GridFootprint.SINGLE_CELL)
                .build();
        var wrongInterfaceType = EquipmentComponentDefinition.builder(id("wrong_interface"), BLADE, OTHER_INTERFACE)
                .suitableFor(BuiltinEquipmentTypes.SWORD)
                .footprint(GridFootprint.SINGLE_CELL)
                .build();
        EquipmentComponentRegistry.register(wrongComponentType);
        EquipmentComponentRegistry.register(wrongInterfaceType);

        assertFalse(BLADE_SLOT.accepts(BuiltinEquipmentTypes.SWORD, wrongComponentType.createInstance()));
        assertFalse(BLADE_SLOT.accepts(BuiltinEquipmentTypes.SWORD, wrongInterfaceType.createInstance()));
    }

    @Test
    void componentRegistrationRequiresEquipmentTypesAndFootprint() {
        assertThrows(IllegalStateException.class, () ->
                EquipmentComponentDefinition.builder(SWORD_PART, BLADE, TANG)
                        .footprint(GridFootprint.SINGLE_CELL)
                        .build());
        assertThrows(IllegalStateException.class, () ->
                EquipmentComponentDefinition.builder(SWORD_PART, BLADE, TANG)
                        .suitableFor(BuiltinEquipmentTypes.SWORD)
                        .build());
    }

    @Test
    void hostAndStructureCodecsPreserveRequiredEquipmentType() {
        var host = new EquipmentHostDefinition(HOST, BuiltinEquipmentTypes.SWORD, List.of(BLADE_SLOT));
        var encodedHost = EquipmentHostDefinition.CODEC.encodeStart(JsonOps.INSTANCE, host).getOrThrow();
        var encodedStructure = EquipmentStructure.CODEC.encodeStart(JsonOps.INSTANCE, host.createStructure()).getOrThrow();

        assertEquals(BuiltinEquipmentTypes.SWORD.toString(),
                encodedHost.getAsJsonObject().get("equipment_type").getAsString());
        assertEquals(host, EquipmentHostDefinition.CODEC.parse(JsonOps.INSTANCE, encodedHost).getOrThrow());
        assertEquals(BuiltinEquipmentTypes.SWORD.toString(),
                encodedStructure.getAsJsonObject().get("equipment_type").getAsString());
        assertEquals(host.createStructure(),
                EquipmentStructure.CODEC.parse(JsonOps.INSTANCE, encodedStructure).getOrThrow());

        JsonObject hostWithoutType = encodedHost.getAsJsonObject().deepCopy();
        hostWithoutType.remove("equipment_type");
        JsonObject structureWithoutType = encodedStructure.getAsJsonObject().deepCopy();
        structureWithoutType.remove("equipment_type");
        assertTrue(EquipmentHostDefinition.CODEC.parse(JsonOps.INSTANCE, hostWithoutType).error().isPresent());
        assertTrue(EquipmentStructure.CODEC.parse(JsonOps.INSTANCE, structureWithoutType).error().isPresent());
    }

    private static EquipmentComponentDefinition register(ResourceLocation componentId,
                                                         ResourceLocation... equipmentTypes) {
        var definition = EquipmentComponentDefinition.builder(componentId, BLADE, TANG)
                .suitableFor(equipmentTypes)
                .footprint(GridFootprint.SINGLE_CELL)
                .build();
        EquipmentComponentRegistry.register(definition);
        return definition;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("equipment_type_test", path);
    }
}
