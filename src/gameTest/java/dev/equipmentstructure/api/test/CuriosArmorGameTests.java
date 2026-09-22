package dev.equipmentstructure.api.test;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.compat.curios.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.*;
import top.theillusivec4.curios.api.CuriosApi;

@GameTestHolder(EquipmentStructureApiMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CuriosArmorGameTests {
    @GameTest(template = "empty")
    public static void privateNativeSlotsAreNotAutomaticallyTakenOver(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var inv = CuriosApi.getCuriosInventory(player).orElseThrow();
        var key = new CuriosSlotKey("esa_private", 0, false);
        helper.assertTrue(!CuriosArmorCompat.manages(CuriosArmorCompat.context(player, key)), "invisible native slots require explicit routing");
        inv.getStacksHandler(key.type()).orElseThrow().getStacks().setStackInSlot(0, new ItemStack(Items.APPLE));
        helper.assertTrue(inv.getStacksHandler(key.type()).orElseThrow().getStacks().getStackInSlot(0).is(Items.APPLE), "private native inventory remains usable by its addon");
        var armor = new ItemStack(Items.DIAMOND_CHESTPLATE); CuriosEquipmentTemplates.prepare(armor, player);
        helper.assertTrue(EquipmentStructureApi.structure(armor).orElseThrow().slot(key.slotId()).isEmpty(), "hidden private type is not exposed in armor templates");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void creativeCloneUsesSlotExtensionAndRejectsSurvival(GameTestHelper helper) {
        var extensions = new top.theillusivec4.curios.api.extensions.RegisterCuriosExtensionsEvent();
        if (!extensions.isSlotExtensionRegistered(KEY.type())) extensions.registerSlotExtension(new top.theillusivec4.curios.api.extensions.ICurioSlotExtension() {
            @Override public ItemStack getCloneStack(top.theillusivec4.curios.api.SlotContext context, ItemStack original) {
                return original.isEmpty() ? ItemStack.EMPTY : new ItemStack(Items.APPLE);
            }
        }, KEY.type());
        var player = helper.makeMockPlayer(GameType.CREATIVE); player.getAbilities().instabuild = true;
        var armor = install(player);
        var container = new net.minecraft.world.SimpleContainer(dev.equipmentstructure.api.menu.EquipmentAssemblyMenu.containerSize());
        container.setItem(0, armor);
        var menu = new dev.equipmentstructure.api.menu.EquipmentAssemblyMenu(88, player.getInventory(), container, EquipmentStructureApi.slots(armor));
        player.containerMenu = menu;
        helper.assertTrue(new CuriosSlotClonePayload(88, menu.getStateId(), KEY.slotId(), false).apply(player), "creative slot clone is handled");
        helper.assertTrue(menu.getCarried().is(Items.APPLE) && menu.getCarried().getCount() == 64, "original extension supplies creative clone result");
        helper.assertTrue(EquipmentStructureApi.component(armor, KEY.slotId()).isPresent(), "clone leaves installed item unchanged");
        var survival = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(!new CuriosSlotClonePayload(88, menu.getStateId(), KEY.slotId(), false).apply(survival), "survival cannot invoke creative extension");
        helper.succeed();
    }
    private static final java.util.Map<net.minecraft.world.entity.LivingEntity, int[]> TRACES = new java.util.IdentityHashMap<>();
    private static boolean traceRegistered;
    private static void registerTrace() {
        if (traceRegistered) return;
        traceRegistered = true;
        var behavior = new top.theillusivec4.curios.api.type.capability.ICurioItem() {
            @Override public void onEquip(top.theillusivec4.curios.api.SlotContext c, ItemStack previous, ItemStack current) {
                if (TRACES.containsKey(c.entity())) TRACES.get(c.entity())[0]++;
            }
            @Override public void onUnequip(top.theillusivec4.curios.api.SlotContext c, ItemStack next, ItemStack current) {
                if (TRACES.containsKey(c.entity())) TRACES.get(c.entity())[1]++;
            }
            @Override public void curioTick(top.theillusivec4.curios.api.SlotContext c, ItemStack current) {
                if (TRACES.containsKey(c.entity())) TRACES.get(c.entity())[2]++;
            }
            @Override public void onEquipFromUse(top.theillusivec4.curios.api.SlotContext c, ItemStack current) {
                if (TRACES.containsKey(c.entity())) TRACES.get(c.entity())[3]++;
            }
            @Override public com.google.common.collect.Multimap<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>, net.minecraft.world.entity.ai.attributes.AttributeModifier>
                    getAttributeModifiers(top.theillusivec4.curios.api.SlotContext c, net.minecraft.resources.ResourceLocation id, ItemStack current) {
                var result = com.google.common.collect.HashMultimap.<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>, net.minecraft.world.entity.ai.attributes.AttributeModifier>create();
                if (TRACES.containsKey(c.entity())) result.put(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, 2, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
                return result;
            }
        };
        CuriosApi.registerCurio(Items.EMERALD, behavior); CuriosApi.registerCurio(Items.DIAMOND, behavior);
    }
    private static void nativeTick(net.minecraft.world.entity.player.Player player) {
        new top.theillusivec4.curios.common.event.CuriosEventHandler().tick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(player));
    }
    @GameTest(template = "empty")
    public static void nativeCallbacksAttributesAndCosmeticsKeepTheirRoles(GameTestHelper helper) {
        registerTrace();
        var player = helper.makeMockPlayer(GameType.SURVIVAL); var trace = new int[4]; TRACES.put(player, trace);
        try {
            var armor = install(player);
            double base = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            var cosmetic = new CuriosSlotKey(KEY.type(), 0, true);
            var part = EquipmentSlotItemAdapters.read(new ItemStack(Items.EMERALD), armor, cosmetic.slotId(), player).orElseThrow();
            EquipmentStructureApi.install(armor, cosmetic.slotId(), part);
            nativeTick(player); nativeTick(player);
            helper.assertTrue(trace[0] == 1 && trace[2] == 2, "one functional onEquip, one tick per native tick; cosmetics add neither");
            helper.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) == base + 2, "native attribute applied once");
            var h = CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler(KEY.type()).orElseThrow();
            h.getActiveStates().set(0, false); nativeTick(player);
            helper.assertTrue(trace[1] == 1 && trace[2] == 2 && player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) == base, "native disable removes effects and pauses ticks");
            h.getActiveStates().set(0, true); nativeTick(player);
            helper.assertTrue(trace[0] == 2 && trace[2] == 3, "native re-enable restores callback lifecycle");
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); nativeTick(player);
            helper.assertTrue(trace[1] == 2 && player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) == base, "armor removal invokes native unequip once");
            helper.assertTrue(EquipmentStructureApi.structure(armor).orElseThrow().components().size() == 2, "both functional and cosmetic ownership retained");
        } finally { TRACES.remove(player); }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void bindingUiInvokesOriginalEquipFromUseOnce(GameTestHelper helper) {
        registerTrace(); var player = helper.makeMockPlayer(GameType.SURVIVAL); var trace = new int[4]; TRACES.put(player, trace);
        try {
            var source = new ItemStack(Items.DIAMOND, 2);
            helper.assertTrue(CuriosBindingActions.quickInstall(player, source), "binding quick install");
            helper.assertTrue(source.getCount() == 1 && trace[3] == 1, "one source and one native use callback");
            nativeTick(player); nativeTick(player);
            helper.assertTrue(trace[0] == 1 && trace[2] == 2 && trace[3] == 1, "native tick owns equip and effect callbacks without replaying use");
        } finally { TRACES.remove(player); }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void nativeValidationAndMenuVisibilityRemainIndependent(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var inv = CuriosApi.getCuriosInventory(player).orElseThrow();
        var handler = inv.getStacksHandler(KEY.type()).orElseThrow();
        var ctx = CuriosArmorCompat.context(player, KEY);
        helper.assertTrue(CuriosApi.isStackValid(ctx, new ItemStack(Items.EMERALD)) && handler.getStacks().isItemValid(0, new ItemStack(Items.EMERALD)), "native validators remain usable by addons");
        int visible = inv.getVisibleSlots();
        // The real-client smoke checks CuriosContainer; its native constructor requires a connected ServerPlayer.
        helper.assertTrue(handler.isVisible() && inv.getVisibleSlots() == visible && visible > 0, "native capability visibility remains true");
        java.util.function.Consumer<top.theillusivec4.curios.api.event.CurioCanEquipEvent> deny = event -> {
            if (event.getSlotContext().entity() == player) event.setEquipResult(net.neoforged.neoforge.common.util.TriState.FALSE);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny);
        try {
            var armor = new ItemStack(Items.DIAMOND_HELMET); CuriosEquipmentTemplates.prepare(armor, player);
            helper.assertTrue(EquipmentSlotItemAdapters.read(new ItemStack(Items.EMERALD), armor, KEY.slotId(), player).isEmpty(), "original equip event veto governs assembly");
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny); }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void nativeBackupRestoresArmorAndRecoversWithoutArmor(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var armor = install(player); var inv = CuriosApi.getCuriosInventory(player).orElseThrow();
        nativeInventory(player).getStackInSlot(0);
        var saved = inv.saveInventory(true);
        helper.assertTrue(EquipmentStructureApi.component(armor, KEY.slotId()).isEmpty(), "clear backup transfers ownership out of equipment");
        inv.loadInventory(saved);
        helper.assertTrue(nativeInventory(player).getStackInSlot(0).is(Items.EMERALD), "restore returns to the original native index");
        inv.loadInventory(saved);
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "reloading matching snapshot cannot duplicate existing owner");
        var second = inv.saveInventory(true); player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        inv.loadInventory(second);
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 1 && EquipmentStructureApi.component(armor, KEY.slotId()).isEmpty(), "restore without host recovers the item once");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void ordinaryNativeTransformationReplacesSavedItem(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL); var armor = install(player);
        EquipmentStructureApi.setAppearancePresentations(armor, java.util.Map.of(KEY.slotId(),
                new dev.equipmentstructure.api.appearance.AppearancePartPresentation(dev.equipmentstructure.api.appearance.AppearancePose.IDENTITY, false)), true);
        var inv = CuriosApi.getCuriosInventory(player).orElseThrow(); inv.findCurios(Items.EMERALD);
        nativeInventory(player).setStackInSlot(0, new ItemStack(Items.AMETHYST_SHARD));
        helper.assertTrue(inv.findCurios(Items.EMERALD).isEmpty() && inv.findCurios(Items.AMETHYST_SHARD).size() == 1, "native queries observe transformed item in the same tick");
        var saved = armor.copy(); player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); nativeInventory(player).getStackInSlot(0);
        player.setItemSlot(EquipmentSlot.HEAD, saved);
        helper.assertTrue(nativeInventory(player).getStackInSlot(0).is(Items.AMETHYST_SHARD), "transformation survives owner copy and re-equip");
        helper.assertTrue(!dev.equipmentstructure.api.appearance.AppearancePartPresentation.read(EquipmentStructureApi.component(saved, KEY.slotId()).orElseThrow()).visible(),
                "native transformation preserves the user's appearance settings");
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "consumed old item is not returned");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void nativeInactiveQueriesAndRenderFlagsRemainAuthoritative(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL); var armor = install(player);
        var inv = CuriosApi.getCuriosInventory(player).orElseThrow(); var h = inv.getStacksHandler(KEY.type()).orElseThrow();
        h.getActiveStates().set(0, false); h.updateActiveState(0);
        helper.assertTrue(inv.findCurios(stack -> stack.is(Items.EMERALD), false, "esa_inactive").isEmpty(), "ordinary native query excludes inactive slots");
        helper.assertTrue(inv.findCurios(stack -> stack.is(Items.EMERALD), true, "esa_including_inactive").size() == 1, "includeInactive retains access to stored item");
        h.getRenders().set(0, false);
        helper.assertTrue(!CuriosArmorCompat.context(player, KEY).visible(), "assembly uses the native render context");
        h.getActiveStates().set(0, true); h.updateActiveState(0);
        helper.assertTrue(inv.findCurios(stack -> stack.is(Items.EMERALD), false, "esa_reactivated").size() == 1, "native reactivation resumes item visibility");
        helper.assertTrue(EquipmentStructureApi.component(armor, KEY.slotId()).isPresent(), "active/render switches never remove ownership");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void nativeBindingTransformationAndDestructionKeepSingleOwnership(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var inventory = CuriosApi.getCuriosInventory(player).orElseThrow();
        helper.assertTrue(PlayerBoundCurios.install(player, KEY, new ItemStack(Items.DIAMOND)), "initial player binding");
        inventory.findCurios(Items.DIAMOND); // Prime native same-tick queries before an addon transforms it.
        nativeInventory(player).setStackInSlot(0, new ItemStack(Items.EMERALD));
        helper.assertTrue(PlayerBoundCurios.item(player, KEY.slotId()).is(Items.EMERALD), "undeclared replacement inherits player ownership");
        helper.assertTrue(inventory.findCurios(Items.DIAMOND).isEmpty(), "old item query invalidates in the same tick");
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "replacement must not silently eject into inventory");
        helper.assertTrue(PlayerBoundCurios.remove(player, KEY.slotId()).is(Items.EMERALD), "replacement's own removable behavior applies");
        PlayerBoundCurios.install(player, KEY, new ItemStack(Items.DIAMOND));
        nativeInventory(player).setStackInSlot(0, ItemStack.EMPTY);
        helper.assertTrue(PlayerBoundCurios.item(player, KEY.slotId()).isEmpty()
                && inventory.findCurios(Items.DIAMOND).isEmpty(), "native destruction removes binding and query result");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void bindingPacketRejectsStaleAndReplayedTransactions(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var menu = new dev.equipmentstructure.api.menu.EquipmentAssemblyMenu(7, player.getInventory(),
                new net.minecraft.world.SimpleContainer(dev.equipmentstructure.api.menu.EquipmentAssemblyMenu.containerSize()), java.util.List.of());
        player.containerMenu = menu;
        menu.setCarried(new ItemStack(Items.DIAMOND, 2));
        var stale = new CuriosBindingActionPayload(7, menu.getStateId() + 1, KEY, false, menu.getCarried());
        helper.assertTrue(!CuriosBindingActions.act(menu, stale, player) && menu.getCarried().getCount() == 2, "stale state cannot consume cursor");
        var request = new CuriosBindingActionPayload(7, menu.getStateId(), KEY, false, menu.getCarried());
        helper.assertTrue(CuriosBindingActions.act(menu, request, player), "valid direct panel install");
        helper.assertTrue(menu.getCarried().getCount() == 1 && !PlayerBoundCurios.item(player, KEY.slotId()).isEmpty(), "one source unit transfers");
        helper.assertTrue(!CuriosBindingActions.act(menu, request, player) && menu.getCarried().getCount() == 1, "replay cannot transfer twice");
        player.containerMenu = player.inventoryMenu;
        helper.assertTrue(!CuriosBindingActions.act(menu, new CuriosBindingActionPayload(7, menu.getStateId(), KEY, true, menu.getCarried()), player), "closed menu rejects actions");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void multipleBindingsShareNativeCapacityAndRejectTransientSlots(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var handler = CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler(KEY.type()).orElseThrow();
        handler.grow(1); // Native permanent grant, not a second private binding inventory.
        var second = new CuriosSlotKey(KEY.type(), 1, false);
        helper.assertTrue(PlayerBoundCurios.install(player, KEY, new ItemStack(Items.DIAMOND)), "first binding needs no armor");
        helper.assertTrue(PlayerBoundCurios.install(player, second, new ItemStack(Items.DIAMOND)), "second simultaneous binding");
        helper.assertTrue(!PlayerBoundCurios.install(player, KEY, new ItemStack(Items.DIAMOND)), "no overwrite or duplicate capacity");
        var temporary = net.minecraft.resources.ResourceLocation.parse("equipment_structure_api:temporary_binding_test");
        handler.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(temporary, 1,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        helper.assertTrue(handler.getSlots() == 3, "native transient grant still exists");
        helper.assertTrue(!PlayerBoundCurios.install(player, new CuriosSlotKey(KEY.type(), 2, false), new ItemStack(Items.DIAMOND)),
                "temporary slot must not provide an armor-removal escape from permanent binding");
        handler.removeModifier(temporary);
        helper.assertTrue(handler.getSlots() == 2 && !PlayerBoundCurios.item(player, second.slotId()).isEmpty(), "losing temporary capacity preserves both bindings");
        helper.assertTrue(CuriosBindingActions.keys(player).size() == 2, "panel enumerates both stable slots");
        helper.assertTrue(!PlayerBoundCurios.install(player, KEY, new ItemStack(Items.EMERALD)), "ordinary accessory rejected by binding panel");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void dynamicOverflowTransfersOnceAndKeepsTargetTemplate(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var inventory = CuriosApi.getCuriosInventory(player).orElseThrow();
        var handler = inventory.getStacksHandler("esa_unknown_socket").orElseThrow();
        var modifier = net.minecraft.resources.ResourceLocation.parse("equipment_structure_api:cross_equipment_grant");
        handler.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(modifier, 1,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        var chest = new ItemStack(Items.IRON_CHESTPLATE); CuriosEquipmentTemplates.prepare(chest, player);
        var key = new CuriosSlotKey("esa_unknown_socket", 1, false);
        var part = EquipmentSlotItemAdapters.read(new ItemStack(Items.AMETHYST_SHARD), chest, key.slotId(), player).orElseThrow();
        EquipmentStructureApi.install(chest, key.slotId(), part); player.setItemSlot(EquipmentSlot.CHEST, chest);
        helper.assertTrue(handler.getStacks().getStackInSlot(1).is(Items.AMETHYST_SHARD), "expanded destination slot activates");
        handler.removeModifier(modifier);
        helper.assertTrue(handler.getSlots() == 1, "native capacity shrinks");
        inventory.handleInvalidStacks(); inventory.handleInvalidStacks();
        helper.assertTrue(EquipmentStructureApi.component(chest, key.slotId()).isEmpty(), "overflow relinquishes equipment ownership");
        helper.assertTrue(player.getInventory().countItem(Items.AMETHYST_SHARD) == 1, "exactly one native overflow return");
        helper.assertTrue(dev.equipmentstructure.api.grid.GridTransactions.resolve(EquipmentStructureApi.structure(chest).orElseThrow(),
                dev.equipmentstructure.api.grid.GridDefinitions.registered()).allowed(), "native overflow refreshes the surviving grid fingerprint");
        helper.assertTrue(!CuriosEquipmentTemplates.active(chest, player, key), "stale UI slot cannot accept items");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void fiveSlotsStayOnOneTemplateInStableOrder(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var nativeHandler = CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler("esa_unknown_socket").orElseThrow();
        nativeHandler.grow(4);
        var chest = new ItemStack(Items.IRON_CHESTPLATE);
        CuriosEquipmentTemplates.prepare(chest, player);
        var before = EquipmentStructureApi.slots(chest).stream().map(EquipmentSlotDefinition::id).toList();
        helper.assertTrue(before.stream().filter(id -> CuriosSlotKey.parse(id).map(k -> k.type().equals("esa_unknown_socket") && !k.cosmetic()).orElse(false)).count() == 5,
                "all five native slots belong to chest armor");
        for (var item : java.util.List.of(Items.IRON_HELMET, Items.IRON_LEGGINGS, Items.IRON_BOOTS)) {
            var other = new ItemStack(item); CuriosEquipmentTemplates.prepare(other, player);
            helper.assertTrue(EquipmentStructureApi.slots(other).stream().noneMatch(s -> CuriosSlotKey.parse(s.id()).map(k -> k.type().equals("esa_unknown_socket")).orElse(false)),
                    "same native type must never be spread across other armor templates");
        }
        var last = new CuriosSlotKey("esa_unknown_socket", 4, false).slotId();
        var part = EquipmentSlotItemAdapters.read(new ItemStack(Items.AMETHYST_SHARD), chest, last, player).orElseThrow();
        helper.assertTrue(EquipmentStructureApi.install(chest, last, part) == EquipmentStructureApi.InstallResult.INSTALLED, "install last slot");
        CuriosEquipmentTemplates.prepare(chest, player);
        helper.assertTrue(before.equals(EquipmentStructureApi.slots(chest).stream().map(EquipmentSlotDefinition::id).toList()), "filled slots must not jump to the front");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void explicitPlayerBindingTransfersOwnershipOnce(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var armor = new ItemStack(Items.DIAMOND_HELMET); CuriosEquipmentTemplates.prepare(armor, player);
        var part = EquipmentSlotItemAdapters.read(new ItemStack(Items.DIAMOND), armor, KEY.slotId(), player).orElseThrow();
        EquipmentStructureApi.install(armor, KEY.slotId(), part);
        player.setItemSlot(EquipmentSlot.HEAD, armor);
        helper.assertTrue(nativeInventory(player).getStackInSlot(0).is(Items.DIAMOND), "first activation binds item");
        helper.assertTrue(EquipmentStructureApi.component(armor, KEY.slotId()).isEmpty(), "armor must surrender ownership");
        helper.assertTrue(dev.equipmentstructure.api.grid.GridTransactions.resolve(EquipmentStructureApi.structure(armor).orElseThrow(),
                dev.equipmentstructure.api.grid.GridDefinitions.registered()).allowed(), "binding transfer keeps the armor grid usable");
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        helper.assertTrue(nativeInventory(player).getStackInSlot(0).is(Items.DIAMOND), "bound item remains active while naked");
        armor.setDamageValue(armor.getMaxDamage() - 1); armor.hurtAndBreak(2, player, EquipmentSlot.HEAD);
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0, "armor break cannot create a second bound item");
        var saved = CuriosApi.getCuriosInventory(player).orElseThrow().writeTag();
        CuriosArmorCompat.beginNativeLoad();
        try { CuriosApi.getCuriosInventory(player).orElseThrow().readTag(saved); }
        finally { CuriosArmorCompat.endNativeLoad(); }
        helper.assertTrue(PlayerBoundCurios.item(player, KEY.slotId()).is(Items.DIAMOND), "native save preserves player binding");
        java.util.function.Consumer<top.theillusivec4.curios.api.event.CurioCanUnequipEvent> deny = event -> {
            if (event.getEntity() == player) event.setUnequipResult(net.neoforged.neoforge.common.util.TriState.FALSE);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny);
        try { helper.assertTrue(PlayerBoundCurios.remove(player, KEY.slotId()).isEmpty(), "original unequip veto still wins"); }
        finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny); }
        var released = PlayerBoundCurios.remove(player, KEY.slotId());
        helper.assertTrue(released.is(Items.DIAMOND) && !PlayerBoundCurios.bound(released, player), "allowed removal clears ownership marker");
        helper.assertTrue(nativeInventory(player).getStackInSlot(0).isEmpty(), "release leaves no second copy");
        helper.succeed();
    }
    private static final CuriosSlotKey KEY = new CuriosSlotKey("esa_test_head", 0, false);
    private static ItemStack install(net.minecraft.world.entity.player.Player player) {
        var armor = new ItemStack(Items.DIAMOND_HELMET);
        CuriosEquipmentTemplates.prepare(armor, player);
        var part = EquipmentSlotItemAdapters.read(new ItemStack(Items.EMERALD), armor, KEY.slotId(), player).orElseThrow();
        if (EquipmentStructureApi.install(armor, KEY.slotId(), part) != EquipmentStructureApi.InstallResult.INSTALLED)
            throw new AssertionError("fixture install failed");
        player.setItemSlot(EquipmentSlot.HEAD, armor);
        return armor;
    }
    private static top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler nativeInventory(net.minecraft.world.entity.player.Player player) {
        return CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler(KEY.type()).orElseThrow().getStacks();
    }
    @EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
            if (ModList.get().isLoaded("curios")) event.register(CuriosArmorGameTests.class);
        }
    }
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void originalCuriosInventoryReadsArmor(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var armor = new ItemStack(Items.DIAMOND_HELMET);
        CuriosEquipmentTemplates.prepare(armor, player);
        var key = new CuriosSlotKey("esa_test_head", 0, false);
        helper.assertTrue(EquipmentStructureApi.slots(armor).stream().anyMatch(s -> s.id().equals(key.slotId())), "automatic armor template must expose the custom Curios slot");
        helper.assertTrue(EquipmentStructureApi.slots(armor).stream().noneMatch(s -> BuiltinEquipmentSlots.all().contains(s.id())), "Curios armor must not retain our preset installation points");
        var part = EquipmentSlotItemAdapters.read(new ItemStack(Items.EMERALD), armor, key.slotId(), player).orElseThrow();
        helper.assertTrue(EquipmentStructureApi.install(armor, key.slotId(), part) == EquipmentStructureApi.InstallResult.INSTALLED, "original third-party item must install with default geometry");
        player.setItemSlot(EquipmentSlot.HEAD, armor);
        var inventory = CuriosApi.getCuriosInventory(player).orElseThrow();
        helper.assertTrue(inventory.getStacksHandler("esa_test_head").orElseThrow().getStacks().getStackInSlot(0).is(Items.EMERALD), "native inventory must expose the item inside armor");
        helper.assertTrue(inventory.getStacksHandler("esa_test_head").orElseThrow().isVisible(), "native visibility metadata must remain unchanged");
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        helper.assertTrue(inventory.getStacksHandler("esa_test_head").orElseThrow().getStacks().getStackInSlot(0).isEmpty(), "unequipped armor must stop exposing the curio");
        helper.assertTrue(EquipmentStructureApi.component(armor, key.slotId()).isPresent(), "unequipping must preserve armor contents");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void mutableItemIsFlushedBeforeArmorCopy(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var armor = install(player);
        var live = nativeInventory(player).getStackInSlot(0);
        live.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("mutated"));
        var copy = armor.copy();
        var restored = EquipmentStructureApi.component(copy, KEY.slotId()).flatMap(EquipmentComponentRegistry::createValidatedItemStack).orElseThrow();
        helper.assertTrue(restored.getHoverName().getString().equals("mutated"), "copy must contain latest third-party mutations without recursion");
        helper.assertTrue(nativeInventory(player).getStackInSlot(0) == live, "live reference must remain stable after flush");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void nativeTransferAndEntitySaveHaveOneOwner(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var armor = install(player);
        var inventory = nativeInventory(player);
        helper.assertTrue(inventory.extractItem(0, 1, false).is(Items.EMERALD), "native extraction returns the original item");
        helper.assertTrue(EquipmentStructureApi.component(armor, KEY.slotId()).isEmpty(), "extraction removes nested storage atomically");
        helper.assertTrue(inventory.insertItem(0, new ItemStack(Items.EMERALD, 3), false).getCount() == 3, "native insertion must not bypass equipment UI");
        var part = EquipmentSlotItemAdapters.read(new ItemStack(Items.EMERALD), armor, KEY.slotId(), player).orElseThrow();
        EquipmentStructureApi.install(armor, KEY.slotId(), part);
        inventory.getStackInSlot(0);
        var saved = (net.minecraft.nbt.CompoundTag) CuriosApi.getCuriosInventory(player).orElseThrow().writeTag();
        for (var element : saved.getList("Curios", 10)) {
            var entry = (net.minecraft.nbt.CompoundTag) element;
            if (entry.getString("Identifier").equals(KEY.type()))
                helper.assertTrue(entry.getCompound("StacksHandler").getCompound("Stacks").getList("Items", 10).isEmpty(), "entity save must not duplicate armor-owned item");
        }
        helper.assertTrue(EquipmentStructureApi.component(armor, KEY.slotId()).isPresent(), "entity serialization preserves armor owner");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void nativeRebuildDoesNotClearArmor(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var armor = install(player);
        nativeInventory(player).getStackInSlot(0);
        var inventory = CuriosApi.getCuriosInventory(player).orElseThrow();
        CuriosArmorCompat.beginNativeLoad();
        try {
            var tag = inventory.writeTag();
            nativeInventory(player).setStackInSlot(0, ItemStack.EMPTY);
            inventory.readTag(tag);
        } finally { CuriosArmorCompat.endNativeLoad(); }
        helper.assertTrue(EquipmentStructureApi.component(armor, KEY.slotId()).isPresent(), "native rebuild must preserve nested contents");
        helper.assertTrue(nativeInventory(player).getStackInSlot(0).is(Items.EMERALD), "rebuilt native inventory must rebind armor");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void defaultDeathKeepsItemNestedInArmorDrop(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var armor = install(player);
        CuriosArmorLifecycle.freeze(player);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        var drops = new java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity>();
        drops.add(new net.minecraft.world.entity.item.ItemEntity(player.level(), 0, 0, 0, armor));
        var event = new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(player, player.damageSources().generic(), drops, false);
        new top.theillusivec4.curios.common.event.CuriosEventHandler().playerDrops(event);
        CuriosArmorLifecycle.thaw(player);
        helper.assertTrue(drops.size() == 1 && EquipmentStructureApi.component(armor, KEY.slotId()).isPresent(), "default death drops only armor with its nested accessory");
        helper.assertTrue(nativeInventory(player).getStackInSlot(0).isEmpty(), "dead wearer must no longer own the nested item");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void brokenArmorReturnsAccessory(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var armor = install(player);
        nativeInventory(player).getStackInSlot(0);
        armor.setDamageValue(armor.getMaxDamage() - 1);
        armor.hurtAndBreak(2, player, EquipmentSlot.HEAD);
        helper.assertTrue(armor.isEmpty(), "fixture armor must break");
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 1, "armor break must recover exactly one accessory");
        helper.assertTrue(nativeInventory(player).getStackInSlot(0).isEmpty(), "broken armor must stop supplying native item");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void rightClickDoesNotEquipOrCancelItemUse(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var armor = install(player);
        nativeInventory(player).extractItem(0, 1, false);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD));
        var event = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        new top.theillusivec4.curios.common.event.CuriosEventHandler().curioRightClick(event);
        helper.assertTrue(!event.isCanceled(), "integration must preserve the accessory's other right-click actions");
        helper.assertTrue(player.getMainHandItem().getCount() == 1 && EquipmentStructureApi.component(armor, KEY.slotId()).isEmpty(), "right-click must not consume or equip the accessory");
        helper.assertTrue(nativeInventory(player).isItemValid(0, player.getMainHandItem()), "read-only validity remains native while insertion is separately blocked");
        nativeInventory(player).setStackInSlot(0, player.getMainHandItem().copy());
        helper.assertTrue(nativeInventory(player).getStackInSlot(0).isEmpty(), "direct quick-equip setter cannot install into an empty managed slot");
        var valid = EquipmentSlotItemAdapters.read(player.getMainHandItem(), armor, KEY.slotId(), player);
        helper.assertTrue(valid.isPresent(), "equipment interface still validates the same accessory using original Curios rules");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void cachedQueriesFollowSameTickArmorSwap(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        install(player);
        var inventory = CuriosApi.getCuriosInventory(player).orElseThrow();
        helper.assertTrue(inventory.findFirstCurio(Items.EMERALD).isPresent(), "fixture query");
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        helper.assertTrue(inventory.findFirstCurio(Items.EMERALD).isEmpty(), "same-tick query cache must not keep removed armor effects");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void explicitDropRulesPreserveSingleOwnership(GameTestHelper helper) {
        for (var rule : java.util.List.of(top.theillusivec4.curios.api.type.capability.ICurio.DropRule.ALWAYS_KEEP,
                top.theillusivec4.curios.api.type.capability.ICurio.DropRule.ALWAYS_DROP,
                top.theillusivec4.curios.api.type.capability.ICurio.DropRule.DESTROY)) {
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var armor = install(player);
            CuriosArmorLifecycle.freeze(player);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            var drops = new java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity>();
            drops.add(new net.minecraft.world.entity.item.ItemEntity(player.level(), 0, 0, 0, armor));
            var event = new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(player, player.damageSources().generic(), drops, false);
            java.util.function.Consumer<top.theillusivec4.curios.api.event.DropRulesEvent> override = e -> {
                if (e.getEntity() == player) e.addOverride(item -> item.is(Items.EMERALD), rule);
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(override);
            try { new top.theillusivec4.curios.common.event.CuriosEventHandler().playerDrops(event); }
            finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(override); CuriosArmorLifecycle.thaw(player); }
            helper.assertTrue(EquipmentStructureApi.component(armor, KEY.slotId()).isEmpty(), "explicit death rule detaches the armor owner: " + rule);
            helper.assertTrue(drops.size() == (rule == top.theillusivec4.curios.api.type.capability.ICurio.DropRule.ALWAYS_DROP ? 2 : 1), "native loose drop count: " + rule);
            if (rule == top.theillusivec4.curios.api.type.capability.ICurio.DropRule.ALWAYS_KEEP) {
                var clone = helper.makeMockPlayer(GameType.SURVIVAL);
                CuriosApi.getCuriosInventory(clone).orElseThrow().readTag(CuriosApi.getCuriosInventory(player).orElseThrow().writeTag());
                nativeInventory(clone).getStackInSlot(0);
                helper.assertTrue(clone.getInventory().countItem(Items.EMERALD) == 1, "native clone keeps one item for reinstallation through our UI");
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void cosmeticShrinkRecoversOnlyOneItem(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var armor = install(player);
        var key = new CuriosSlotKey(KEY.type(), 0, true);
        var part = EquipmentSlotItemAdapters.read(new ItemStack(Items.EMERALD), armor, key.slotId(), player).orElseThrow();
        helper.assertTrue(EquipmentStructureApi.install(armor, key.slotId(), part) == EquipmentStructureApi.InstallResult.INSTALLED, "cosmetic install");
        var h = CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler(KEY.type()).orElseThrow();
        h.getCosmeticStacks().getStackInSlot(0);
        h.shrink(1);
        h.getSlots();
        helper.assertTrue(EquipmentStructureApi.component(armor, key.slotId()).isEmpty(), "cosmetic overflow must leave armor storage");
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 1, "cosmetic overflow is recovered once; native functional overflow remains in Curios queue");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void itemFootprintOverridesSlotDefault(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var armor = new ItemStack(Items.DIAMOND_HELMET); CuriosEquipmentTemplates.prepare(armor, player);
        var emerald = EquipmentSlotItemAdapters.read(new ItemStack(Items.EMERALD), armor, KEY.slotId(), player).orElseThrow();
        var diamond = EquipmentSlotItemAdapters.read(new ItemStack(Items.DIAMOND), armor, KEY.slotId(), player).orElseThrow();
        helper.assertTrue(EquipmentComponentRegistry.get(emerald.id()).orElseThrow().footprint().shape().area() == 1, "slot fallback is one cell");
        helper.assertTrue(EquipmentComponentRegistry.get(diamond.id()).orElseThrow().footprint().shape().area() == 2, "individual item geometry wins over slot geometry");
        helper.assertTrue(EquipmentSlotItemAdapters.read(new ItemStack(Items.STONE), armor, KEY.slotId(), player).isEmpty(), "native slot validator remains authoritative");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void unknownThirdPartySlotGetsRecoverableFallback(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var armor = new ItemStack(Items.IRON_CHESTPLATE); CuriosEquipmentTemplates.prepare(armor, player);
        var key = new CuriosSlotKey("esa_unknown_socket", 0, false);
        var part = EquipmentSlotItemAdapters.read(new ItemStack(Items.AMETHYST_SHARD), armor, key.slotId(), player).orElseThrow();
        helper.assertTrue(EquipmentStructureApi.install(armor, key.slotId(), part) == EquipmentStructureApi.InstallResult.INSTALLED, "unmapped third-party types default to chest armor");
        helper.assertTrue(EquipmentComponentRegistry.createValidatedItemStack(part).orElseThrow().is(Items.AMETHYST_SHARD), "third-party accessory round trip");
        helper.succeed();
    }
}
