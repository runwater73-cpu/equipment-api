package dev.equipmentstructure.api;

import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentStructureModelTest {

    private static final ResourceLocation HOST = id("test_host");
    private static final ResourceLocation BLADE_SLOT = id("blade");
    private static final ResourceLocation HANDLE_SLOT = id("handle");
    private static final ResourceLocation BLADE_TYPE = id("blade_type");
    private static final ResourceLocation HANDLE_TYPE = id("handle_type");

    @AfterEach
    void clearComponents() {
        EquipmentComponentRegistry.clear();
    }

    @Test
    void createsEmptyStructureFromDefinition() {
        EquipmentStructure structure = definition().createStructure();

        assertEquals(HOST, structure.hostId());
        assertEquals(2, structure.slots().size());
        assertEquals(0, structure.componentCount(BLADE_SLOT));
        assertTrue(structure.components().isEmpty());
    }

    @Test
    void acceptsCompatibleComponentsInIndependentSlots() {
        EquipmentComponentInstance blade = new EquipmentComponentInstance(
                id("iron_blade"), BLADE_TYPE, new CompoundTag()
        );

        EquipmentStructure structure = definition().createStructure()
                .withComponents(BLADE_SLOT, List.of(blade))
                .withComponents(HANDLE_SLOT, List.of(
                        new EquipmentComponentInstance(id("iron_handle"), HANDLE_TYPE)
                ));

        assertEquals(List.of(blade), structure.components(BLADE_SLOT));
        assertEquals(1, structure.componentCount(HANDLE_SLOT));
    }

    @Test
    void rejectsIncompatibleComponents() {
        EquipmentComponentInstance handle = new EquipmentComponentInstance(
                id("iron_handle"), HANDLE_TYPE
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> definition().createStructure().withComponents(BLADE_SLOT, List.of(handle))
        );
    }

    @Test
    void rejectsComponentsBeyondSlotCapacity() {
        EquipmentComponentInstance first = new EquipmentComponentInstance(id("first"), BLADE_TYPE);
        EquipmentComponentInstance second = new EquipmentComponentInstance(id("second"), BLADE_TYPE);

        assertThrows(
                IllegalArgumentException.class,
                () -> definition().createStructure().withComponents(
                        BLADE_SLOT, List.of(first, second)
                )
        );
    }

    @Test
    void codecRoundTripPreservesStructure() {
        EquipmentComponentInstance blade = new EquipmentComponentInstance(
                id("iron_blade"), BLADE_TYPE
        );
        EquipmentStructure original = definition().createStructure()
                .withComponents(BLADE_SLOT, List.of(blade));

        var encoded = EquipmentStructure.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        EquipmentStructure decoded = EquipmentStructure.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(original, decoded);
    }

    @Test
    void definitionRejectsDuplicateSlots() {
        EquipmentSlotDefinition slot = slot(BLADE_SLOT, BLADE_TYPE);

        assertThrows(
                IllegalArgumentException.class,
                () -> new EquipmentHostDefinition(HOST, HOST, List.of(slot, slot))
        );
    }

    @Test
    void acceptsRegisteredSubtype() {
        ResourceLocation specializedType = id("long_blade_type");
        EquipmentComponentTypeRegistry.registerSubtype(specializedType, BLADE_TYPE);
        EquipmentComponentInstance blade = new EquipmentComponentInstance(
                id("long_blade"), specializedType
        );

        register(blade.id(), specializedType, specializedType);
        assertTrue(slot(BLADE_SLOT, BLADE_TYPE).accepts(HOST, blade));
    }

    @Test
    void supportsDynamicSlotsWithoutChangingHostIdentity() {
        ResourceLocation coreSlot = id("core");
        EquipmentSlotDefinition slot = EquipmentSlotDefinition.of(coreSlot, id("core_type"));

        EquipmentStructure extended = definition().createStructure().addSlot(slot);

        assertEquals(HOST, extended.hostId());
        assertEquals(1, extended.version());
        assertTrue(extended.slot(coreSlot).isPresent());
        assertThrows(IllegalArgumentException.class, () -> extended.addSlot(slot));
    }

    @Test
    void checksExplicitInterfaceTypeSeparatelyFromComponentType() {
        ResourceLocation socket = id("socket");
        EquipmentSlotDefinition socketSlot = new EquipmentSlotDefinition(
                id("socket_slot"), socket, BLADE_TYPE
        );
        EquipmentComponentInstance legacyBlade = new EquipmentComponentInstance(
                id("legacy_blade"), BLADE_TYPE
        );
        EquipmentComponentInstance socketBlade = new EquipmentComponentInstance(
                id("socket_blade"), BLADE_TYPE, socket
        );

        register(legacyBlade.id(), BLADE_TYPE, BLADE_TYPE);
        register(socketBlade.id(), BLADE_TYPE, socket);
        assertFalse(socketSlot.accepts(HOST, legacyBlade));
        assertTrue(socketSlot.accepts(HOST, socketBlade));
    }

    @Test
    void migratesStructureForwardThroughRegisteredSteps() {
        EquipmentStructureMigrations.clear();
        EquipmentStructureMigrations.register(HOST, 1, 2, structure -> structure.addSlot(
                EquipmentSlotDefinition.of(id("core"), id("core_type"))
        ).withVersion(2));

        EquipmentStructure migrated = EquipmentStructureMigrations.migrate(
                definition().createStructure(), 2
        );

        assertEquals(2, migrated.version());
        assertTrue(migrated.slot(id("core")).isPresent());
        EquipmentStructureMigrations.clear();
    }

    @Test
    void rejectsMissingMigrationStep() {
        EquipmentStructureMigrations.clear();

        assertThrows(
                IllegalStateException.class,
                () -> EquipmentStructureMigrations.migrate(definition().createStructure(), 2)
        );
    }

    @Test
    void rejectsMigrationThatSkipsAVersion() {
        EquipmentStructureMigrations.clear();
        assertThrows(
                IllegalArgumentException.class,
                () -> EquipmentStructureMigrations.register(HOST, 1, 3, structure -> structure.withVersion(3))
        );
        EquipmentStructureMigrations.clear();
    }

    @Test
    void rejectsCyclicTypeRelationship() {
        ResourceLocation first = id("cycle_first");
        ResourceLocation second = id("cycle_second");
        EquipmentComponentTypeRegistry.registerSubtype(first, second);

        assertThrows(
                IllegalArgumentException.class,
                () -> EquipmentComponentTypeRegistry.registerSubtype(second, first)
        );
    }

    @Test
    void componentDataIsDefensivelyCopied() {
        CompoundTag source = new CompoundTag();
        source.putInt("quality", 4);
        EquipmentComponentInstance component = new EquipmentComponentInstance(
                id("tempered_blade"), BLADE_TYPE, source
        );

        source.putInt("quality", 1);
        CompoundTag returned = component.data();
        returned.putInt("quality", 0);

        assertEquals(4, component.data().getInt("quality"));
    }

    @Test
    void builderProvidesAConciseDefinitionEntryPoint() {
        EquipmentHostDefinition built = EquipmentHostDefinition.builder(HOST, HOST)
                .slot(EquipmentSlotDefinition.of(BLADE_SLOT, BLADE_TYPE))
                .slot(EquipmentSlotDefinition.of(HANDLE_SLOT, HANDLE_TYPE))
                .version(2)
                .build();

        assertEquals(HOST, built.id());
        assertEquals(2, built.slots().size());
        assertEquals(2, built.version());
    }

    private static EquipmentHostDefinition definition() {
        return new EquipmentHostDefinition(
                HOST,
                HOST,
                List.of(
                        slot(BLADE_SLOT, BLADE_TYPE),
                        slot(HANDLE_SLOT, HANDLE_TYPE)
                )
        );
    }

    private static EquipmentSlotDefinition slot(
            ResourceLocation slotId,
            ResourceLocation componentType
    ) {
        return new EquipmentSlotDefinition(
                slotId,
                componentType,
                componentType
        );
    }

    private static void register(ResourceLocation id, ResourceLocation componentType,
                                 ResourceLocation interfaceType) {
        EquipmentComponentRegistry.register(EquipmentComponentDefinition
                .builder(id, componentType, interfaceType)
                .suitableFor(HOST)
                .footprint(dev.equipmentstructure.api.grid.GridFootprint.SINGLE_CELL)
                .build());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("equipment_structure_api_test", path);
    }

}
