package dev.equipmentstructure.api.test;

import dev.equipmentstructure.api.EquipmentComponentDefinition;
import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentComponentItem;
import dev.equipmentstructure.api.EquipmentComponentRegistry;
import dev.equipmentstructure.api.BuiltinEquipmentTypes;
import dev.equipmentstructure.api.EquipmentHostDefinition;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.appearance.AppearancePose;
import dev.equipmentstructure.api.appearance.AppearancePoseStorage;
import dev.equipmentstructure.api.appearance.AppearanceRotation;
import dev.equipmentstructure.api.event.EquipmentStructureInstallEvent;
import dev.equipmentstructure.api.event.EquipmentStructureRemoveEvent;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;

/** Real vanilla click paths; fixtures are compiled only with includeGameTests. */
@GameTestHolder(EquipmentStructureApiMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class EquipmentAssemblyMenuGameTests {
    private static final ResourceLocation TYPE = id("menu_test_type");
    private static final ResourceLocation SOCKET = id("menu_test_socket");
    private static final ResourceLocation SECOND = id("menu_test_second");
    private static final ResourceLocation PART = id("menu_test_part");
    private static final ResourceLocation CANCELLED = id("menu_test_cancelled");
    private static final ResourceLocation LOCKED = id("menu_test_locked");
    private static final List<EquipmentSlotDefinition> SLOTS = List.of(
            EquipmentSlotDefinition.of(SOCKET, TYPE),
            EquipmentSlotDefinition.of(SECOND, TYPE));
    private static Item partItem;
    private static Item cancelledItem;
    private static Item mutatingItem;
    private static Item throwingItem;
    private static boolean listenersRegistered;

    private EquipmentAssemblyMenuGameTests() {}

    @EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
    public static final class Fixtures {
        @SubscribeEvent
        public static void registerItems(RegisterEvent event) {
            event.register(Registries.ITEM, helper -> {
                partItem = new PartItem(PART);
                cancelledItem = new PartItem(CANCELLED);
                helper.register(PART, partItem);
                helper.register(CANCELLED, cancelledItem);
                mutatingItem = new MutatingPartItem(false);
                throwingItem = new MutatingPartItem(true);
                helper.register(id("menu_test_mutating_reader"), mutatingItem);
                helper.register(id("menu_test_throwing_reader"), throwingItem);
            });
        }
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        event.register(EquipmentAssemblyMenuGameTests.class);
        if (!listenersRegistered) {
            listenersRegistered = true;
            EquipmentComponentRegistry.register(GameTestFixtures.component(PART, TYPE, TYPE,
                    true, instance -> new ItemStack(partItem)));
            EquipmentComponentRegistry.register(GameTestFixtures.component(id("menu_test_mutating_reader"), TYPE, TYPE,
                    true, instance -> new ItemStack(mutatingItem)));
            EquipmentComponentRegistry.register(GameTestFixtures.component(CANCELLED, TYPE, TYPE,
                    true, instance -> new ItemStack(cancelledItem)));
            EquipmentComponentRegistry.register(GameTestFixtures.component(LOCKED, TYPE, TYPE,
                    false, instance -> new ItemStack(partItem)));
            NeoForge.EVENT_BUS.addListener(EquipmentStructureInstallEvent.class, change -> {
                if (change.component().id().equals(CANCELLED)) change.setCanceled(true);
            });
            NeoForge.EVENT_BUS.addListener(EquipmentStructureRemoveEvent.class, change -> {
                if (change.component().id().equals(CANCELLED)) change.setCanceled(true);
            });
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void primaryClickInstallsOneAndPickupRemovesIt(GameTestHelper helper) {
        var test = menu(helper, ItemStack.EMPTY);
        test.menu.setCarried(new ItemStack(partItem, 8));
        test.clickPart(ClickType.PICKUP, 0);
        helper.assertTrue(test.menu.getCarried().getCount() == 7, "install must consume one of eight items");
        test.assertInstalled(helper, PART);
        test.menu.setCarried(ItemStack.EMPTY);
        test.clickPart(ClickType.PICKUP, 0);
        helper.assertTrue(test.menu.getCarried().getCount() == 1, "pickup should return exactly one item");
        helper.assertTrue(EquipmentStructureApi.component(test.menu.equipmentStack(), SOCKET).isEmpty(),
                "pickup must remove structure data, not just the mirror");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void workspaceClickTargetsItsExplicitInterface(GameTestHelper helper) {
        var test = menu(helper, ItemStack.EMPTY);
        test.menu.selectInterface(SOCKET);
        test.menu.setCarried(new ItemStack(partItem, 8));
        helper.assertTrue(test.menu.clickInterface(SECOND, 0, ClickType.PICKUP.ordinal(), test.player), "valid targeted action");
        helper.assertTrue(EquipmentStructureApi.component(test.menu.equipmentStack(), SOCKET).isEmpty(), "previous interface unchanged");
        helper.assertTrue(EquipmentStructureApi.component(test.menu.equipmentStack(), SECOND).isPresent(), "explicit interface installed");
        helper.assertTrue(test.menu.getCarried().getCount() == 7, "only one item consumed");
        test.menu.setCarried(ItemStack.EMPTY);
        test.menu.selectInterface(SOCKET);
        test.menu.clickInterface(SECOND, 0, ClickType.PICKUP.ordinal(), test.player);
        helper.assertTrue(test.menu.getCarried().getCount() == 1, "targeted pickup returns component");
        helper.assertTrue(EquipmentStructureApi.component(test.menu.equipmentStack(), SECOND).isEmpty(), "targeted pickup removes structure");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void invalidWorkspaceActionsCannotMutateItems(GameTestHelper helper) {
        var test = menu(helper, ItemStack.EMPTY);
        test.menu.setCarried(new ItemStack(partItem, 8));
        helper.assertTrue(!test.menu.clickInterface(id("missing"), 0, ClickType.PICKUP.ordinal(), test.player), "unknown interface rejected");
        helper.assertTrue(!test.menu.clickInterface(SOCKET, 50, ClickType.PICKUP.ordinal(), test.player), "invalid button rejected");
        helper.assertTrue(!test.menu.clickInterface(SOCKET, 0, -1, test.player), "invalid action rejected");
        helper.assertTrue(!test.menu.clickInterface(SOCKET, 0, ClickType.QUICK_CRAFT.ordinal(), test.player), "quick craft rejected");
        helper.assertTrue(test.menu.getCarried().getCount() == 8, "no items consumed");
        helper.assertTrue(EquipmentStructureApi.component(test.menu.equipmentStack(), SOCKET).isEmpty(), "no data installed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lastQuickMovedPartStillHasMirror(GameTestHelper helper) {
        var test = menu(helper, ItemStack.EMPTY);
        test.player.getInventory().setItem(9, new ItemStack(partItem));
        test.menu.clicked(EquipmentAssemblyMenu.containerSize(), 0, ClickType.QUICK_MOVE, test.player);
        test.assertInstalled(helper, PART);
        helper.assertTrue(test.partStack().getCount() == 1, "last shifted part must retain its projection");
        helper.assertTrue(test.player.getInventory().getItem(9).isEmpty(), "source must be consumed once");
        test.clickPart(ClickType.QUICK_MOVE, 0);
        helper.assertTrue(test.player.getInventory().countItem(partItem) == 1, "shift removal returns one item");
        helper.assertTrue(EquipmentStructureApi.component(test.menu.equipmentStack(), SOCKET).isEmpty(),
                "shift removal must clear installed data");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cancelledRemovalBlocksEveryTransferPath(GameTestHelper helper) {
        for (ClickType click : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE, ClickType.SWAP, ClickType.THROW)) {
            var test = menu(helper, equipment(CANCELLED));
            test.clickPart(click, 0);
            test.assertInstalled(helper, CANCELLED);
            helper.assertTrue(test.partStack().getCount() == 1, "cancelled removal must preserve mirror for " + click);
            helper.assertTrue(test.menu.getCarried().isEmpty(), "cancelled removal must not fill cursor");
            helper.assertTrue(test.player.getInventory().isEmpty(), "cancelled removal must not fill inventory");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void permanentPartCannotBePickedUp(GameTestHelper helper) {
        var test = menu(helper, equipment(LOCKED));
        test.clickPart(ClickType.PICKUP, 0);
        test.assertInstalled(helper, LOCKED);
        helper.assertTrue(test.menu.getCarried().isEmpty(), "permanent part cannot be picked up");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void doubleClickElsewhereCannotCollectMirror(GameTestHelper helper) {
        var test = menu(helper, equipment(CANCELLED));
        test.menu.setCarried(new ItemStack(cancelledItem));
        test.player.getInventory().setItem(10, new ItemStack(cancelledItem, 3));
        test.menu.clicked(EquipmentAssemblyMenu.containerSize(), 0, ClickType.PICKUP_ALL, test.player);
        helper.assertTrue(test.menu.getCarried().getCount() == 4,
                "double click should collect real inventory items but never the installed projection");
        helper.assertTrue(test.partStack().getCount() == 1, "double click must retain mirror");
        test.assertInstalled(helper, CANCELLED);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void sameSchemaEquipmentReplacementClearsOldProjection(GameTestHelper helper) {
        var test = menu(helper, equipment(PART));
        test.menu.setCarried(equipment(CANCELLED));
        test.menu.clicked(EquipmentAssemblyMenu.EQUIPMENT_SLOT, 0, ClickType.PICKUP, test.player);
        helper.assertTrue(EquipmentStructureApi.component(test.menu.getCarried(), SOCKET)
                .orElseThrow().id().equals(PART), "old equipment must retain its original part");
        test.menu.selectInterface(SOCKET);
        helper.assertTrue(test.partStack().is(cancelledItem), "new equipment must show its own part");
        test.menu.removed(test.player);
        helper.assertTrue(test.player.getInventory().countItem(partItem) == 0, "old mirror must not be returned");
        helper.assertTrue(test.player.getInventory().countItem(cancelledItem) == 0, "new mirror must not be returned");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void switchingByIndexUsesSameTransactionAsId(GameTestHelper helper) {
        var test = menu(helper, equipment(PART));
        test.menu.selectInterface(1);
        helper.assertTrue(test.partStack().isEmpty(), "index selection must discard the old interface mirror");
        test.assertInstalled(helper, PART);
        helper.assertTrue(test.player.getInventory().countItem(partItem) == 0, "selection must not duplicate components");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rejectedInstallReturnsPendingInputOnClose(GameTestHelper helper) {
        var test = menu(helper, ItemStack.EMPTY);
        test.menu.setCarried(new ItemStack(cancelledItem, 8));
        test.clickPart(ClickType.PICKUP, 0);
        helper.assertTrue(EquipmentStructureApi.component(test.menu.equipmentStack(), SOCKET).isEmpty(),
                "rejected installation must not update equipment");
        test.menu.removed(test.player);
        helper.assertTrue(test.player.getInventory().countItem(cancelledItem)
                        + test.menu.getCarried().getCount() == 8, "closing must preserve all rejected input");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void closingAfterPrimaryInstallDoesNotReturnMirror(GameTestHelper helper) {
        var test = menu(helper, ItemStack.EMPTY);
        test.menu.setCarried(new ItemStack(partItem));
        test.clickPart(ClickType.PICKUP, 0);
        test.assertInstalled(helper, PART);
        test.menu.removed(test.player);
        helper.assertTrue(test.player.getInventory().countItem(partItem) == 0, "closing must not duplicate mirror");
        helper.assertTrue(test.player.getInventory().countItem(Items.IRON_SWORD) == 1, "equipment must be returned");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hiddenStagingSlotIsInactive(GameTestHelper helper) {
        var test = menu(helper, ItemStack.EMPTY);
        test.menu.selectInterface((ResourceLocation) null);
        helper.assertTrue(!test.menu.getSlot(EquipmentAssemblyMenu.PART_SLOT_START).isActive(),
                "an unselected interface must not have an invisible active slot");
        helper.assertTrue(!test.menu.canDragTo(test.menu.getSlot(EquipmentAssemblyMenu.PART_SLOT_START)),
                "quick drag must skip the staging slot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void externalApiMutationRefreshesProjection(GameTestHelper helper) {
        var test = menu(helper, equipment(PART));
        helper.assertTrue(EquipmentStructureApi.remove(test.menu.equipmentStack(), SOCKET).isPresent(),
                "external remove should succeed");
        test.menu.broadcastChanges();
        helper.assertTrue(test.partStack().isEmpty(), "external remove must discard obsolete projection");
        EquipmentStructureApi.install(test.menu.equipmentStack(), SOCKET,
                new EquipmentComponentInstance(PART, TYPE, TYPE));
        test.menu.broadcastChanges();
        helper.assertTrue(test.partStack().is(partItem), "external installation must become visible");
        test.menu.removed(test.player);
        helper.assertTrue(test.player.getInventory().countItem(partItem) == 0, "external refresh must not return a mirror");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fortyInterfacesUseOnePhysicalStagingSlot(GameTestHelper helper) {
        var test = menu(helper, ItemStack.EMPTY);
        for (int i = 2; i < 40; i++) {
            EquipmentStructureApi.addSlot(test.menu.equipmentStack(),
                    EquipmentSlotDefinition.of(id("menu_test_dynamic_" + i), TYPE));
        }
        test.menu.broadcastChanges();
        helper.assertTrue(test.menu.structureSlotCount() == 40, "all dynamic interfaces must be readable");
        helper.assertTrue(test.menu.slots.size() == 38, "dynamic interfaces must not grow physical menu slots");
        test.menu.selectInterface(39);
        test.menu.setCarried(new ItemStack(partItem));
        test.clickPart(ClickType.PICKUP, 0);
        helper.assertTrue(EquipmentStructureApi.component(test.menu.equipmentStack(), id("menu_test_dynamic_39"))
                .isPresent(), "a component can be installed beyond interface sixteen");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hotbarInsertionAndRemovalPreserveItemCounts(GameTestHelper helper) {
        var test = menu(helper, ItemStack.EMPTY);
        test.player.getInventory().setItem(0, new ItemStack(partItem, 8));
        test.clickPart(ClickType.SWAP, 0);
        test.assertInstalled(helper, PART);
        helper.assertTrue(test.player.getInventory().getItem(0).getCount() == 7, "hotbar insertion must consume one");
        test.clickPart(ClickType.SWAP, 1);
        helper.assertTrue(test.player.getInventory().getItem(1).getCount() == 1, "empty hotbar slot receives one part");
        helper.assertTrue(EquipmentStructureApi.component(test.menu.equipmentStack(), SOCKET).isEmpty(),
                "hotbar removal must clear component data");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void ordinaryInventoryShiftClickDoesNotMergeIntoItself(GameTestHelper helper) {
        var test = menu(helper, ItemStack.EMPTY);
        test.player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 32));
        test.menu.clicked(EquipmentAssemblyMenu.containerSize(), 0, ClickType.QUICK_MOVE, test.player);
        helper.assertTrue(test.player.getInventory().getItem(9).isEmpty(), "source main-inventory slot must empty");
        helper.assertTrue(test.player.getInventory().getItem(0).getCount() == 32, "hotbar should receive all items");
        helper.assertTrue(test.player.getInventory().countItem(Items.COBBLESTONE) == 32, "shift click conserves items");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void faultyFactoryLeavesInstalledComponentIntact(GameTestHelper helper) {
        ResourceLocation faulty = id("menu_test_faulty_factory");
        EquipmentComponentRegistry.register(GameTestFixtures.component(faulty, TYPE, TYPE, true,
                instance -> { throw new IllegalStateException("deliberate factory failure"); }));
        try {
            var test = menu(helper, equipment(faulty));
            test.menu.broadcastChanges();
            helper.assertTrue(test.partStack().isEmpty(), "broken factory should not create a transfer item");
            test.clickPart(ClickType.PICKUP, 0);
            test.assertInstalled(helper, faulty);
        } finally {
            EquipmentComponentRegistry.unregister(faulty);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void componentReadersReceiveDefensiveSingleItemCopy(GameTestHelper helper) {
        var test = menu(helper, ItemStack.EMPTY);
        test.menu.setCarried(new ItemStack(mutatingItem, 8));
        test.clickPart(ClickType.PICKUP, 0);
        test.assertInstalled(helper, id("menu_test_mutating_reader"));
        helper.assertTrue(test.menu.getCarried().getCount() == 7,
                "addon mutation must not consume the real carried stack");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void componentReaderExceptionLeavesInputUntouched(GameTestHelper helper) {
        var test = menu(helper, ItemStack.EMPTY);
        test.menu.setCarried(new ItemStack(throwingItem, 8));
        test.clickPart(ClickType.PICKUP, 0);
        helper.assertTrue(test.menu.getCarried().getCount() == 8, "reader failure must preserve input");
        helper.assertTrue(test.partStack().isEmpty(), "reader failure must not create a pending item");
        helper.assertTrue(EquipmentStructureApi.component(test.menu.equipmentStack(), SOCKET).isEmpty(),
                "reader failure must not update equipment");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void quickInstallFindsAnEmptyTargetAndConsumesOnePerGesture(GameTestHelper helper) {
        var test = menu(helper, ItemStack.EMPTY);
        test.menu.selectInterface((ResourceLocation) null);
        test.player.getInventory().setItem(9, new ItemStack(partItem, 8));
        test.menu.clicked(EquipmentAssemblyMenu.containerSize(), 0, ClickType.QUICK_MOVE, test.player);
        test.assertInstalled(helper, PART);
        helper.assertTrue(EquipmentStructureApi.component(test.menu.equipmentStack(), SECOND).isEmpty(), "one gesture must install only one");
        helper.assertTrue(test.player.getInventory().getItem(9).getCount() == 7, "one source consumed without selection");
        test.menu.clicked(EquipmentAssemblyMenu.containerSize(), 0, ClickType.QUICK_MOVE, test.player);
        helper.assertTrue(EquipmentStructureApi.component(test.menu.equipmentStack(), SECOND).isPresent(), "occupied selection must find next empty target");
        helper.assertTrue(test.menu.selectedInterface().orElseThrow().id().equals(SECOND), "installed destination selected");
        helper.assertTrue(test.player.getInventory().getItem(9).getCount() == 6, "second gesture consumes one");
        var full = test.menu.equipmentStack().copy();
        test.menu.clicked(EquipmentAssemblyMenu.containerSize(), 0, ClickType.QUICK_MOVE, test.player);
        helper.assertTrue(ItemStack.matches(full, test.menu.equipmentStack()), "full equipment never replaces existing parts");
        helper.assertTrue(test.player.getInventory().getItem(9).getCount() == 6, "full equipment consumes nothing");
        test.menu.removed(test.player);
        helper.assertTrue(test.player.getInventory().countItem(partItem) == 6, "closing creates no physical mirror duplicates");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void quickInstallPrefersSelectionAndHonorsCancelledEvents(GameTestHelper helper) {
        var test = menu(helper, ItemStack.EMPTY);
        test.menu.selectInterface(SECOND);
        test.player.getInventory().setItem(9, new ItemStack(partItem, 4));
        test.menu.clicked(EquipmentAssemblyMenu.containerSize(), 0, ClickType.QUICK_MOVE, test.player);
        helper.assertTrue(EquipmentStructureApi.component(test.menu.equipmentStack(), SOCKET).isEmpty(), "selection takes priority over registration order");
        helper.assertTrue(EquipmentStructureApi.component(test.menu.equipmentStack(), SECOND).isPresent(), "selected target installed");
        test.player.getInventory().setItem(9, new ItemStack(cancelledItem, 4));
        var before = test.menu.equipmentStack().copy();
        test.menu.clicked(EquipmentAssemblyMenu.containerSize(), 0, ClickType.QUICK_MOVE, test.player);
        helper.assertTrue(ItemStack.matches(before, test.menu.equipmentStack()), "cancelled event must not mutate equipment");
        helper.assertTrue(test.player.getInventory().getItem(9).getCount() == 4, "cancelled event consumes nothing");
        helper.assertTrue(test.menu.selectedInterface().orElseThrow().id().equals(SECOND), "failed install preserves selection");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void quickInstallCanReachLaterPagesAndRejectsIncompatibleInputs(GameTestHelper helper) {
        var test = menu(helper, ItemStack.EMPTY);
        var many = new java.util.ArrayList<EquipmentSlotDefinition>();
        for (int i = 0; i < 20; i++) many.add(EquipmentSlotDefinition.of(id("unrelated_" + i), id("other_type")));
        many.add(EquipmentSlotDefinition.of(SECOND, TYPE));
        EquipmentStructureApi.setStructure(test.menu.equipmentStack(), new EquipmentHostDefinition(
                id("paged_test"), BuiltinEquipmentTypes.SWORD, many).createStructure());
        test.menu.selectInterface(many.getFirst().id());
        test.player.getInventory().setItem(9, new ItemStack(partItem, 3));
        test.menu.clicked(EquipmentAssemblyMenu.containerSize(), 0, ClickType.QUICK_MOVE, test.player);
        helper.assertTrue(EquipmentStructureApi.component(test.menu.equipmentStack(), SECOND).isPresent(), "later page is eligible");
        helper.assertTrue(test.player.getInventory().getItem(9).getCount() == 2, "only one input consumed");
        test.menu.clicked(EquipmentAssemblyMenu.containerSize(), 0, ClickType.QUICK_MOVE, test.player);
        helper.assertTrue(test.player.getInventory().getItem(9).getCount() == 2, "incompatible empty positions reject input");
        helper.assertTrue(EquipmentStructureApi.structure(test.menu.equipmentStack()).orElseThrow().components().size() == 1, "no incompatible installs");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void serverFeatureSwitchesGuardQuickInstallAndEveryAppearanceSavePath(GameTestHelper helper) {
        var spec = dev.equipmentstructure.api.EquipmentFeatureConfig.SPEC;
        helper.assertTrue(spec.isLoaded(), "server feature configuration must be loaded");
        var quick = (net.neoforged.neoforge.common.ModConfigSpec.BooleanValue) spec.getValues().get("assembly.quickInstall");
        var pose = (net.neoforged.neoforge.common.ModConfigSpec.BooleanValue) spec.getValues().get("appearance.positionEditing");
        var original = (net.neoforged.neoforge.common.ModConfigSpec.BooleanValue) spec.getValues().get("appearance.hideOriginal");
        var parts = (net.neoforged.neoforge.common.ModConfigSpec.BooleanValue) spec.getValues().get("appearance.hideAttachments");
        boolean q = quick.get(), p = pose.get(), o = original.get(), a = parts.get();
        try {
            quick.set(false); pose.set(false); original.set(false); parts.set(false);
            var test = menu(helper, ItemStack.EMPTY);
            test.player.containerMenu = test.menu;
            test.player.getInventory().setItem(9, new ItemStack(partItem, 3));
            test.menu.clicked(EquipmentAssemblyMenu.containerSize(), 0, ClickType.QUICK_MOVE, test.player);
            helper.assertTrue(test.player.getInventory().getItem(9).getCount() == 3, "disabled quick install consumes nothing");
            test.menu.setCarried(new ItemStack(partItem)); test.clickPart(ClickType.PICKUP, 0);
            test.assertInstalled(helper, PART);
            var before = test.menu.equipmentStack().copy();
            var moved = new AppearancePose(1, 2, 3, AppearanceRotation.IDENTITY, 1.2);
            var identity = AppearancePose.IDENTITY;
            var hidden = new dev.equipmentstructure.api.appearance.AppearancePartPresentation(identity, false);
            var motion = new dev.equipmentstructure.api.appearance.AppearanceMotionSettings(
                    dev.equipmentstructure.api.appearance.AppearanceMotionSettings.Center.LOCAL,
                    new AppearanceRotation(1, 0, 0, 1));
            var orbitEdit = new dev.equipmentstructure.api.appearance.AppearancePartPresentation(identity, true, motion);
            helper.assertTrue(!test.menu.applyAppearanceLayout(before, java.util.Map.of(SOCKET, orbitEdit), true, test.player),
                    "disabled position editing rejects orbit center and tilt changes");
            helper.assertTrue(!test.menu.applyAppearancePose(SOCKET, before, moved, test.player), "legacy pose path checks permission");
            helper.assertTrue(!test.menu.applyAppearancePresentation(SOCKET, before, moved,
                    dev.equipmentstructure.api.appearance.AppearanceVisibility.VISIBLE, test.player), "single presentation checks pose permission");
            helper.assertTrue(!test.menu.applyAppearancePresentation(SOCKET, before, identity,
                    new dev.equipmentstructure.api.appearance.AppearanceVisibility(false, true), test.player), "single presentation checks original hiding");
            helper.assertTrue(!test.menu.applyAppearanceLayout(before, java.util.Map.of(SOCKET, hidden), true, test.player), "batch checks attachment hiding");
            helper.assertTrue(!test.menu.applyAppearanceLayout(before, java.util.Map.of(), false, test.player), "empty batch still checks original hiding");
            helper.assertTrue(ItemStack.matches(before, test.menu.equipmentStack()), "all rejected saves leave data intact");
            // Existing hidden data is kept when rules change, but the player can always reveal it.
            EquipmentStructureApi.setAppearancePresentation(test.menu.equipmentStack(), SOCKET, moved,
                    new dev.equipmentstructure.api.appearance.AppearanceVisibility(false, false));
            var shown = new dev.equipmentstructure.api.appearance.AppearancePartPresentation(moved, true);
            helper.assertTrue(test.menu.applyAppearanceLayout(test.menu.equipmentStack().copy(), java.util.Map.of(SOCKET, shown), true, test.player),
                    "disabled hiding must not trap already hidden equipment");
            pose.set(true);
            helper.assertTrue(test.menu.applyAppearanceLayout(test.menu.equipmentStack().copy(), java.util.Map.of(SOCKET, orbitEdit), true, test.player),
                    "enabled position editing saves orbit overrides");
            pose.set(false);
            helper.assertTrue(test.menu.applyAppearancePose(SOCKET, test.menu.equipmentStack().copy(), identity, test.player),
                    "legacy pose save with unchanged pose must retain orbit settings even when editing is disabled");
            helper.assertTrue(test.menu.applyAppearancePresentation(SOCKET, test.menu.equipmentStack().copy(), identity,
                    dev.equipmentstructure.api.appearance.AppearanceVisibility.VISIBLE, test.player),
                    "legacy visibility save must retain orbit settings");
            helper.assertTrue(dev.equipmentstructure.api.appearance.AppearanceMotionSettings.read(
                    EquipmentStructureApi.component(test.menu.equipmentStack(), SOCKET).orElseThrow()).equals(motion),
                    "legacy saves must not reset orbit center or tilt");
        } finally { quick.set(q); pose.set(p); original.set(o); parts.set(a); }
        helper.succeed();
    }

    private static TestMenu menu(GameTestHelper helper, ItemStack equipment) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SimpleContainer container = new SimpleContainer(EquipmentAssemblyMenu.containerSize());
        container.setItem(0, equipment.isEmpty() ? equipment(null) : equipment);
        var menu = new EquipmentAssemblyMenu(1, player.getInventory(), container, SLOTS);
        menu.selectInterface(SOCKET);
        return new TestMenu(player, menu);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void poseSaveAcceptsEquipmentSnapshotAfterUnrelatedMenuUpdates(GameTestHelper helper) {
        var test = menu(helper, equipment(PART));
        ItemStack expected = test.menu.equipmentStack().copy();
        test.player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 12));
        test.menu.setCarried(new ItemStack(partItem, 7));
        test.menu.selectInterface(SECOND);
        test.menu.broadcastChanges();
        var pose = new AppearancePose(3, 2, -1, AppearanceRotation.IDENTITY, 1.5);
        helper.assertTrue(test.menu.applyAppearancePose(SOCKET, expected, pose, test.player),
                "unrelated inventory, cursor and selected interface updates must not reject a current equipment snapshot");
        helper.assertTrue(AppearancePoseStorage.read(EquipmentStructureApi.component(test.menu.equipmentStack(), SOCKET)
                .orElseThrow()).filter(pose::equals).isPresent(), "accepted pose must be stored on the server item");
        helper.assertTrue(EquipmentStructureApi.component(expected, SOCKET).flatMap(AppearancePoseStorage::read).isEmpty(),
                "request snapshot remains unchanged");
        helper.assertTrue(test.menu.getCarried().getCount() == 7
                && test.player.getInventory().countItem(Items.COBBLESTONE) == 12, "pose save preserves unrelated items");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void staleOrForeignPoseSaveCannotOverwriteCurrentEquipment(GameTestHelper helper) {
        var test = menu(helper, equipment(PART));
        ItemStack expected = test.menu.equipmentStack().copy();
        var pose = new AppearancePose(3, 2, -1, AppearanceRotation.IDENTITY, 1.5);
        Player other = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(!test.menu.applyAppearancePose(SOCKET, expected, pose, other), "another player cannot save this menu");
        helper.assertTrue(!test.menu.applyAppearancePose(SECOND, expected, pose, test.player), "empty interface cannot receive a pose");
        test.menu.equipmentStack().setDamageValue(1);
        ItemStack before = test.menu.equipmentStack().copy();
        helper.assertTrue(!test.menu.applyAppearancePose(SOCKET, expected, pose, test.player), "stale equipment snapshot rejected");
        helper.assertTrue(!test.menu.applyAppearancePose(SOCKET, ItemStack.EMPTY, pose, test.player), "empty snapshot rejected");
        helper.assertTrue(ItemStack.matches(before, test.menu.equipmentStack()), "rejection must leave the current equipment intact");
        helper.assertTrue(test.menu.applyAppearancePose(SOCKET, before, pose, test.player), "fresh snapshot accepted");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rapidSelectionDoesNotTransferMirrors(GameTestHelper helper) {
        var test = menu(helper, equipment(PART));
        for (int i = 0; i < 100; i++) {
            test.menu.selectInterface(SECOND);
            test.menu.selectInterface(SOCKET);
        }
        test.assertInstalled(helper, PART);
        helper.assertTrue(test.partStack().getCount() == 1, "only the final projection is present");
        helper.assertTrue(test.player.getInventory().countItem(partItem) == 0, "rapid switching creates no physical items");
        test.clickPart(ClickType.PICKUP, 0);
        helper.assertTrue(test.menu.getCarried().getCount() == 1, "final removal returns one item");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void batchAppearanceRequiresLiveMenuAndRejectsPartialStaleOrForeignWrites(GameTestHelper helper) {
        var test = menu(helper, equipment(PART));
        var stack = test.menu.equipmentStack();
        EquipmentStructureApi.setStructure(stack, EquipmentStructureApi.structure(stack).orElseThrow()
                .withComponent(SECOND, new EquipmentComponentInstance(PART, TYPE, TYPE)));
        var expected = stack.copy();
        var edit = new dev.equipmentstructure.api.appearance.AppearancePartPresentation(
                new AppearancePose(3, 2, -1, AppearanceRotation.IDENTITY, 1.5), false);
        var edits = java.util.Map.of(SOCKET, edit, SECOND, edit);
        helper.assertTrue(!test.menu.applyAppearanceLayout(expected, edits, false, test.player), "inactive menu must reject saves");
        test.player.containerMenu = test.menu;
        helper.assertTrue(!test.menu.applyAppearanceLayout(expected, edits, false, helper.makeMockPlayer(GameType.SURVIVAL)),
                "another player must not save this menu");
        helper.assertTrue(!test.menu.applyAppearanceLayout(expected, java.util.Map.of(SOCKET, edit, id("unknown_slot"), edit), false, test.player),
                "mixed valid/invalid targets must all be rejected");
        helper.assertTrue(ItemStack.matches(stack, expected), "rejected batch must not mutate even its valid target");
        stack.setDamageValue(1);
        var current = stack.copy();
        helper.assertTrue(!test.menu.applyAppearanceLayout(expected, edits, false, test.player), "stale snapshot must reject entire batch");
        helper.assertTrue(ItemStack.matches(stack, current), "stale batch changed equipment");
        helper.assertTrue(test.menu.applyAppearanceLayout(current, edits, false, test.player), "live owner can save both parts");
        for (var slot : java.util.List.of(SOCKET, SECOND)) {
            helper.assertTrue(dev.equipmentstructure.api.appearance.AppearancePartPresentation.read(
                    EquipmentStructureApi.component(stack, slot).orElseThrow()).equals(edit), "every edited part must be saved");
        }
        helper.assertTrue(!dev.equipmentstructure.api.appearance.AppearanceVisibilityStorage.originalVisible(stack), "host visibility saved with batch");
        helper.succeed();
    }

    private static ItemStack equipment(ResourceLocation part) {
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        var structure = new EquipmentHostDefinition(
                id("menu_test_host"), BuiltinEquipmentTypes.SWORD, SLOTS).createStructure();
        if (part != null) structure = structure.withComponents(SOCKET, List.of(new EquipmentComponentInstance(part, TYPE, TYPE)));
        EquipmentStructureApi.setStructure(stack, structure);
        return stack;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, path);
    }

    private record TestMenu(Player player, EquipmentAssemblyMenu menu) {
        ItemStack partStack() { return menu.getSlot(EquipmentAssemblyMenu.PART_SLOT_START).getItem(); }
        void clickPart(ClickType type, int button) { menu.clicked(EquipmentAssemblyMenu.PART_SLOT_START, button, type, player); }
        void assertInstalled(GameTestHelper helper, ResourceLocation id) {
            helper.assertTrue(EquipmentStructureApi.component(menu.equipmentStack(), SOCKET)
                    .map(component -> component.id().equals(id)).orElse(false), "expected installed component " + id);
        }
    }

    private static final class PartItem extends Item implements EquipmentComponentItem {
        private final ResourceLocation componentId;
        private PartItem(ResourceLocation componentId) {
            super(new Item.Properties());
            this.componentId = componentId;
        }
        @Override
        public EquipmentComponentInstance createComponent(ItemStack stack) {
            return new EquipmentComponentInstance(componentId, TYPE, TYPE);
        }
    }

    private static final class MutatingPartItem extends Item implements EquipmentComponentItem {
        private final boolean fail;
        private MutatingPartItem(boolean fail) {
            super(new Item.Properties());
            this.fail = fail;
        }
        @Override
        public EquipmentComponentInstance createComponent(ItemStack stack) {
            if (stack.getCount() != 1) throw new IllegalArgumentException("expected single-item snapshot");
            stack.setCount(0);
            if (fail) throw new IllegalStateException("deliberate reader failure");
            return new EquipmentComponentInstance(id("menu_test_mutating_reader"), TYPE, TYPE);
        }
    }
}
