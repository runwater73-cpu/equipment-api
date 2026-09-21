package dev.equipmentstructure.api.test;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.appearance.*;
import dev.equipmentstructure.api.event.*;
import dev.equipmentstructure.api.grid.*;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import dev.equipmentstructure.api.network.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Actual ItemStack components, vanilla menu ownership, events and packet codecs. No client required. */
@GameTestHolder(EquipmentStructureApiMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class EquipmentGridGameTests {
    private static final ResourceLocation HOST = id("host"), A = id("a"), B = id("b"), TYPE = id("type"),
            SMALL = id("small"), LARGE = id("large"), FOREIGN = id("foreign");
    private static final List<EquipmentSlotDefinition> SLOTS = List.of(
            EquipmentSlotDefinition.of(A, TYPE), EquipmentSlotDefinition.of(B, TYPE));
    private static Item smallItem, largeItem, foreignItem;

    @EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
    public static final class Fixtures {
        @SubscribeEvent public static void items(RegisterEvent event) {
            event.register(Registries.ITEM, helper -> {
                smallItem = new PartItem(SMALL, TYPE); helper.register(SMALL, smallItem);
                largeItem = new PartItem(LARGE, TYPE); helper.register(LARGE, largeItem);
                foreignItem = new PartItem(FOREIGN, FOREIGN); helper.register(FOREIGN, foreignItem);
            });
        }
    }

    @SubscribeEvent public static void tests(RegisterGameTestsEvent event) {
        event.register(EquipmentGridGameTests.class);
        EquipmentGridRegistry.registerHost(HOST, new GridBoard(GridShape.rectangle(4, 3),
                GridShape.rectangle(1, 1), new GridPlacement(3, 2)));
        EquipmentComponentRegistry.register(definition(SMALL, TYPE, smallItem, GridFootprint.SINGLE_CELL));
        EquipmentComponentRegistry.register(definition(LARGE, TYPE, largeItem, GridFootprint.freelyRotating(GridShape.rectangle(2, 2))));
        EquipmentComponentRegistry.register(definition(FOREIGN, FOREIGN, foreignItem, GridFootprint.SINGLE_CELL));
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void menuInstallRejectsBodyCollisionAndConsumesExactlyOne(GameTestHelper helper) {
        var t = menu(helper); t.menu.setCarried(new ItemStack(smallItem, 3));
        var original = t.menu.equipmentStack().copy();
        helper.assertTrue(!apply(t, GridActionPayload.Action.INSTALL, A, new GridPlacement(3, 2)), "body blocks input");
        helper.assertTrue(ItemStack.matches(original, t.menu.equipmentStack()) && t.menu.getCarried().getCount() == 3, "failed input preserved");
        var request = request(t, GridActionPayload.Action.INSTALL, A, Map.of(A, new GridPlacement(0, 0)));
        helper.assertTrue(t.menu.applyGridAction(request, t.player), "install accepted");
        helper.assertTrue(t.menu.getCarried().getCount() == 2 && current(t).component(A).isPresent(), "one item consumed");
        helper.assertTrue(current(t).grid().orElseThrow().placements().get(A).equals(new GridPlacement(0, 0)), "position saved with component");
        helper.assertTrue(!t.menu.applyGridAction(request, t.player) && t.menu.getCarried().getCount() == 2, "replayed request rejected");
        helper.assertTrue(!apply(t, GridActionPayload.Action.INSTALL, B, new GridPlacement(0, 0)), "another part blocks input");
        t.menu.setCarried(new ItemStack(foreignItem));
        helper.assertTrue(!apply(t, GridActionPayload.Action.INSTALL, B, new GridPlacement(1, 0)), "wrong interface rejected");
        helper.assertTrue(t.menu.getCarried().is(foreignItem) && current(t).component(B).isEmpty(), "foreign item retained");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void failedReplacementKeepsBothItemsAndSuccessfulExchangeReturnsOldOnce(GameTestHelper helper) {
        var t = menu(helper);
        t.menu.setCarried(new ItemStack(smallItem, 2));
        helper.assertTrue(apply(t, GridActionPayload.Action.INSTALL, A, new GridPlacement(0, 0)), "first");
        helper.assertTrue(apply(t, GridActionPayload.Action.INSTALL, B, new GridPlacement(1, 0)), "neighbor");
        t.menu.setCarried(new ItemStack(largeItem));
        var before = t.menu.equipmentStack().copy();
        helper.assertTrue(!apply(t, GridActionPayload.Action.REPLACE, A, new GridPlacement(0, 0)), "replacement collides");
        helper.assertTrue(ItemStack.matches(before, t.menu.equipmentStack()) && t.menu.getCarried().is(largeItem), "failed swap preserves both");
        helper.assertTrue(apply(t, GridActionPayload.Action.REPLACE, A, new GridPlacement(0, 1)), "valid replacement");
        helper.assertTrue(t.menu.getCarried().is(smallItem) && t.menu.getCarried().getCount() == 1, "old item returned exactly once");
        helper.assertTrue(current(t).component(A).orElseThrow().id().equals(LARGE), "new part installed");
        t.menu.setCarried(ItemStack.EMPTY);
        helper.assertTrue(apply(t, GridActionPayload.Action.REMOVE, A, null), "remove");
        helper.assertTrue(t.menu.getCarried().is(largeItem) && current(t).grid().orElseThrow().placements().size() == 1, "one item and area released");
        helper.assertTrue(!apply(t, GridActionPayload.Action.REMOVE, A, null), "cannot remove twice");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void cancellationLeavesCarriedItemsAndGridUntouched(GameTestHelper helper) {
        var t = menu(helper); t.menu.setCarried(new ItemStack(smallItem));
        Consumer<EquipmentStructureInstallEvent> cancelInstall = e -> { if (e.stack() == t.menu.equipmentStack()) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(EquipmentStructureInstallEvent.class, cancelInstall);
        try {
            helper.assertTrue(!apply(t, GridActionPayload.Action.INSTALL, A, new GridPlacement(0, 0)), "install canceled");
            helper.assertTrue(t.menu.getCarried().is(smallItem) && current(t).components().isEmpty() && current(t).grid().isEmpty(), "no partial install");
        } finally { NeoForge.EVENT_BUS.unregister(cancelInstall); }
        helper.assertTrue(apply(t, GridActionPayload.Action.INSTALL, A, new GridPlacement(0, 0)), "setup");
        var before = t.menu.equipmentStack().copy();
        Consumer<EquipmentStructureRemoveEvent> cancelRemove = e -> { if (e.stack() == t.menu.equipmentStack()) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(EquipmentStructureRemoveEvent.class, cancelRemove);
        try {
            helper.assertTrue(!apply(t, GridActionPayload.Action.REMOVE, A, null) && t.menu.getCarried().isEmpty(), "remove canceled");
            t.menu.setCarried(new ItemStack(largeItem));
            helper.assertTrue(!apply(t, GridActionPayload.Action.REPLACE, A, new GridPlacement(0, 0)), "replace removal canceled");
            helper.assertTrue(ItemStack.matches(before, t.menu.equipmentStack()) && t.menu.getCarried().is(largeItem), "cancel kept state");
        } finally { NeoForge.EVENT_BUS.unregister(cancelRemove); }
        NeoForge.EVENT_BUS.addListener(EquipmentStructureInstallEvent.class, cancelInstall);
        try {
            helper.assertTrue(!apply(t, GridActionPayload.Action.REPLACE, A, new GridPlacement(0, 0)), "replace installation canceled");
            helper.assertTrue(ItemStack.matches(before, t.menu.equipmentStack()) && t.menu.getCarried().is(largeItem), "old part not removed early");
        } finally { NeoForge.EVENT_BUS.unregister(cancelInstall); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void movesRequireOwnerLiveMenuCurrentItemAndCurrentDefinitions(GameTestHelper helper) {
        var t = menu(helper); t.menu.setCarried(new ItemStack(smallItem));
        helper.assertTrue(apply(t, GridActionPayload.Action.INSTALL, A, new GridPlacement(0, 0)), "setup");
        var request = request(t, GridActionPayload.Action.MOVE, A, Map.of(A, new GridPlacement(1, 0)));
        helper.assertTrue(!t.menu.applyGridAction(request, helper.makeMockPlayer(GameType.SURVIVAL)), "foreign owner");
        t.player.containerMenu = t.player.inventoryMenu;
        helper.assertTrue(!t.menu.applyGridAction(request, t.player), "closed menu");
        t.player.containerMenu = t.menu;
        var staleState = new GridActionPayload(t.menu.containerId, t.menu.getStateId() + 1, 9, request.expectedEquipment(),
                request.expectedCarried(), request.definitions(), request.action(), A, request.placements());
        helper.assertTrue(!t.menu.applyGridAction(staleState, t.player), "state ID mismatch");
        var staleDefinitions = new GridActionPayload(t.menu.containerId, t.menu.getStateId(), 9, request.expectedEquipment(),
                request.expectedCarried(), "0".repeat(64), request.action(), A, request.placements());
        helper.assertTrue(!t.menu.applyGridAction(staleDefinitions, t.player), "definition generation mismatch");
        t.menu.equipmentStack().setDamageValue(1);
        helper.assertTrue(!t.menu.applyGridAction(request, t.player), "changed item snapshot");
        helper.assertTrue(apply(t, GridActionPayload.Action.MOVE, A, new GridPlacement(1, 0)), "fresh request accepted");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void callbacksCannotCommitAgainstChangedCursorOrEquipment(GameTestHelper helper) {
        var t = menu(helper); t.menu.setCarried(new ItemStack(smallItem));
        Consumer<EquipmentStructureInstallEvent> changedCursor = e -> {
            if (e.stack() == t.menu.equipmentStack()) t.menu.setCarried(new ItemStack(Items.DIAMOND));
        };
        NeoForge.EVENT_BUS.addListener(EquipmentStructureInstallEvent.class, changedCursor);
        try {
            helper.assertTrue(!apply(t, GridActionPayload.Action.INSTALL, A, new GridPlacement(0, 0)), "cursor changed in event");
            helper.assertTrue(current(t).components().isEmpty() && t.menu.getCarried().is(Items.DIAMOND), "outer operation cannot overwrite event state");
        } finally { NeoForge.EVENT_BUS.unregister(changedCursor); }
        t.menu.setCarried(new ItemStack(smallItem));
        helper.assertTrue(apply(t, GridActionPayload.Action.INSTALL, A, new GridPlacement(0, 0)), "setup");
        var before = current(t);
        Consumer<EquipmentGridChangeEvent> changedEquipment = e -> { if (e.stack() == t.menu.equipmentStack()) e.stack().setDamageValue(2); };
        NeoForge.EVENT_BUS.addListener(EquipmentGridChangeEvent.class, changedEquipment);
        try {
            helper.assertTrue(!apply(t, GridActionPayload.Action.MOVE, A, new GridPlacement(1, 0)), "equipment data changed in event");
            helper.assertTrue(current(t) == before && t.menu.equipmentStack().getDamageValue() == 2, "pose and event edit retained");
        } finally { NeoForge.EVENT_BUS.unregister(changedEquipment); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void layoutAndAppearanceSaveIndependentlyWithoutInstallNotifications(GameTestHelper helper) {
        var t = menu(helper); t.menu.setCarried(new ItemStack(smallItem));
        helper.assertTrue(apply(t, GridActionPayload.Action.INSTALL, A, new GridPlacement(0, 0)), "setup");
        var stack = t.menu.equipmentStack();
        var pose = new AppearancePose(3, 4, 5, AppearanceRotation.IDENTITY, 1.25);
        helper.assertTrue(EquipmentStructureApi.setAppearancePose(stack, A, pose), "pose saved");
        var before = current(t).component(A).orElseThrow();
        AtomicInteger installs = new AtomicInteger();
        Consumer<EquipmentStructureInstallEvent> count = e -> { if (e.stack() == stack) installs.incrementAndGet(); };
        NeoForge.EVENT_BUS.addListener(EquipmentStructureInstallEvent.class, count);
        try {
            helper.assertTrue(apply(t, GridActionPayload.Action.MOVE, A, new GridPlacement(1, 0, GridRotation.HALF_TURN)), "move/rotate");
            helper.assertTrue(current(t).component(A).orElseThrow().equals(before) && installs.get() == 0, "move preserves data and does not reinstall");
            var state = current(t).grid();
            helper.assertTrue(EquipmentStructureApi.setAppearancePresentations(stack, Map.of(A, new AppearancePartPresentation(AppearancePose.IDENTITY, false)), false), "batch appearance edit");
            helper.assertTrue(current(t).grid().equals(state), "batch appearance kept layout");
            var expected = current(t).component(A).orElseThrow();
            EquipmentStructureApi.updateComponentData(stack, A, expected, tag -> { tag.putInt("charge", 7); return tag; });
            helper.assertTrue(current(t).grid().equals(state), "component state update kept layout");
        } finally { NeoForge.EVENT_BUS.unregister(count); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void stackAndPacketsRoundTripTheAuthoritativeLayout(GameTestHelper helper) throws Exception {
        var t = menu(helper); t.menu.setCarried(new ItemStack(smallItem));
        helper.assertTrue(apply(t, GridActionPayload.Action.INSTALL, A, new GridPlacement(0, 0)), "setup");
        var b = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(b, t.menu.equipmentStack());
            var decoded = ItemStack.STREAM_CODEC.decode(b);
            helper.assertTrue(ItemStack.matches(decoded, t.menu.equipmentStack()), "vanilla item sync keeps layout");
            var ops = helper.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            var saved = ItemStack.CODEC.encodeStart(ops, decoded).getOrThrow();
            helper.assertTrue(ItemStack.matches(decoded, ItemStack.CODEC.parse(ops, saved).getOrThrow()), "item NBT save/load");
            var request = request(t, GridActionPayload.Action.MOVE, A, Map.of(A, new GridPlacement(1, 0)));
            GridActionPayload.STREAM_CODEC.encode(b, request);
            var received = GridActionPayload.STREAM_CODEC.decode(b);
            helper.assertTrue(t.menu.applyGridAction(received, t.player), "decoded intent works");
            var definitions = GridDefinitions.registered();
            GridDefinitionsPayload.STREAM_CODEC.encode(b, new GridDefinitionsPayload(definitions));
            var receivedDefinitions = GridDefinitionsPayload.STREAM_CODEC.decode(b).definitions();
            helper.assertTrue(definitions.equals(receivedDefinitions) && definitions.fingerprint().equals(receivedDefinitions.fingerprint()), "definition sync round trip");
            GridDefinitionSync.receive(GridDefinitions.EMPTY);
            helper.assertTrue(GridDefinitionSync.current().equals(definitions), "integrated server not overwritten by client data");
            var client = new java.util.concurrent.atomic.AtomicReference<GridDefinitions>();
            var worker = new Thread(new ThreadGroup("grid-client-test"), () -> client.set(GridDefinitionSync.current()));
            worker.start(); worker.join(5000);
            helper.assertTrue(!worker.isAlive() && GridDefinitions.EMPTY.equals(client.get()), "empty sync replaces client defaults");
            helper.assertTrue(b.readableBytes() == 0, "all bytes consumed");
        } finally { b.release(); GridDefinitionSync.clearClient(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void packetDecoderRejectsOversizedAndDuplicateBatches(GameTestHelper helper) {
        var t = menu(helper);
        var before = t.menu.equipmentStack().copy();
        for (int count : new int[]{-1, GridActionPayload.MAX_EDITS + 1, 2}) {
            var b = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                b.writeVarInt(t.menu.containerId); b.writeVarInt(0); b.writeVarInt(1);
                ItemStack.STREAM_CODEC.encode(b, before); ItemStack.OPTIONAL_STREAM_CODEC.encode(b, ItemStack.EMPTY);
                b.writeUtf(GridDefinitions.registered().fingerprint()); b.writeEnum(GridActionPayload.Action.MOVE);
                b.writeResourceLocation(A); b.writeVarInt(count);
                if (count == 2) for (int n = 0; n < count; n++) {
                    b.writeResourceLocation(A); b.writeVarInt(n); b.writeVarInt(0); b.writeEnum(GridRotation.NONE);
                }
                boolean rejected = false;
                try { GridActionPayload.STREAM_CODEC.decode(b); } catch (IllegalArgumentException expected) { rejected = true; }
                helper.assertTrue(rejected, "malformed edit batch rejected");
            } finally { b.release(); }
        }
        helper.assertTrue(ItemStack.matches(before, t.menu.equipmentStack()), "decoding cannot modify equipment");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void quickRemovePacketReturnsPartToVanillaInventory(GameTestHelper helper) {
        var t = menu(helper);
        t.menu.setCarried(new ItemStack(smallItem));
        helper.assertTrue(apply(t, GridActionPayload.Action.INSTALL, A, new GridPlacement(0, 0)), "setup");
        int before = inventoryCount(t.player, smallItem);
        var request = request(t, GridActionPayload.Action.QUICK_REMOVE, A, Map.of());
        var b = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            GridActionPayload.STREAM_CODEC.encode(b, request);
            var decoded = GridActionPayload.STREAM_CODEC.decode(b);
            helper.assertTrue(decoded.action() == GridActionPayload.Action.QUICK_REMOVE
                    && decoded.placements().isEmpty(), "quick-remove intent round trip");
            helper.assertTrue(t.menu.applyGridAction(decoded, t.player), "quick remove applied");
        } finally {
            b.release();
        }
        helper.assertTrue(t.menu.getCarried().isEmpty(), "quick remove leaves cursor empty");
        helper.assertTrue(current(t).component(A).isEmpty(), "component removed from equipment");
        helper.assertTrue(inventoryCount(t.player, smallItem) == before + 1, "one restored item returned to inventory");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void schemaMigrationRejectsMissingCoordinates(GameTestHelper helper) {
        var t = menu(helper); t.menu.setCarried(new ItemStack(smallItem));
        helper.assertTrue(apply(t, GridActionPayload.Action.INSTALL, A, new GridPlacement(1, 0)), "setup");
        var before = current(t);
        try (var migration = EquipmentStructureMigrations.register(HOST, 1, 2, value ->
                new EquipmentStructure(value.hostId(), value.equipmentType(), value.slots(), value.components(), 2))) {
            helper.assertTrue(EquipmentStructureApi.migrate(t.menu.equipmentStack(), 2) == EquipmentStructureApi.MigrationResult.GRID_REJECTED, "missing coordinates rejected");
            helper.assertTrue(current(t).grid().orElseThrow().placements().equals(before.grid().orElseThrow().placements()), "rejected callback leaves positions unchanged");
            helper.assertTrue(current(t).components().equals(before.components()), "parts preserved");
            helper.assertTrue(EquipmentStructureApi.gridLayout(t.menu.equipmentStack()).orElseThrow().allowed(), "new definition fingerprint valid");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void missingCoordinatesAreRejectedAndRemovalRecoversParts(GameTestHelper helper) {
        var t = menu(helper);
        var structure = current(t).withComponent(A, new EquipmentComponentInstance(SMALL, TYPE))
                .withComponent(B, new EquipmentComponentInstance(LARGE, TYPE));
        EquipmentStructureApi.setStructure(t.menu.equipmentStack(), structure);
        helper.assertTrue(!apply(t, GridActionPayload.Action.INITIALIZE, A, null), "missing coordinates rejected");
        helper.assertTrue(current(t).equals(structure), "rejection does not rewrite parts");
        EquipmentStructureApi.setStructure(t.menu.equipmentStack(), structure.withGrid(new GridState(1, 0,
                GridDefinitions.registered().fingerprint(structure), Map.of(A, new GridPlacement(0, 0), B, new GridPlacement(1, 0)))));
        var valid = current(t);
        var invalid = valid.withGrid(new GridState(1, 5, "0".repeat(64), valid.grid().orElseThrow().placements()));
        EquipmentStructureApi.setStructure(t.menu.equipmentStack(), invalid);
        helper.assertTrue(!apply(t, GridActionPayload.Action.MOVE, A, new GridPlacement(3, 1)), "stale definition token blocks movement");
        helper.assertTrue(apply(t, GridActionPayload.Action.REMOVE, A, null), "recovery removal remains possible");
        helper.assertTrue(t.menu.getCarried().is(smallItem) && current(t).component(B).isPresent(), "returned only selected part");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void ordinaryAndShiftInstallUseTheSameGridSpaceCheck(GameTestHelper helper) {
        var t = menu(helper);
        var stack = t.menu.equipmentStack();
        helper.assertTrue(EquipmentStructureApi.installAt(stack, A, new EquipmentComponentInstance(LARGE, TYPE),
                Optional.of(new GridPlacement(0, 0))) == EquipmentStructureApi.InstallResult.INSTALLED, "API explicit install");
        helper.assertTrue(EquipmentStructureApi.install(stack, B, new EquipmentComponentInstance(LARGE, TYPE))
                == EquipmentStructureApi.InstallResult.INSTALLED, "API first fit");
        helper.assertTrue(current(t).grid().orElseThrow().placements().get(B).equals(new GridPlacement(2, 0)), "stable first fit");
        var t2 = menu(helper);
        t2.player.getInventory().setItem(9, new ItemStack(largeItem, 2));
        t2.menu.quickMoveStack(t2.player, EquipmentAssemblyMenu.containerSize());
        helper.assertTrue(current(t2).grid().isPresent() && current(t2).components().size() == 1, "shift uses grid transaction");
        helper.assertTrue(t2.player.getInventory().getItem(9).getCount() == 1, "shift consumes one");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void replacementRejectsStackedCursorAndUnavailableReturnFactory(GameTestHelper helper) {
        var t = menu(helper); t.menu.setCarried(new ItemStack(smallItem));
        helper.assertTrue(apply(t, GridActionPayload.Action.INSTALL, A, new GridPlacement(0, 0)), "setup");
        var before = t.menu.equipmentStack().copy();
        t.menu.setCarried(new ItemStack(largeItem, 2));
        helper.assertTrue(!apply(t, GridActionPayload.Action.REPLACE, A, new GridPlacement(0, 0)), "stacked cursor has no old-item destination");
        helper.assertTrue(ItemStack.matches(before, t.menu.equipmentStack()) && t.menu.getCarried().getCount() == 2, "no consumption");
        var unrecoverable = current(t).withComponent(A, new EquipmentComponentInstance(id("missing_part"), TYPE));
        EquipmentStructureApi.setStructure(t.menu.equipmentStack(), unrecoverable);
        t.menu.setCarried(ItemStack.EMPTY);
        helper.assertTrue(!apply(t, GridActionPayload.Action.REMOVE, A, null), "cannot fabricate missing item factory");
        helper.assertTrue(current(t) == unrecoverable && t.menu.getCarried().isEmpty(), "saved part retained");
        helper.succeed();
    }

    private static TestMenu menu(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var inventory = new SimpleContainer(EquipmentAssemblyMenu.containerSize());
        var equipment = new ItemStack(Items.IRON_SWORD);
        EquipmentStructureApi.setStructure(equipment, new EquipmentHostDefinition(
                HOST, BuiltinEquipmentTypes.SWORD, SLOTS).createStructure());
        inventory.setItem(EquipmentAssemblyMenu.EQUIPMENT_SLOT, equipment);
        var menu = new EquipmentAssemblyMenu(7, player.getInventory(), inventory, SLOTS);
        player.containerMenu = menu;
        return new TestMenu(player, menu);
    }
    private static int inventoryCount(Player player, Item item) {
        int count = 0;
        for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
            var stack = player.getInventory().getItem(index);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }
    private record TestMenu(Player player, EquipmentAssemblyMenu menu) {}
    private static EquipmentStructure current(TestMenu t) { return EquipmentStructureApi.structure(t.menu.equipmentStack()).orElseThrow(); }
    private static GridActionPayload request(TestMenu t, GridActionPayload.Action action, ResourceLocation slot,
                                             Map<ResourceLocation, GridPlacement> placements) {
        return new GridActionPayload(t.menu.containerId, t.menu.getStateId(), 1, t.menu.equipmentStack(), t.menu.getCarried(),
                GridDefinitions.registered().fingerprint(), action, slot, placements);
    }
    private static boolean apply(TestMenu t, GridActionPayload.Action action, ResourceLocation slot, GridPlacement p) {
        return t.menu.applyGridAction(request(t, action, slot, p == null ? Map.of() : Map.of(slot, p)), t.player);
    }
    private static EquipmentComponentDefinition definition(ResourceLocation id, ResourceLocation type, Item item, GridFootprint shape) {
        return GameTestFixtures.component(id, type, type, true, instance -> {
            var stack = new ItemStack(item);
            if (!instance.data().isEmpty()) stack.set(DataComponents.CUSTOM_DATA, CustomData.of(instance.data()));
            return stack;
        }).withFootprint(shape);
    }
    private static final class PartItem extends Item implements EquipmentComponentItem {
        private final ResourceLocation id, type;
        private PartItem(ResourceLocation id, ResourceLocation type) { super(new Properties()); this.id = id; this.type = type; }
        @Override public EquipmentComponentInstance createComponent(ItemStack stack) {
            return new EquipmentComponentInstance(id, type, type, stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
        }
    }
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "grid_test/" + path);
    }
}
