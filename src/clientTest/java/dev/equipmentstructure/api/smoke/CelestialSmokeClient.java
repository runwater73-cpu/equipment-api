package dev.equipmentstructure.api.smoke;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.client.EquipmentAssemblyScreen;
import dev.equipmentstructure.api.compat.curios.PlayerBoundCurios;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID, value = Dist.CLIENT)
public final class CelestialSmokeClient {
    private static int age, wait, stage;
    private static boolean done, serverPassed;
    @SubscribeEvent public static void chat(ClientChatReceivedEvent.System event) {
        if (!Boolean.getBoolean("equipment_structure_api.celestialSmoke")) return;
        var message = event.getMessage().getString();
        if (message.startsWith(CelestialSmokeFixtures.PREFIX + "FAILED")) finish(message);
        else if (message.startsWith(CelestialSmokeFixtures.PREFIX + "PASSED")) serverPassed = true;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("equipment_structure_api.celestialSmoke") || done) return;
        var mc = Minecraft.getInstance();
        if (++age > 3800) { finish("FAILED client timeout stage=" + stage); return; }
        if (mc.player == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen) { mc.player.respawn(); mc.setScreen(null); wait = 0; return; }
        if (++wait < 35) return;
        mc.getToasts().clear();
        try {
            if (stage == 0 && mc.screen instanceof EquipmentAssemblyScreen screen) {
                mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, EquipmentAssemblyMenu.containerSize(), 0, ClickType.QUICK_MOVE, mc.player);
            } else if (stage == 1 && mc.screen instanceof EquipmentAssemblyScreen) {
                CelestialSmokeFixtures.check(!PlayerBoundCurios.item(mc.player, CelestialSmokeFixtures.BOUND.slotId()).isEmpty(), "real empty-hand UI binds scroll");
                shot("celestial-01-covenant.png"); mc.player.closeContainer();
            } else if (stage == 2 && mc.player.containerMenu.getClass().getName().contains("PandoraEditMenu")) {
                var menu = mc.player.containerMenu;
                var slot = menu.slots.stream().filter(s -> s.container == mc.player.getInventory() && s.getContainerSlot() == 9).findFirst().orElseThrow();
                mc.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.QUICK_MOVE, mc.player);
            } else if (stage == 3 && mc.player.containerMenu.getClass().getName().contains("PandoraEditMenu")) {
                CelestialSmokeFixtures.check(mc.player.containerMenu.slots.stream().anyMatch(s -> s.container != mc.player.getInventory()
                        && s.getItem().is(CelestialSmokeFixtures.item("curseofpandora:charm_of_health"))), "native Pandora GUI synchronizes nested item");
                shot("celestial-02-pandora.png"); mc.player.closeContainer();
            } else if (stage == 4 && mc.player.containerMenu.getClass().getName().contains("CuriosListMenu")) {
                CelestialSmokeFixtures.check(((dev.xkmc.l2tabs.compat.common.CuriosListMenu)mc.player.containerMenu).curios.getSize() == 0, "native L2 GUI has no duplicate managed slots");
                shot("celestial-03-l2-slots.png");
                var ctor = dev.xkmc.l2tabs.tabs.contents.AttributeScreen.class.getDeclaredConstructor(Component.class, int.class);
                ctor.setAccessible(true); mc.setScreen(ctor.newInstance(Component.literal("玩家属性"), 0));
            } else if (stage == 5 && mc.screen instanceof dev.xkmc.l2tabs.tabs.contents.AttributeScreen) {
                shot("celestial-04-attributes.png");
                var ctor = dev.xkmc.l2hostility.content.menu.tab.DifficultyScreen.class.getDeclaredConstructor(Component.class);
                ctor.setAccessible(true); mc.setScreen(ctor.newInstance(Component.literal("莱特兰难度")));
            } else if (stage == 6 && mc.screen instanceof dev.xkmc.l2hostility.content.menu.tab.DifficultyScreen) {
                shot("celestial-05-difficulty.png"); mc.player.closeContainer(); mc.setScreen(null);
            } else if (stage == 7 && serverPassed) {
                finish("PASSED native binding UI, Pandora nested inventory, L2 filtered menu, attributes, difficulty, respawn"); return;
            } else return;
            stage++; wait = 0;
        } catch (Throwable failure) {
            EquipmentStructureApiMod.LOGGER.error("CELESTIAL_SMOKE_CLIENT_FAILURE", failure); finish("FAILED stage=" + stage + " " + failure);
        }
    }
    private static void shot(String name) { var mc = Minecraft.getInstance(); Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), ignored -> {}); }
    private static void finish(String result) { done = true; EquipmentStructureApiMod.LOGGER.info("CELESTIAL_SMOKE_{}", result); Minecraft.getInstance().stop(); }
}
