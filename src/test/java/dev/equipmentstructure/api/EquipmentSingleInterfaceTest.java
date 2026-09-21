package dev.equipmentstructure.api;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import dev.equipmentstructure.api.ui.EquipmentStructureUiDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentSingleInterfaceTest {
    private static final ResourceLocation TYPE = id("part");
    private static final ResourceLocation SOCKET = id("socket");
    private static final ResourceLocation SLOT = id("primary");
    private static final ResourceLocation COMPONENT = id("component");

    @AfterEach
    void clearRegistrations() {
        EquipmentComponentRegistry.clear();
        EquipmentInterfaceRegistry.clear();
    }

    @Test
    void oneInterfaceRejectsTwoPartsAndExposesSingleQuery() {
        EquipmentStructure empty = host().createStructure();
        EquipmentComponentInstance first = part(1);
        assertTrue(empty.component(SLOT).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> empty.withComponents(SLOT, List.of(first, part(2))));
        assertEquals(first, empty.withComponents(SLOT, List.of(first)).component(SLOT).orElseThrow());
    }

    @Test
    void multipleConcreteSlotsMayShareOneInterfaceType() {
        ResourceLocation secondary = id("secondary");
        var slots = List.of(
                new EquipmentSlotDefinition(SLOT, SOCKET, TYPE),
                new EquipmentSlotDefinition(secondary, SOCKET, TYPE));
        EquipmentStructure structure = new EquipmentHostDefinition(id("dual_host"), TYPE, slots).createStructure()
                .withComponents(SLOT, List.of(part(1)))
                .withComponents(secondary, List.of(part(2)));

        assertEquals(SOCKET, structure.slot(SLOT).orElseThrow().interfaceType());
        assertEquals(SOCKET, structure.slot(secondary).orElseThrow().interfaceType());
        assertEquals(part(1), structure.component(SLOT).orElseThrow());
        assertEquals(part(2), structure.component(secondary).orElseThrow());
        assertEquals(2, structure.components().size());
    }

    @Test
    void hostBuilderOffersExplicitSlotDeclarationWithoutCapacitySemantics() {
        ResourceLocation secondary = id("secondary");
        EquipmentHostDefinition definition = EquipmentHostDefinition.builder(id("builder_host"), TYPE)
                .slot(SLOT, SOCKET)
                .slot(secondary, SOCKET, TYPE)
                .build();

        assertEquals(List.of(SLOT, secondary), definition.slots().stream()
                .map(EquipmentSlotDefinition::id).toList());
        assertEquals(SOCKET, definition.slots().getFirst().interfaceType());
        assertEquals(SOCKET, definition.slots().getFirst().componentType());
        assertEquals(TYPE, definition.slots().get(1).componentType());
    }

    @Test
    void publicSlotModelContainsOnlyIdentityAndCompatibilityTypes() {
        assertEquals(slot(), EquipmentSlotDefinition.of(SLOT, SOCKET, TYPE));
        assertEquals(3, EquipmentSlotDefinition.class.getRecordComponents().length);
        assertTrue(java.util.Arrays.stream(EquipmentSlotDefinition.class.getRecordComponents())
                .noneMatch(component -> component.getName().equals("required")
                        || component.getName().equals("capacity")));
    }

    @Test
    void storageOmitsRemovedPolicyFieldsAndRoundTrips() {
        EquipmentStructure source = host().createStructure().withComponents(SLOT, List.of(part(42)));
        var encoded = EquipmentStructure.CODEC.encodeStart(JsonOps.INSTANCE, source).getOrThrow();
        assertFalse(encoded.toString().contains("capacity"));
        assertFalse(encoded.toString().contains("required"));
        assertEquals(source, EquipmentStructure.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void malformedStoredDataReturnsCodecErrors() {
        JsonObject overflow = storedStructure();
        overflow.getAsJsonObject("components").getAsJsonArray(SLOT.toString())
                .add(EquipmentComponentInstance.CODEC.encodeStart(JsonOps.INSTANCE, part(2)).getOrThrow());
        assertTrue(EquipmentStructure.CODEC.parse(JsonOps.INSTANCE, overflow).error().isPresent());
        JsonObject duplicate = storedStructure();
        duplicate.getAsJsonArray("slots").add(duplicate.getAsJsonArray("slots").get(0).deepCopy());
        assertTrue(EquipmentStructure.CODEC.parse(JsonOps.INSTANCE, duplicate).error().isPresent());
        JsonObject unknown = storedStructure();
        unknown.getAsJsonObject("components").add(id("unknown").toString(),
                unknown.getAsJsonObject("components").remove(SLOT.toString()));
        assertTrue(EquipmentStructure.CODEC.parse(JsonOps.INSTANCE, unknown).error().isPresent());
    }

    @Test
    void registeredInterfaceCreatesIndependentConcreteSlots() {
        EquipmentInterfaceRegistry.register(new EquipmentInterfaceDefinition(SOCKET, TYPE));
        assertEquals(slot(), EquipmentSlotDefinition.fromInterface(SLOT, SOCKET));
        assertEquals(TYPE, EquipmentInterfaceRegistry.get(SOCKET).orElseThrow().defaultComponentType());
        assertThrows(IllegalArgumentException.class,
                () -> EquipmentSlotDefinition.fromInterface(SLOT, id("unknown")));
    }

    @Test
    void registrationsAreIdempotentButRejectConflictingDefinitions() {
        var interfaceDefinition = new EquipmentInterfaceDefinition(SOCKET, TYPE);
        EquipmentInterfaceRegistry.register(interfaceDefinition);
        EquipmentInterfaceRegistry.register(interfaceDefinition);
        assertThrows(IllegalStateException.class, () -> EquipmentInterfaceRegistry.register(
                new EquipmentInterfaceDefinition(SOCKET, id("different"))));
        var componentDefinition = definition(COMPONENT, TYPE, SOCKET, true);
        EquipmentComponentRegistry.register(componentDefinition);
        EquipmentComponentRegistry.register(componentDefinition);
        assertThrows(IllegalStateException.class, () -> EquipmentComponentRegistry.register(
                definition(COMPONENT, TYPE, id("different"), true)));
        assertTrue(EquipmentComponentRegistry.unregister(COMPONENT));
        assertFalse(EquipmentComponentRegistry.unregister(COMPONENT));
        assertTrue(EquipmentInterfaceRegistry.unregister(SOCKET));
    }

    @Test
    void registeredIdentityCannotBeForgedEvenWhenSlotAcceptsBothTypes() {
        ResourceLocation child = id("special_part");
        EquipmentComponentTypeRegistry.registerSubtype(child, TYPE);
        try {
            EquipmentComponentRegistry.register(definition(COMPONENT, TYPE, SOCKET, true));
            assertTrue(slot().accepts(TYPE, part(1)));
            assertFalse(slot().accepts(TYPE, new EquipmentComponentInstance(COMPONENT, child, SOCKET)));
        } finally {
            EquipmentComponentTypeRegistry.unregisterSubtype(child, TYPE);
        }
    }

    @Test
    void changedConcreteRegistrationDoesNotInvalidateSavedEquipment() {
        EquipmentStructure original = host().createStructure().withComponents(SLOT, List.of(part(1)));
        var saved = EquipmentStructure.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        EquipmentComponentRegistry.register(definition(COMPONENT, id("new_type"), SOCKET, true));
        assertFalse(slot().accepts(TYPE, part(1)));
        assertEquals(original, EquipmentStructure.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());
    }

    @Test
    void definitionFactoryPreservesDataAndDoesNotTreatRemovableAsInstallPermission() {
        var definition = definition(COMPONENT, TYPE, SOCKET, false);
        EquipmentComponentRegistry.register(definition);
        CompoundTag data = new CompoundTag();
        data.putInt("value", 17);
        EquipmentComponentInstance component = definition.createInstance(data);
        data.putInt("value", 99);
        assertEquals(17, component.data().getInt("value"));
        assertTrue(slot().accepts(TYPE, component));
    }

    @Test
    void dataModelKeepsMoreThanSixteenDistinctInterfaces() {
        var slots = IntStream.range(0, 40).mapToObj(index ->
                new EquipmentSlotDefinition(id("slot_" + index), SOCKET, TYPE)).toList();
        EquipmentStructure structure = new EquipmentHostDefinition(id("large_host"), TYPE, slots).createStructure();
        for (var slot : slots) {
            structure = structure.withComponents(slot.id(), List.of(part(1)));
        }
        assertEquals(40, structure.components().size());
        var encoded = EquipmentStructure.CODEC.encodeStart(JsonOps.INSTANCE, structure).getOrThrow();
        assertEquals(structure, EquipmentStructure.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void nbtRoundTripPreservesExactNumericAndArrayTypes() {
        CompoundTag data = new CompoundTag();
        data.putInt("integer", 1);
        data.putLong("long", 1L);
        data.putByteArray("bytes", new byte[]{1, 2, 3});
        data.putIntArray("integers", new int[]{1, 2000});
        EquipmentComponentInstance component = new EquipmentComponentInstance(COMPONENT, TYPE, SOCKET, data);
        EquipmentStructure original = host().createStructure().withComponents(SLOT, List.of(component));
        CompoundTag encoded = (CompoundTag) EquipmentStructure.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        EquipmentStructure decoded = EquipmentStructure.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(1, decoded.slots().size());
        assertEquals(component, decoded.component(SLOT).orElseThrow());
    }

    @Test
    void missingSubtypeRegistrationDoesNotDestroyPersistedComponent() {
        ResourceLocation child = id("removed_mod_part");
        EquipmentComponentTypeRegistry.registerSubtype(child, TYPE);
        EquipmentStructure source;
        try {
            source = host().createStructure().withComponents(SLOT,
                    List.of(new EquipmentComponentInstance(COMPONENT, child, SOCKET)));
        } finally {
            EquipmentComponentTypeRegistry.unregisterSubtype(child, TYPE);
        }
        var saved = EquipmentStructure.CODEC.encodeStart(NbtOps.INSTANCE, source).getOrThrow();
        assertEquals(source, EquipmentStructure.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow());
        assertFalse(slot().accepts(TYPE, source.component(SLOT).orElseThrow()));
    }

    private static JsonObject storedStructure() {
        var source = new EquipmentStructure(id("host"), TYPE,
                List.of(slot()), Map.of(SLOT, List.of(part(1))), 7);
        return EquipmentStructure.CODEC.encodeStart(JsonOps.INSTANCE, source).getOrThrow().getAsJsonObject();
    }

    private static EquipmentHostDefinition host() {
        return new EquipmentHostDefinition(id("host"), TYPE, List.of(slot()));
    }

    private static EquipmentSlotDefinition slot() {
        return new EquipmentSlotDefinition(SLOT, SOCKET, TYPE);
    }

    private static EquipmentComponentInstance part(int value) {
        CompoundTag data = new CompoundTag();
        // JSON has no NBT integer-width information. Use strings for JSON migration
        // cases; the dedicated NbtOps test covers exact numeric/array persistence.
        data.putString("value", Integer.toString(value));
        return new EquipmentComponentInstance(COMPONENT, TYPE, SOCKET, data);
    }

    private static EquipmentComponentDefinition definition(ResourceLocation id,
            ResourceLocation componentType, ResourceLocation interfaceType, boolean removable) {
        return EquipmentComponentDefinition.builder(id, componentType, interfaceType)
                .suitableFor(TYPE)
                .footprint(dev.equipmentstructure.api.grid.GridFootprint.SINGLE_CELL)
                .removable(removable)
                .build();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("single_interface_test", path);
    }
}
