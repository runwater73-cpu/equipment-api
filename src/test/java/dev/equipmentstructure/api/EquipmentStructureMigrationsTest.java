package dev.equipmentstructure.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentStructureMigrationsTest {
    private static final ResourceLocation HOST = id("host");
    private static final ResourceLocation TYPE = id("type");
    private static final ResourceLocation SLOT = id("primary");

    @BeforeEach
    @AfterEach
    void reset() {
        EquipmentStructureMigrations.clear();
    }

    @Test
    void dynamicSlotsDoNotSkipSchemaMigrationsAndPreserveComponentData() {
        CompoundTag data = new CompoundTag();
        data.putInt("quality", 42);
        var part = new EquipmentComponentInstance(id("part"), TYPE, data);
        var original = structure().withComponents(SLOT, List.of(part))
                .addSlot(slot("dynamic_a")).addSlot(slot("dynamic_b"));
        assertEquals(1, original.version());
        EquipmentStructureMigrations.register(HOST, 1, 2,
                value -> value.addSlot(slot("schema_v2")).withVersion(2));
        EquipmentStructureMigrations.register(HOST, 2, 3, value -> value.withVersion(3));
        var result = EquipmentStructureMigrations.migrate(original, 3);
        assertEquals(3, result.version());
        assertEquals(4, result.slots().size());
        assertEquals(original.components(), result.components());
        assertEquals(42, result.component(SLOT).orElseThrow().data().getInt("quality"));
        assertEquals(3, original.slots().size());
    }

    @Test
    void missingLaterStepIsRejectedBeforeAnyCallbackRuns() {
        AtomicInteger calls = new AtomicInteger();
        EquipmentStructureMigrations.register(HOST, 1, 2, value -> {
            calls.incrementAndGet();
            return value.withVersion(2);
        });
        assertThrows(IllegalStateException.class, () -> EquipmentStructureMigrations.migrate(structure(), 3));
        assertEquals(0, calls.get());
    }

    @Test
    void capturesWholeChainBeforeAuthorCallbacksCanReplaceRegistrations() {
        EquipmentStructureMigrations.register(HOST, 1, 2, value -> {
            EquipmentStructureMigrations.clear();
            EquipmentStructureMigrations.register(HOST, 2, 3,
                    later -> later.addSlot(slot("replacement")).withVersion(3));
            return value.withVersion(2);
        });
        EquipmentStructureMigrations.register(HOST, 2, 3,
                value -> value.addSlot(slot("captured")).withVersion(3));
        var result = EquipmentStructureMigrations.migrate(structure(), 3);
        assertTrue(result.slot(id("captured")).isPresent());
        assertTrue(result.slot(id("replacement")).isEmpty());
    }

    @Test
    void callbacksRunOutsideRegistryLock() {
        EquipmentStructureMigrations.register(HOST, 1, 2, value -> {
            assertDoesNotThrow(() -> CompletableFuture.runAsync(() ->
                    EquipmentStructureMigrations.register(HOST, 2, 3, later -> later.withVersion(3)))
                    .get(3, TimeUnit.SECONDS));
            return value.withVersion(2);
        });
        assertEquals(2, EquipmentStructureMigrations.migrate(structure(), 2).version());
    }

    @Test
    void registrationRejectsInvalidVersionsIncludingOverflow() {
        for (int[] versions : new int[][]{{0, 1}, {1, 1}, {2, 1}, {1, 3}, {Integer.MAX_VALUE, Integer.MIN_VALUE}}) {
            assertThrows(IllegalArgumentException.class, () -> EquipmentStructureMigrations.register(
                    HOST, versions[0], versions[1], value -> value));
        }
    }

    @Test
    void resultMustMatchRegisteredVersion() {
        for (int target : new int[]{1, 3}) {
            try (var ignored = EquipmentStructureMigrations.register(HOST, 1, 2,
                    value -> value.withVersion(target))) {
                assertThrows(IllegalArgumentException.class,
                        () -> EquipmentStructureMigrations.migrate(structure(), 2));
            }
        }
    }

    @Test
    void rejectsDifferentHostAndNullResult() {
        try (var ignored = EquipmentStructureMigrations.register(HOST, 1, 2,
                value -> new EquipmentStructure(id("different"), value.equipmentType(),
                        value.slots(), value.components(), 2))) {
            assertThrows(IllegalArgumentException.class, () -> EquipmentStructureMigrations.migrate(structure(), 2));
        }
        try (var ignored = EquipmentStructureMigrations.register(HOST, 1, 2, value -> null)) {
            assertThrows(NullPointerException.class, () -> EquipmentStructureMigrations.migrate(structure(), 2));
        }
    }

    @Test
    void migrationCannotChangeEquipmentType() {
        try (var ignored = EquipmentStructureMigrations.register(HOST, 1, 2,
                value -> new EquipmentStructure(value.hostId(), id("different_type"),
                        value.slots(), value.components(), 2))) {
            assertThrows(IllegalArgumentException.class,
                    () -> EquipmentStructureMigrations.migrate(structure(), 2));
        }
    }

    @Test
    void unchangedReturnsSameSnapshotAndBackwardsIsRejected() {
        var current = structure().withVersion(3);
        assertSame(current, EquipmentStructureMigrations.migrate(current, 3));
        assertSame(current, current.withVersion(3));
        assertThrows(IllegalArgumentException.class, () -> EquipmentStructureMigrations.migrate(current, 2));
        assertThrows(IllegalArgumentException.class, () -> current.withVersion(0));
    }

    @Test
    void oldHandleCannotRemoveSameCallbackRegisteredAfterClear() {
        EquipmentStructureMigration callback = value -> value.withVersion(2);
        var old = EquipmentStructureMigrations.register(HOST, 1, 2, callback);
        assertTrue(old.isActive());
        EquipmentStructureMigrations.clear();
        var replacement = EquipmentStructureMigrations.register(HOST, 1, 2, callback);
        assertFalse(old.isActive());
        assertFalse(old.unregister());
        old.close();
        assertTrue(replacement.isActive());
        assertTrue(replacement.unregister());
        assertFalse(replacement.isActive());
        assertFalse(replacement.unregister());
    }

    @Test
    void duplicateRegistrationDoesNotReplaceOriginal() {
        try (var first = EquipmentStructureMigrations.register(HOST, 1, 2, value -> value.withVersion(2))) {
            assertThrows(IllegalArgumentException.class, () -> EquipmentStructureMigrations.register(
                    HOST, 1, 2, value -> { throw new AssertionError("replacement must not run"); }));
            assertTrue(first.isActive());
            assertEquals(2, EquipmentStructureMigrations.migrate(structure(), 2).version());
        }
    }

    @Test
    void maximumSchemaVersionCanStillAcceptDynamicInterfaces() {
        var current = structure().withVersion(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, current.addSlot(slot("extra")).version());
        try (var ignored = EquipmentStructureMigrations.register(HOST, Integer.MAX_VALUE - 1, Integer.MAX_VALUE,
                value -> value.withVersion(Integer.MAX_VALUE))) {
            assertEquals(Integer.MAX_VALUE, EquipmentStructureMigrations.migrate(
                    structure().withVersion(Integer.MAX_VALUE - 1), Integer.MAX_VALUE).version());
        }
    }

    private static EquipmentStructure structure() {
        return new EquipmentStructure(HOST, TYPE,
                List.of(EquipmentSlotDefinition.of(SLOT, TYPE)), Map.of());
    }

    private static EquipmentSlotDefinition slot(String name) {
        return EquipmentSlotDefinition.of(id(name), TYPE);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("migration_test", path);
    }
}
