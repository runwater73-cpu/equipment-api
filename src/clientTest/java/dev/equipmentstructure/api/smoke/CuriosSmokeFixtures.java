package dev.equipmentstructure.api.smoke;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.compat.curios.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class CuriosSmokeFixtures {
    private static ServerPlayer player;
    private static int ticks;
    private static boolean opened, equipped;
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!Boolean.getBoolean("equipment_structure_api.curiosSmoke") || !(event.getEntity() instanceof ServerPlayer p)) return;
        player = p; ticks = 0; opened = false; equipped = false;
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getInventory().clearContent(); p.getInventory().selected = 0;
        p.getInventory().setItem(0, new ItemStack(Items.DIAMOND_HELMET));
        p.getInventory().setItem(9, new ItemStack(Items.EMERALD));
        p.serverLevel().setDayTime(6000);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (player == null || player.hasDisconnected()) return;
        if (++ticks == 40) {
            var inv = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).orElseThrow();
            int visible = inv.getVisibleSlots();
            var nativeMenu = new top.theillusivec4.curios.common.inventory.container.CuriosContainer(91, player.getInventory());
            if (nativeMenu.slots.stream().anyMatch(s -> s instanceof top.theillusivec4.curios.common.inventory.CurioSlot c && c.getIdentifier().equals("head")))
                throw new AssertionError("managed slots leaked into original Curios menu");
            if (visible != inv.getVisibleSlots() || !inv.getStacksHandler("head").orElseThrow().isVisible())
                throw new AssertionError("native metadata modified by menu visibility");
            if (!dev.equipmentstructure.api.command.EquipmentStructureCommands.openForPlayer(player)) throw new AssertionError("auto helmet template");
            opened = true;
        }
        if (opened && !equipped && ticks > 80 && !(player.containerMenu instanceof dev.equipmentstructure.api.menu.EquipmentAssemblyMenu)) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                var armor = player.getInventory().getItem(i);
                if (!armor.is(Items.DIAMOND_HELMET)) continue;
                if (EquipmentStructureApi.component(armor, new CuriosSlotKey("head", 0, false).slotId()).isEmpty()) throw new AssertionError("lost accessory");
                player.getInventory().setItem(i, ItemStack.EMPTY);
                player.setItemSlot(EquipmentSlot.HEAD, armor);
                equipped = true; break;
            }
        }
    }
}
