package dev.equipmentstructure.api.test;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.event.EquipmentBreakEvent;
import dev.equipmentstructure.api.grid.GridFootprint;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(EquipmentStructureApiMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class EquipmentBreakGameTests {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "break_test");
    private static EquipmentComponentItemAdapters.Registration binding;
    @SubscribeEvent public static void register(RegisterGameTestsEvent event) { event.register(EquipmentBreakGameTests.class); }
    private static ItemStack equipment(boolean factory) {
        var builder = EquipmentComponentDefinition.builder(ID, ID, ID).suitableFor(BuiltinEquipmentTypes.SWORD)
                .footprint(GridFootprint.SINGLE_CELL).removable(false);
        if (factory) builder.itemFactory(part -> new ItemStack(Items.FLINT));
        var definition = builder.build(); EquipmentComponentRegistry.register(definition);
        if (factory) binding = EquipmentComponentItemAdapters.register(ID, Items.FLINT, ID, stack -> Optional.of(new net.minecraft.nbt.CompoundTag()));
        var sword = new ItemStack(Items.IRON_SWORD);
        EquipmentStructureApi.setStructure(sword, new EquipmentStructure(BuiltinEquipmentTemplates.SWORD,
                BuiltinEquipmentTypes.SWORD, List.of(new EquipmentSlotDefinition(ID, ID)), Map.of(ID, List.of(definition.createInstance()))));
        sword.setDamageValue(sword.getMaxDamage() - 1);
        return sword;
    }
    private static void cleanup() { if (binding != null) { binding.close(); binding = null; } EquipmentComponentRegistry.unregister(ID); }
    @GameTest(template = "empty") public static void ordinaryLockedPartReturnsOnBreak(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var sword = equipment(true);
        try {
            sword.hurtAndBreak(2, player, EquipmentSlot.MAINHAND);
            helper.assertTrue(sword.isEmpty() && player.getInventory().countItem(Items.FLINT) == 1, "durability break returns one ordinary part despite manual removal lock");
        } finally { cleanup(); }
        helper.succeed();
    }
    @GameTest(template = "empty") public static void missingFactoryPreservesWholeHost(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var sword = equipment(false);
        try {
            sword.hurtAndBreak(2, player, EquipmentSlot.MAINHAND);
            helper.assertTrue(!sword.isEmpty() && sword.getDamageValue() == sword.getMaxDamage() - 1
                    && EquipmentStructureApi.component(sword, ID).isPresent(), "unrecoverable part must preserve the complete host");
        } finally { cleanup(); }
        helper.succeed();
    }
    @GameTest(template = "empty") public static void breakPolicyCanDestroyOrCancel(GameTestHelper helper) {
        for (boolean cancel : List.of(false, true)) {
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var sword = equipment(true);
            java.util.function.Consumer<EquipmentBreakEvent> policy = event -> {
                if (event.equipment() != sword) return;
                if (cancel) event.setCanceled(true); else event.setAction(ID, EquipmentBreakEvent.Action.DESTROY);
            };
            NeoForge.EVENT_BUS.addListener(policy);
            try {
                sword.hurtAndBreak(2, player, EquipmentSlot.MAINHAND);
                helper.assertTrue(sword.isEmpty() != cancel && player.getInventory().countItem(Items.FLINT) == 0, "author break policy must be atomic");
            } finally { NeoForge.EVENT_BUS.unregister(policy); cleanup(); }
        }
        helper.succeed();
    }
    @GameTest(template = "empty") public static void fullInventoryDropsPartOnce(GameTestHelper helper) {
        var drops = new ArrayList<net.minecraft.world.entity.item.ItemEntity>();
        // GameTestHelper's basic Player does not implement ServerPlayer's drop-spawning override.
        var player = new net.minecraft.world.entity.player.Player(helper.getLevel(), net.minecraft.core.BlockPos.ZERO, 0,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "break-test")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
            @Override public net.minecraft.world.entity.item.ItemEntity drop(ItemStack stack, boolean random, boolean trace) {
                var drop = super.drop(stack, random, trace); if (drop != null) drops.add(drop); return drop;
            }
        };
        for (int i = 0; i < player.getInventory().items.size(); i++) player.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
        var sword = equipment(true);
        try {
            sword.hurtAndBreak(2, player, EquipmentSlot.MAINHAND);
            helper.assertTrue(sword.isEmpty() && drops.size() == 1 && drops.getFirst().getItem().is(Items.FLINT), "full inventory must produce exactly one returned part drop");
        } finally { cleanup(); }
        helper.succeed();
    }
}
