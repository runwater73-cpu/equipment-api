package dev.equipmentstructure.api.test;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.attribute.EquipmentAttributeContribution;
import dev.equipmentstructure.api.attribute.EquipmentAttributeRegistry;
import dev.equipmentstructure.api.event.EquipmentStructureChangedEvent;
import dev.equipmentstructure.api.event.EquipmentStructureInstallEvent;
import dev.equipmentstructure.api.event.EquipmentStructureRemoveEvent;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Actual stack, menu, entity and event boundaries; excluded from the release artifact. */
@GameTestHolder(EquipmentStructureApiMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class EquipmentComponentBehaviorGameTests {
    private static final ResourceLocation TYPE = id("type");
    private static final ResourceLocation SLOT = id("primary");
    private static final ResourceLocation SECOND = id("secondary");
    private static final List<EquipmentSlotDefinition> SLOTS = List.of(
            EquipmentSlotDefinition.of(SLOT, TYPE), EquipmentSlotDefinition.of(SECOND, TYPE));

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(EquipmentComponentBehaviorGameTests.class);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void assemblyChecksAreReadOnlyAndNotificationsRunOnce(GameTestHelper helper) {
        ItemStack stack = equipment();
        AtomicInteger installs = new AtomicInteger();
        AtomicInteger removals = new AtomicInteger();
        List<String> notifications = new ArrayList<>();
        try (var part = bind("read_only", new EquipmentComponentBehavior() {
            @Override public boolean canInstall(EquipmentComponentContext ctx) {
                installs.incrementAndGet();
                ctx.equipment().shrink(1);
                EquipmentStructureApi.clearStructure(ctx.equipment());
                ctx.component().data().putInt("tampered", 1);
                helper.assertTrue(!ctx.equipment().isEmpty() && ctx.wearer().isEmpty(), "isolated preview snapshot");
                return true;
            }
            @Override public boolean canRemove(EquipmentComponentContext ctx) {
                removals.incrementAndGet();
                EquipmentStructureApi.clearStructure(ctx.equipment());
                return true;
            }
            @Override public void onInstalled(EquipmentComponentContext ctx) {
                helper.assertTrue(ctx.structure().component(SLOT).orElseThrow() == ctx.component(), "installed snapshot");
                ctx.equipment().shrink(1);
                notifications.add("installed");
            }
            @Override public void onRemoved(EquipmentComponentContext ctx) {
                helper.assertTrue(ctx.structure().component(SLOT).isPresent(), "previous structure retained");
                helper.assertTrue(EquipmentStructureApi.component(ctx.equipment(), SLOT).isEmpty(), "post-removal stack");
                notifications.add("removed");
            }
        })) {
            helper.assertTrue(EquipmentStructureApi.checkInstall(stack, SLOT, part.instance()).isAllowed(), "preview allowed");
            helper.assertTrue(installs.get() == 1 && notifications.isEmpty(), "preview has no notification");
            helper.assertTrue(EquipmentStructureApi.install(stack, SLOT, part.instance()) == EquipmentStructureApi.InstallResult.INSTALLED,
                    "install allowed");
            helper.assertTrue(installs.get() == 2, "one install check per API install");
            helper.assertTrue(!EquipmentStructureApi.checkInstall(stack, SLOT, part.instance()).isAllowed()
                    && installs.get() == 2, "occupied slots skip behavior");
            helper.assertTrue(stack.getCount() == 1 && !part.instance().data().contains("tampered"), "host and data isolated");
            helper.assertTrue(EquipmentStructureApi.checkRemove(stack, SLOT).isAllowed() && removals.get() == 1, "removal preview");
            helper.assertTrue(EquipmentStructureApi.remove(stack, SLOT).isPresent() && removals.get() == 2, "one removal check");
            helper.assertTrue(notifications.equals(List.of("installed", "removed")), "one notification per commit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rejectionExceptionsAndRecursionFailClosed(GameTestHelper helper) {
        ItemStack stack = equipment();
        var original = current(stack);
        try (var part = bind("reject", new EquipmentComponentBehavior() {
            @Override public boolean canInstall(EquipmentComponentContext ctx) { return false; }
        })) {
            helper.assertTrue(EquipmentStructureApi.install(stack, SLOT, part.instance())
                    == EquipmentStructureApi.InstallResult.BEHAVIOR_REJECTED, "explicit rejection");
        }
        try (var part = bind("throw_checks", new EquipmentComponentBehavior() {
            @Override public boolean canInstall(EquipmentComponentContext ctx) { throw new IllegalStateException("test check failure"); }
            @Override public boolean canRemove(EquipmentComponentContext ctx) { throw new IllegalStateException("test remove failure"); }
        })) {
            helper.assertTrue(EquipmentStructureApi.install(stack, SLOT, part.instance())
                    == EquipmentStructureApi.InstallResult.BEHAVIOR_REJECTED, "throwing checks reject");
            helper.assertTrue(current(stack) == original, "rejected install does not write");
            EquipmentStructureApi.setStructure(stack, original.withComponent(SLOT, part.instance()));
            var installed = current(stack);
            helper.assertTrue(EquipmentStructureApi.checkRemove(stack, SLOT).status()
                    == EquipmentStructureApi.RemoveCheckStatus.BEHAVIOR_REJECTED, "removal preview rejects exception");
            helper.assertTrue(EquipmentStructureApi.remove(stack, SLOT).isEmpty() && current(stack) == installed, "no failed removal");
        }
        EquipmentStructureApi.setStructure(stack, original);
        try (var part = bind("recursive", new EquipmentComponentBehavior() {
            @Override public boolean canInstall(EquipmentComponentContext ctx) {
                return EquipmentStructureApi.checkInstall(ctx.equipment(), ctx.slot().id(), ctx.component()).isAllowed();
            }
            @Override public boolean canRemove(EquipmentComponentContext ctx) {
                return EquipmentStructureApi.checkRemove(ctx.equipment(), ctx.slot().id()).isAllowed();
            }
        })) {
            helper.assertTrue(!EquipmentStructureApi.checkInstall(stack, SLOT, part.instance()).isAllowed(), "recursive install rejects");
            EquipmentStructureApi.setStructure(stack, original.withComponent(SLOT, part.instance()));
            helper.assertTrue(EquipmentStructureApi.remove(stack, SLOT).isEmpty(), "recursive removal rejects");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cancellationAndConflictsNeverNotify(GameTestHelper helper) {
        ItemStack stack = equipment();
        List<String> notifications = new ArrayList<>();
        AtomicBoolean cancel = new AtomicBoolean(true);
        Consumer<EquipmentStructureInstallEvent> beforeInstall = event -> {
            if (event.stack() == stack) event.setCanceled(cancel.get());
        };
        Consumer<EquipmentStructureRemoveEvent> beforeRemove = event -> {
            if (event.stack() == stack) event.setCanceled(cancel.get());
        };
        NeoForge.EVENT_BUS.addListener(EquipmentStructureInstallEvent.class, beforeInstall);
        NeoForge.EVENT_BUS.addListener(EquipmentStructureRemoveEvent.class, beforeRemove);
        try (var part = bind("events", new EquipmentComponentBehavior() {
            @Override public void onInstalled(EquipmentComponentContext ctx) { notifications.add("installed"); }
            @Override public void onRemoved(EquipmentComponentContext ctx) { notifications.add("removed"); }
        })) {
            helper.assertTrue(EquipmentStructureApi.install(stack, SLOT, part.instance()) == EquipmentStructureApi.InstallResult.CANCELED,
                    "event still cancels installation");
            helper.assertTrue(notifications.isEmpty(), "canceled install never notifies");
            cancel.set(false);
            EquipmentStructureApi.install(stack, SLOT, part.instance());
            cancel.set(true);
            helper.assertTrue(EquipmentStructureApi.remove(stack, SLOT).isEmpty() && notifications.size() == 1,
                    "canceled removal never notifies");
            cancel.set(false);
            EquipmentStructureApi.remove(stack, SLOT);
            helper.assertTrue(notifications.equals(List.of("installed", "removed")), "event allowance commits");
        } finally {
            NeoForge.EVENT_BUS.unregister(beforeInstall);
            NeoForge.EVENT_BUS.unregister(beforeRemove);
        }
        try (var part = bind("captured_conflict", new EquipmentComponentBehavior() {
            @Override public boolean canInstall(EquipmentComponentContext ctx) {
                EquipmentStructureApi.clearStructure(stack);
                return true;
            }
            @Override public boolean canRemove(EquipmentComponentContext ctx) {
                EquipmentStructureApi.clearStructure(stack);
                return true;
            }
            @Override public void onInstalled(EquipmentComponentContext ctx) { notifications.add("wrong"); }
            @Override public void onRemoved(EquipmentComponentContext ctx) { notifications.add("wrong"); }
        })) {
            var original = current(stack);
            helper.assertTrue(EquipmentStructureApi.install(stack, SLOT, part.instance()) == EquipmentStructureApi.InstallResult.CONFLICT,
                    "captured stack mutation cannot be overwritten");
            EquipmentStructureApi.setStructure(stack, original.withComponent(SLOT, part.instance()));
            helper.assertTrue(EquipmentStructureApi.remove(stack, SLOT).isEmpty(), "remove detects captured conflict");
            helper.assertTrue(!EquipmentStructureApi.hasStructure(stack) && notifications.size() == 2, "no resurrection or notification");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void permanentPartsAndWrongTypesCannotBeAllowedByBehavior(GameTestHelper helper) {
        ItemStack stack = equipment();
        AtomicInteger checks = new AtomicInteger();
        ResourceLocation part = id("permanent");
        EquipmentComponentRegistry.register(GameTestFixtures.component(part, TYPE, TYPE, false));
        EquipmentComponentBehaviorRegistry.register(part, new EquipmentComponentBehavior() {
            @Override public boolean canInstall(EquipmentComponentContext ctx) { checks.incrementAndGet(); return true; }
            @Override public boolean canRemove(EquipmentComponentContext ctx) { checks.incrementAndGet(); return true; }
        });
        try {
            var invalid = new EquipmentComponentInstance(part, id("wrong_type"));
            helper.assertTrue(EquipmentStructureApi.install(stack, SLOT, invalid)
                    == EquipmentStructureApi.InstallResult.INCOMPATIBLE_TYPE && checks.get() == 0, "type rules precede behavior");
            var valid = EquipmentComponentRegistry.get(part).orElseThrow().createInstance();
            EquipmentStructureApi.install(stack, SLOT, valid);
            helper.assertTrue(EquipmentStructureApi.checkRemove(stack, SLOT).status() == EquipmentStructureApi.RemoveCheckStatus.NOT_REMOVABLE,
                    "permanent remains permanent");
            helper.assertTrue(EquipmentStructureApi.remove(stack, SLOT).isEmpty() && checks.get() == 1, "behavior cannot relax definition");
        } finally {
            EquipmentComponentBehaviorRegistry.unregister(part);
            EquipmentComponentRegistry.unregister(part);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void menuRejectionsPreserveCursorInventoryAndInstalledParts(GameTestHelper helper) {
        AtomicBoolean allowInstall = new AtomicBoolean(false);
        AtomicBoolean allowRemove = new AtomicBoolean(false);
        try (var part = bind("menu", new EquipmentComponentBehavior() {
            @Override public boolean canInstall(EquipmentComponentContext ctx) { return allowInstall.get(); }
            @Override public boolean canRemove(EquipmentComponentContext ctx) { return allowRemove.get(); }
        }); var adapter = EquipmentComponentItemAdapters.register(id("menu_adapter"), Items.PAPER, part.id(),
                input -> Optional.of(new CompoundTag()))) {
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var container = new SimpleContainer(EquipmentAssemblyMenu.containerSize());
            container.setItem(EquipmentAssemblyMenu.EQUIPMENT_SLOT, equipment());
            var menu = new EquipmentAssemblyMenu(81, player.getInventory(), container, SLOTS);
            menu.selectInterface(SLOT);
            menu.setCarried(new ItemStack(Items.PAPER, 3));
            menu.clicked(EquipmentAssemblyMenu.PART_SLOT_START, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().getCount() == 3 && current(menu.equipmentStack()).components().isEmpty(), "denied cursor install");
            menu.setCarried(ItemStack.EMPTY);
            player.getInventory().setItem(9, new ItemStack(Items.PAPER, 3));
            helper.assertTrue(menu.quickMoveStack(player, EquipmentAssemblyMenu.containerSize()).isEmpty(), "denied shift install");
            helper.assertTrue(player.getInventory().getItem(9).getCount() == 3, "inventory retained");
            allowInstall.set(true);
            helper.assertTrue(!menu.quickMoveStack(player, EquipmentAssemblyMenu.containerSize()).isEmpty(), "allowed shift install");
            helper.assertTrue(player.getInventory().getItem(9).getCount() == 2, "only one consumed");
            menu.clicked(EquipmentAssemblyMenu.PART_SLOT_START, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().isEmpty() && current(menu.equipmentStack()).component(SLOT).isPresent(), "denied cursor removal");
            helper.assertTrue(menu.quickMoveStack(player, EquipmentAssemblyMenu.PART_SLOT_START).isEmpty(), "denied shift removal");
            allowRemove.set(true);
            menu.clicked(EquipmentAssemblyMenu.PART_SLOT_START, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().is(Items.PAPER) && menu.getCarried().getCount() == 1
                    && current(menu.equipmentStack()).components().isEmpty(), "one component returned");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void throwingNotificationsCannotTurnSuccessfulTransferIntoFailure(GameTestHelper helper) {
        ItemStack stack = equipment();
        List<String> order = new ArrayList<>();
        Consumer<EquipmentStructureChangedEvent> listener = event -> {
            if (event.stack() == stack) order.add(event.changeType().name());
        };
        NeoForge.EVENT_BUS.addListener(EquipmentStructureChangedEvent.class, listener);
        try (var part = bind("post_errors", new EquipmentComponentBehavior() {
            @Override public void onInstalled(EquipmentComponentContext ctx) {
                order.add("install_callback"); throw new IllegalStateException("expected notification failure");
            }
            @Override public void onRemoved(EquipmentComponentContext ctx) {
                order.add("remove_callback"); throw new IllegalStateException("expected notification failure");
            }
        })) {
            helper.assertTrue(EquipmentStructureApi.install(stack, SLOT, part.instance()) == EquipmentStructureApi.InstallResult.INSTALLED,
                    "committed install still succeeds");
            helper.assertTrue(EquipmentStructureApi.remove(stack, SLOT).isPresent(), "committed removal still returns the item");
            helper.assertTrue(order.equals(List.of("INSTALLED", "install_callback", "REMOVED", "remove_callback")), "event ordering");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void tickRequiresLiveEquipmentAndSkipsPartsRemovedDuringDispatch(GameTestHelper helper) {
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        wearer.setItemSlot(EquipmentSlot.MAINHAND, equipment());
        ItemStack live = wearer.getMainHandItem();
        List<ResourceLocation> calls = new ArrayList<>();
        AtomicInteger removeNotifications = new AtomicInteger();
        try (var part = bind("tick", new EquipmentComponentBehavior() {
            @Override public void tick(EquipmentComponentContext ctx) {
                calls.add(ctx.slot().id());
                helper.assertTrue(ctx.wearer().orElseThrow() == wearer && ctx.equipmentSlot().orElseThrow() == EquipmentSlot.MAINHAND,
                        "explicit wearer context");
                EquipmentStructureApi.tickComponents(live, wearer, EquipmentSlot.MAINHAND);
                EquipmentStructureApi.remove(live, SECOND);
            }
            @Override public void onRemoved(EquipmentComponentContext ctx) { removeNotifications.incrementAndGet(); }
        })) {
            EquipmentStructureApi.setStructure(live, current(live).withComponent(SLOT, part.instance()).withComponent(SECOND, part.instance()));
            EquipmentStructureApi.tickComponents(live.copy(), wearer, EquipmentSlot.MAINHAND);
            EquipmentStructureApi.tickComponents(live, wearer, EquipmentSlot.OFFHAND);
            helper.assertTrue(calls.isEmpty(), "copies and wrong equipment locations do not tick");
            EquipmentStructureApi.tickComponents(live, wearer, EquipmentSlot.MAINHAND);
            helper.assertTrue(calls.equals(List.of(SLOT)) && removeNotifications.get() == 1, "no recursive or removed-part ticks");
            wearer.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            EquipmentStructureApi.tickComponents(live, wearer, EquipmentSlot.MAINHAND);
            helper.assertTrue(calls.size() == 1, "unequipped hosts stop ticking");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void tickErrorsAreIsolatedAndReplacingHostStopsDispatch(GameTestHelper helper) {
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        wearer.setItemSlot(EquipmentSlot.MAINHAND, equipment());
        ItemStack live = wearer.getMainHandItem();
        List<ResourceLocation> calls = new ArrayList<>();
        AtomicBoolean replace = new AtomicBoolean(false);
        try (var part = bind("tick_errors", new EquipmentComponentBehavior() {
            @Override public void tick(EquipmentComponentContext ctx) {
                calls.add(ctx.slot().id());
                if (replace.get()) ctx.wearer().orElseThrow().setItemSlot(EquipmentSlot.MAINHAND, equipment());
                throw new IllegalStateException("expected tick failure");
            }
        })) {
            EquipmentStructureApi.setStructure(live, current(live).withComponent(SLOT, part.instance()).withComponent(SECOND, part.instance()));
            EquipmentStructureApi.tickComponents(live, wearer, EquipmentSlot.MAINHAND);
            helper.assertTrue(calls.equals(List.of(SLOT, SECOND)), "bad tick cannot stop another slot");
            calls.clear();
            EquipmentStructureApi.tickComponents(live, wearer, EquipmentSlot.MAINHAND);
            helper.assertTrue(calls.isEmpty(), "same game tick cannot retry a failed effect");
            replace.set(true);
            var secondWearer = helper.makeMockPlayer(GameType.SURVIVAL);
            secondWearer.setItemSlot(EquipmentSlot.MAINHAND, live.copy());
            EquipmentStructureApi.tickComponents(secondWearer.getMainHandItem(), secondWearer, EquipmentSlot.MAINHAND);
            helper.assertTrue(calls.equals(List.of(SLOT)), "host replacement stops stale dispatch");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void componentDataUpdatesPersistAndRefreshAttributesWithoutReinstalling(GameTestHelper helper) {
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        wearer.setItemSlot(EquipmentSlot.MAINHAND, equipment());
        ItemStack live = wearer.getMainHandItem();
        AtomicInteger lifecycle = new AtomicInteger();
        AtomicInteger updates = new AtomicInteger();
        Consumer<EquipmentStructureChangedEvent> listener = event -> {
            if (event.stack() == live && event.changeType() == EquipmentStructureChangedEvent.ChangeType.COMPONENT_DATA_UPDATED)
                updates.incrementAndGet();
        };
        NeoForge.EVENT_BUS.addListener(EquipmentStructureChangedEvent.class, listener);
        try (var part = bind("charge", new EquipmentComponentBehavior() {
            @Override public void onInstalled(EquipmentComponentContext ctx) { lifecycle.incrementAndGet(); }
            @Override public void onRemoved(EquipmentComponentContext ctx) { lifecycle.incrementAndGet(); }
            @Override public void tick(EquipmentComponentContext ctx) {
                var host = ctx.wearer().orElseThrow().getItemBySlot(ctx.equipmentSlot().orElseThrow());
                helper.assertTrue(EquipmentStructureApi.updateComponentData(host, ctx.slot().id(), ctx.component(), data -> {
                    data.putInt("charge", data.getInt("charge") + 2);
                    return data;
                }) == EquipmentStructureApi.ComponentDataResult.UPDATED, "update charge through safe API");
            }
        })) {
            EquipmentAttributeRegistry.register(part.id(), ctx -> List.of(EquipmentAttributeContribution.add(
                    Attributes.ATTACK_DAMAGE, id("charge_bonus"), ctx.component().data().getInt("charge"))));
            EquipmentStructureApi.install(live, SLOT, part.instance());
            EquipmentStructureApi.tickComponents(live, wearer, EquipmentSlot.MAINHAND);
            var savedPart = current(live).component(SLOT).orElseThrow();
            helper.assertTrue(savedPart.data().getInt("charge") == 2 && lifecycle.get() == 1 && updates.get() == 1,
                    "data update does not reinstall part");
            helper.assertTrue(EquipmentStructureApi.attributeContributions(live, SLOT).getFirst().amount() == 2,
                    "attribute resolver reads updated data");
            ItemStack restored = ItemStack.parse(helper.getLevel().registryAccess(), live.save(helper.getLevel().registryAccess())).orElseThrow();
            helper.assertTrue(current(restored).equals(current(live)), "changed data survives save/reload");
            helper.assertTrue(EquipmentStructureApi.updateComponentData(live, SLOT, savedPart, data -> data)
                    == EquipmentStructureApi.ComponentDataResult.UNCHANGED && updates.get() == 1, "no-op emits no update");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void dataUpdatesRejectStaleInstancesAndConflictingTransformers(GameTestHelper helper) {
        ItemStack stack = equipment();
        try (var part = bind("data_conflicts", new EquipmentComponentBehavior() {})) {
            EquipmentStructureApi.install(stack, SLOT, part.instance());
            var expected = current(stack).component(SLOT).orElseThrow();
            var replacement = new EquipmentComponentInstance(expected.id(), expected.componentType(), expected.interfaceType(), expected.data());
            EquipmentStructureApi.setStructure(stack, current(stack).withComponent(SLOT, replacement));
            AtomicInteger transformations = new AtomicInteger();
            helper.assertTrue(EquipmentStructureApi.updateComponentData(stack, SLOT, expected, data -> {
                transformations.incrementAndGet(); return data;
            }) == EquipmentStructureApi.ComponentDataResult.CONFLICT && transformations.get() == 0,
                    "even identical replacement cannot accept stale writes");
            var original = current(stack);
            try {
                EquipmentStructureApi.updateComponentData(stack, SLOT, replacement, data -> {
                    data.putBoolean("partial", true); throw new IllegalArgumentException("expected transformer failure");
                });
                helper.fail("transformer error must propagate");
            } catch (IllegalArgumentException expectedError) {
                helper.assertTrue(current(stack) == original, "throwing transformer cannot write partial data");
            }
            helper.assertTrue(EquipmentStructureApi.updateComponentData(stack, SLOT, replacement, data -> {
                EquipmentStructureApi.clearStructure(stack); return data;
            }) == EquipmentStructureApi.ComponentDataResult.CONFLICT, "captured live mutation detected");
            helper.assertTrue(!EquipmentStructureApi.hasStructure(stack), "no stale resurrection");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registrationConflictsAndMissingBehaviorLeaveIdentityUntouched(GameTestHelper helper) {
        ItemStack stack = equipment();
        var behavior = new EquipmentComponentBehavior() {};
        try (var part = bind("registration", behavior)) {
            EquipmentComponentBehaviorRegistry.register(part.id(), behavior);
            try {
                EquipmentComponentBehaviorRegistry.register(part.id(), new EquipmentComponentBehavior() {});
                helper.fail("conflicting registration should fail");
            } catch (IllegalStateException expected) {
                helper.assertTrue(EquipmentComponentBehaviorRegistry.get(part.id()).orElseThrow() == behavior, "original retained");
            }
            EquipmentComponentBehaviorRegistry.unregister(part.id());
            helper.assertTrue(EquipmentComponentRegistry.isRegistered(part.id()), "unregistering behavior preserves definition");
            helper.assertTrue(EquipmentStructureApi.install(stack, SLOT, part.instance()) == EquipmentStructureApi.InstallResult.INSTALLED,
                    "data-only parts install without a behavior");
            helper.assertTrue(EquipmentStructureApi.remove(stack, SLOT).isPresent(), "data-only parts remain removable");
        }
        helper.succeed();
    }

    private static ItemStack equipment() {
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        EquipmentStructureApi.setStructure(stack, new EquipmentStructure(
                id("host"), BuiltinEquipmentTypes.SWORD, SLOTS, Map.of()));
        return stack;
    }

    private static EquipmentStructure current(ItemStack stack) { return EquipmentStructureApi.structure(stack).orElseThrow(); }

    private static Part bind(String name, EquipmentComponentBehavior behavior) {
        var definition = GameTestFixtures.component(id(name), TYPE, TYPE, true, part -> new ItemStack(Items.PAPER));
        EquipmentComponentRegistry.register(definition);
        EquipmentComponentBehaviorRegistry.register(definition.id(), behavior);
        return new Part(definition.id(), definition.createInstance());
    }

    private record Part(ResourceLocation id, EquipmentComponentInstance instance) implements AutoCloseable {
        @Override public void close() {
            EquipmentAttributeRegistry.unregister(id);
            EquipmentComponentBehaviorRegistry.unregister(id);
            EquipmentComponentRegistry.unregister(id);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("equipment_structure_api", "behavior_test/" + path);
    }
}
