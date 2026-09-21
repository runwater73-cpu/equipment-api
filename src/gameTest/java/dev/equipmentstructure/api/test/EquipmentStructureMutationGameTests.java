package dev.equipmentstructure.api.test;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentComponentRegistry;
import dev.equipmentstructure.api.BuiltinEquipmentTypes;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.EquipmentStructureMigrations;
import dev.equipmentstructure.api.event.EquipmentStructureChangedEvent;
import dev.equipmentstructure.api.event.EquipmentStructureExtensionEvent;
import dev.equipmentstructure.api.event.EquipmentStructureInstallEvent;
import dev.equipmentstructure.api.event.EquipmentStructureMigrationEvent;
import dev.equipmentstructure.api.event.EquipmentStructureRemoveEvent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** Live Data Component/event contracts. Never included in the ordinary API artifact. */
@GameTestHolder(EquipmentStructureApiMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class EquipmentStructureMutationGameTests {
    private static final ResourceLocation HOST = id("mutation_host");
    private static final ResourceLocation SLOT = id("mutation_primary");
    private static final ResourceLocation TYPE = id("mutation_type");

    private EquipmentStructureMutationGameTests() {}

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(EquipmentStructureMutationGameTests.class);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void repeatedInitializationPreservesSameHostAndForeignHost(GameTestHelper helper) {
        ItemStack equipment = new ItemStack(Items.IRON_SWORD);
        var registries = helper.getLevel().registryAccess();
        ResourceLocation datapackHost = id("datapack_host");
        helper.assertTrue(EquipmentStructureApi.initialize(equipment, registries, datapackHost), "first init");
        var saved = current(equipment).addSlot(slot("dynamic")).withComponents(id("dynamic"), List.of(part("saved")));
        EquipmentStructureApi.setStructure(equipment, saved);
        helper.assertTrue(!EquipmentStructureApi.initialize(equipment, registries, datapackHost), "repeat must decline");
        helper.assertTrue(current(equipment) == saved, "same-host initialization must preserve snapshot");
        var foreign = new EquipmentStructure(HOST, saved.equipmentType(), saved.slots(), saved.components(), 7);
        EquipmentStructureApi.setStructure(equipment, foreign);
        helper.assertTrue(!EquipmentStructureApi.initialize(equipment, registries, datapackHost), "foreign-host init must decline");
        helper.assertTrue(current(equipment) == foreign, "foreign structure must survive");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void emptyAndUnknownTemplateInitializationDoNotWrite(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        helper.assertTrue(!EquipmentStructureApi.initialize(ItemStack.EMPTY, registries, id("datapack_host")), "empty init");
        helper.assertTrue(!EquipmentStructureApi.initializeFromProvider(ItemStack.EMPTY, registries), "empty provider init");
        ItemStack equipment = new ItemStack(Items.IRON_SWORD);
        helper.assertTrue(!EquipmentStructureApi.initialize(equipment, registries, id("missing_mutation_host")), "unknown init");
        helper.assertTrue(!EquipmentStructureApi.hasStructure(equipment), "unknown template must leave item alone");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void batchExtensionPreservesPartsAndSchemaVersion(GameTestHelper helper) {
        ItemStack equipment = equipment(true);
        var original = current(equipment);
        helper.assertTrue(EquipmentStructureApi.extend(equipment, value -> value.addSlot(slot("a")).addSlot(slot("b")))
                == EquipmentStructureApi.ExtensionResult.EXTENDED, "batch should extend");
        helper.assertTrue(current(equipment).components().equals(original.components()), "parts retained");
        helper.assertTrue(current(equipment).version() == 1, "dynamic slots must keep schema version");
        helper.assertTrue(EquipmentStructureApi.addSlot(equipment, slot("c")) == EquipmentStructureApi.SlotMutationResult.ADDED,
                "single append");
        helper.assertTrue(current(equipment).version() == 1 && current(equipment).slots().size() == 4, "schema/slot count");
        var unchanged = current(equipment);
        helper.assertTrue(EquipmentStructureApi.extend(equipment, value -> value) == EquipmentStructureApi.ExtensionResult.UNCHANGED,
                "identity transform");
        helper.assertTrue(EquipmentStructureApi.addSlot(equipment, slot("c")) == EquipmentStructureApi.SlotMutationResult.ALREADY_EXISTS,
                "duplicate rejected");
        helper.assertTrue(current(equipment) == unchanged, "no-op should retain snapshot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void unsafeExtensionsAreRejectedBeforeEvents(GameTestHelper helper) {
        ItemStack equipment = equipment(true);
        var original = current(equipment);
        AtomicInteger events = new AtomicInteger();
        Consumer<EquipmentStructureExtensionEvent> listener = event -> {
            if (event.stack() == equipment) events.incrementAndGet();
        };
        NeoForge.EVENT_BUS.addListener(EquipmentStructureExtensionEvent.class, listener);
        try {
            List<UnaryOperator<EquipmentStructure>> invalid = List.of(
                    value -> new EquipmentStructure(id("other_host"), value.equipmentType(), value.slots(), value.components(), 1),
                    value -> new EquipmentStructure(HOST, BuiltinEquipmentTypes.AXE, value.slots(), value.components(), 1),
                    value -> value.withVersion(2),
                    value -> new EquipmentStructure(HOST, value.equipmentType(), List.of(), Map.of(), 1),
                    value -> new EquipmentStructure(HOST, value.equipmentType(), List.of(slot("first"), value.slots().getFirst()), value.components(), 1),
                    value -> value.withComponents(SLOT, List.of()),
                    value -> value.withComponents(SLOT, List.of(part("replacement"))),
                    value -> value.addSlot(slot("filled_extra")).withComponents(id("filled_extra"), List.of(part("extra"))));
            for (var transform : invalid) {
                expectIllegal(helper, () -> EquipmentStructureApi.extend(equipment, transform));
                helper.assertTrue(current(equipment) == original, "invalid extension must not write");
            }
            helper.assertTrue(events.get() == 0, "invalid proposals should not reach events");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void installDoesNotOverwriteAnEventReplacement(GameTestHelper helper) {
        ResourceLocation outer = id("outer");
        EquipmentComponentRegistry.register(GameTestFixtures.component(outer, TYPE, TYPE));
        ItemStack equipment = equipment(false);
        var replacement = current(equipment).withComponents(SLOT, List.of(part("event_part")));
        Consumer<EquipmentStructureInstallEvent> listener = event -> {
            if (event.stack() == equipment) EquipmentStructureApi.setStructure(equipment, replacement);
        };
        NeoForge.EVENT_BUS.addListener(EquipmentStructureInstallEvent.class, listener);
        try {
            helper.assertTrue(EquipmentStructureApi.install(equipment, SLOT, part("outer")) == EquipmentStructureApi.InstallResult.CONFLICT,
                    "stale install must report conflict");
            helper.assertTrue(current(equipment) == replacement, "event replacement retained");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            EquipmentComponentRegistry.unregister(outer);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void removeDoesNotReturnPartFromAReplacedSnapshot(GameTestHelper helper) {
        ItemStack equipment = equipment(true);
        var replacement = current(equipment).withComponents(SLOT, List.of(part("event_part")));
        Consumer<EquipmentStructureRemoveEvent> listener = event -> {
            if (event.stack() == equipment) EquipmentStructureApi.setStructure(equipment, replacement);
        };
        NeoForge.EVENT_BUS.addListener(EquipmentStructureRemoveEvent.class, listener);
        try {
            helper.assertTrue(EquipmentStructureApi.remove(equipment, SLOT).isEmpty(), "stale removal must not return part");
            helper.assertTrue(current(equipment) == replacement, "new part must remain installed");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bothExtensionEntrypointsDetectReplacedSnapshots(GameTestHelper helper) {
        ItemStack equipment = equipment(true);
        var replacement = current(equipment).addSlot(slot("event_slot"));
        Consumer<EquipmentStructureExtensionEvent> listener = event -> {
            if (event.stack() == equipment) EquipmentStructureApi.setStructure(equipment,
                    new EquipmentStructure(replacement.hostId(), replacement.equipmentType(),
                            replacement.slots(), replacement.components(), replacement.version()));
        };
        NeoForge.EVENT_BUS.addListener(EquipmentStructureExtensionEvent.class, listener);
        try {
            helper.assertTrue(EquipmentStructureApi.addSlot(equipment, slot("outer")) == EquipmentStructureApi.SlotMutationResult.CONFLICT,
                    "single append conflict");
            helper.assertTrue(EquipmentStructureApi.extend(equipment, value -> value.addSlot(slot("outer")))
                    == EquipmentStructureApi.ExtensionResult.CONFLICT, "batch conflict, including equal replacement");
            helper.assertTrue(current(equipment).equals(replacement), "listener's slot retained");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void impureTransformerDoesNotOverwriteItsNewerSnapshot(GameTestHelper helper) {
        ItemStack equipment = equipment(true);
        var replacement = current(equipment).addSlot(slot("captured_write"));
        helper.assertTrue(EquipmentStructureApi.extend(equipment, value -> {
            EquipmentStructureApi.setStructure(equipment, replacement);
            return value;
        }) == EquipmentStructureApi.ExtensionResult.CONFLICT, "even an identity result must detect conflict");
        helper.assertTrue(current(equipment) == replacement, "new snapshot retained");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void failedMigrationChainNeverWritesPartialResult(GameTestHelper helper) {
        ItemStack equipment = equipment(true);
        var original = current(equipment);
        AtomicInteger calls = new AtomicInteger();
        try (var first = EquipmentStructureMigrations.register(HOST, 1, 2, value -> {
            calls.incrementAndGet();
            return value.addSlot(slot("v2")).withVersion(2);
        })) {
            try {
                EquipmentStructureApi.migrate(equipment, 3);
                helper.fail("missing second step should fail");
            } catch (IllegalStateException expected) {
                helper.assertTrue(calls.get() == 0, "missing chain detected before callbacks");
            }
            try (var second = EquipmentStructureMigrations.register(HOST, 2, 3, value -> value.withVersion(4))) {
                expectIllegal(helper, () -> EquipmentStructureApi.migrate(equipment, 3));
                helper.assertTrue(calls.get() == 1, "first step ran only for complete chain");
                helper.assertTrue(current(equipment) == original, "invalid second result must not write v2");
            }
            try (var second = EquipmentStructureMigrations.register(HOST, 2, 3, value -> {
                throw new IllegalArgumentException("author migration failed");
            })) {
                expectIllegal(helper, () -> EquipmentStructureApi.migrate(equipment, 3));
                helper.assertTrue(current(equipment) == original, "throwing migration must not write v2");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void migrationCancellationAndSuccessFireOneEventForWholeChain(GameTestHelper helper) {
        ItemStack equipment = equipment(true);
        var original = current(equipment).addSlot(slot("dynamic_before_migration"));
        EquipmentStructureApi.setStructure(equipment, original);
        AtomicInteger beforeCount = new AtomicInteger();
        AtomicInteger changedCount = new AtomicInteger();
        Consumer<EquipmentStructureMigrationEvent> before = event -> {
            if (event.stack() != equipment) return;
            helper.assertTrue(event.previous() == original && event.proposed().version() == 3, "whole chain proposal");
            event.setCanceled(beforeCount.incrementAndGet() == 1);
        };
        Consumer<EquipmentStructureChangedEvent> after = event -> {
            if (event.stack() != equipment) return;
            helper.assertTrue(event.changeType() == EquipmentStructureChangedEvent.ChangeType.MIGRATED, "migration event type");
            changedCount.incrementAndGet();
        };
        NeoForge.EVENT_BUS.addListener(EquipmentStructureMigrationEvent.class, before);
        NeoForge.EVENT_BUS.addListener(EquipmentStructureChangedEvent.class, after);
        try (var first = EquipmentStructureMigrations.register(HOST, 1, 2, value -> value.withVersion(2));
             var second = EquipmentStructureMigrations.register(HOST, 2, 3, value -> value.withVersion(3))) {
            helper.assertTrue(EquipmentStructureApi.migrate(equipment, 3) == EquipmentStructureApi.MigrationResult.CANCELED, "cancel result");
            helper.assertTrue(current(equipment) == original && changedCount.get() == 0, "canceled chain must not write");
            helper.assertTrue(EquipmentStructureApi.migrate(equipment, 3) == EquipmentStructureApi.MigrationResult.MIGRATED, "success result");
            helper.assertTrue(beforeCount.get() == 2 && changedCount.get() == 1, "once per chain");
            helper.assertTrue(current(equipment).components().equals(original.components()), "parts retained");
            helper.assertTrue(current(equipment).slots().equals(original.slots()), "dynamic slots retained");
            helper.assertTrue(EquipmentStructureApi.migrate(equipment, 3) == EquipmentStructureApi.MigrationResult.UNCHANGED, "same version no-op");
            helper.assertTrue(beforeCount.get() == 2 && changedCount.get() == 1, "no-op must not fire events");
            ItemStack restored = ItemStack.parse(helper.getLevel().registryAccess(),
                    equipment.save(helper.getLevel().registryAccess())).orElseThrow();
            helper.assertTrue(current(restored).equals(current(equipment)), "saved migrated structure should round trip");
        } finally {
            NeoForge.EVENT_BUS.unregister(before);
            NeoForge.EVENT_BUS.unregister(after);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void migrationCallbackConflictDoesNotReachCommitEvent(GameTestHelper helper) {
        ItemStack equipment = equipment(true);
        var replacement = current(equipment).addSlot(slot("external"));
        AtomicInteger events = new AtomicInteger();
        Consumer<EquipmentStructureMigrationEvent> listener = event -> {
            if (event.stack() == equipment) events.incrementAndGet();
        };
        NeoForge.EVENT_BUS.addListener(EquipmentStructureMigrationEvent.class, listener);
        try (var first = EquipmentStructureMigrations.register(HOST, 1, 2, value -> {
            EquipmentStructureApi.setStructure(equipment, replacement);
            return value.withVersion(2);
        })) {
            helper.assertTrue(EquipmentStructureApi.migrate(equipment, 2) == EquipmentStructureApi.MigrationResult.CONFLICT, "callback conflict");
            helper.assertTrue(current(equipment) == replacement && events.get() == 0, "no stale proposal event or commit");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void migrationEventCannotBeOverwrittenByOuterCommit(GameTestHelper helper) {
        ItemStack equipment = equipment(true);
        Consumer<EquipmentStructureMigrationEvent> listener = event -> {
            if (event.stack() == equipment) EquipmentStructureApi.clearStructure(equipment);
        };
        NeoForge.EVENT_BUS.addListener(EquipmentStructureMigrationEvent.class, listener);
        try (var first = EquipmentStructureMigrations.register(HOST, 1, 2, value -> value.withVersion(2))) {
            helper.assertTrue(EquipmentStructureApi.migrate(equipment, 2) == EquipmentStructureApi.MigrationResult.CONFLICT, "event conflict");
            helper.assertTrue(!EquipmentStructureApi.hasStructure(equipment), "outer commit must not resurrect cleared data");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    private static void expectIllegal(GameTestHelper helper, Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        helper.fail("expected IllegalArgumentException");
    }

    private static ItemStack equipment(boolean installed) {
        ItemStack equipment = new ItemStack(Items.IRON_SWORD);
        var structure = new EquipmentStructure(HOST, BuiltinEquipmentTypes.SWORD,
                List.of(EquipmentSlotDefinition.of(SLOT, TYPE)), Map.of());
        if (installed) structure = structure.withComponents(SLOT, List.of(part("saved")));
        EquipmentStructureApi.setStructure(equipment, structure);
        return equipment;
    }

    private static EquipmentStructure current(ItemStack equipment) {
        return EquipmentStructureApi.structure(equipment).orElseThrow();
    }

    private static EquipmentComponentInstance part(String path) {
        return new EquipmentComponentInstance(id(path), TYPE);
    }

    private static EquipmentSlotDefinition slot(String path) {
        return EquipmentSlotDefinition.of(id(path), TYPE);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, path);
    }
}
