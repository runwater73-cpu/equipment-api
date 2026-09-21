package dev.equipmentstructure.api.smoke;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.grid.*;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import dev.equipmentstructure.api.menu.EquipmentAssemblyLayout;
import dev.equipmentstructure.api.client.EquipmentAssemblyScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Exercises the real screen, vanilla cursor sync and grid packets against a dedicated server. */
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID, value = Dist.CLIENT)
public final class SmokeClient {
    private static int age, step, wait;
    private static boolean done;
    // Isolate scripted screen calls from accidental physical input in the test window.
    @SubscribeEvent public static void mousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!done && event.getScreen() instanceof EquipmentAssemblyScreen) event.setCanceled(true);
    }
    @SubscribeEvent public static void mouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!done && event.getScreen() instanceof EquipmentAssemblyScreen) event.setCanceled(true);
    }
    @SubscribeEvent public static void keyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!done && event.getScreen() instanceof EquipmentAssemblyScreen) event.setCanceled(true);
    }
    @SubscribeEvent public static void mouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (!done && event.getScreen() instanceof EquipmentAssemblyScreen) event.setCanceled(true);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (done) return;
        var mc = Minecraft.getInstance();
        mc.getToasts().clear();
        if (++age > 2400) { fail(mc, "Timed out waiting for assembly smoke test"); return; }
        if (!(mc.screen instanceof EquipmentAssemblyScreen screen)
                || !(mc.player.containerMenu instanceof EquipmentAssemblyMenu menu)) return;
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()
                || net.minecraft.client.gui.screens.Screen.hasControlDown()
                || net.minecraft.client.gui.screens.Screen.hasAltDown()) return;
        if (++wait < 30) return;
        try {
            var structure = EquipmentStructureApi.structure(menu.equipmentStack()).orElse(null);
            var definitions = GridDefinitionSync.current();
            var board = definitions.hosts().get(BuiltinEquipmentTemplates.SWORD);
            if (board == null || board.area().area() != 81) throw new IllegalStateException("Missing synchronized 9x9 sword board");
            var view = structure == null ? null : GridTransactions.resolve(structure, definitions);
            if (view != null && view.layout().isEmpty()) throw new IllegalStateException("Invalid grid: " + view.status());
            switch (step) {
                case 0 -> {
                    shot(mc, "01-empty-sword.png");
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, EquipmentAssemblyMenu.containerSize(), 0, ClickType.PICKUP, mc.player);
                }
                case 1 -> {
                    if (menu.getCarried().isEmpty()) throw new IllegalStateException("Cursor did not receive component");
                    screen.mouseClicked(screen.getGuiLeft() + 306, screen.getGuiTop() + 37, 0);
                    screen.mouseReleased(screen.getGuiLeft() + 306, screen.getGuiTop() + 37, 0);
                }
                case 2 -> {
                    if (!menu.getCarried().isEmpty() || structure.component(BuiltinEquipmentSlots.BLADE).isEmpty())
                        throw new IllegalStateException("Sidebar install did not synchronize");
                    if (view.layout().orElseThrow().parts().get(BuiltinEquipmentSlots.BLADE).footprint().shape().area() != 3)
                        throw new IllegalStateException("Component footprint was not preserved");
                    shot(mc, "02-installed-footprint.png");
                    screen.mouseClicked(screen.getGuiLeft() + 306, screen.getGuiTop() + 37, 0);
                    screen.mouseReleased(screen.getGuiLeft() + 306, screen.getGuiTop() + 37, 0);
                }
                case 3 -> {
                    if (menu.getCarried().isEmpty() || structure.component(BuiltinEquipmentSlots.BLADE).isPresent())
                        throw new IllegalStateException("Sidebar removal did not synchronize");
                    if (screen.informationHintAt(screen.getGuiLeft() + 330, screen.getGuiTop() + 200).isPresent())
                        throw new IllegalStateException("Removed component left a clickable details panel");
                    shot(mc, "03-removed-no-stale-details.png");
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, EquipmentAssemblyMenu.containerSize(), 0, ClickType.PICKUP, mc.player);
                }
                case 4 -> {
                    if (!menu.getCarried().isEmpty()) throw new IllegalStateException("Cursor not returned to inventory");
                    click(screen, EquipmentAssemblyLayout.equipmentCoreX(), EquipmentAssemblyLayout.equipmentCoreY());
                }
                case 5 -> {
                    requireEmptyHost(menu);
                    shot(mc, "04-empty-equipment-input.png");
                    // The retired workspace center must not accept equipment or pick up an invisible slot.
                    click(screen, 192, 83);
                }
                case 6 -> {
                    requireEmptyHost(menu);
                    click(screen, EquipmentAssemblyLayout.equipmentCoreX(), EquipmentAssemblyLayout.equipmentCoreY());
                }
                case 7 -> {
                    if (!menu.equipmentStack().is(Items.IRON_SWORD) || !menu.getCarried().isEmpty())
                        throw new IllegalStateException("Visible equipment input did not accept the sword");
                    shot(mc, "05-reinserted-equipment.png");
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, EquipmentAssemblyMenu.EQUIPMENT_SLOT, 0, ClickType.QUICK_MOVE, mc.player);
                }
                case 8 -> {
                    if (!menu.equipmentStack().isEmpty() || !menu.getCarried().isEmpty())
                        throw new IllegalStateException("Shift-removing equipment failed");
                    shot(mc, "06-shift-removed-equipment.png");
                    int source = -1;
                    for (int i = EquipmentAssemblyMenu.containerSize(); i < menu.slots.size(); i++) {
                        if (menu.slots.get(i).getItem().is(Items.IRON_SWORD)) {
                            if (source >= 0) throw new IllegalStateException("Equipment was duplicated");
                            source = i;
                        }
                    }
                    if (source < 0) throw new IllegalStateException("Equipment was lost");
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, source, 0, ClickType.QUICK_MOVE, mc.player);
                }
                case 9 -> {
                    if (!menu.equipmentStack().is(Items.IRON_SWORD) || !menu.getCarried().isEmpty())
                        throw new IllegalStateException("Shift-inserting equipment failed");
                    if (mc.player.getInventory().countItem(Items.IRON_SWORD) != 0
                            || mc.player.getInventory().countItem(Items.AMETHYST_SHARD) != 1)
                        throw new IllegalStateException("Unexpected inventory after input regression");
                    EquipmentStructureApiMod.LOGGER.info("RELEASE_SMOKE_PASSED: board, component install/remove, equipment pickup/reinsert, retired center inactive, shift equipment transfer, item counts");
                    done = true; mc.stop();
                }
            }
            step++; wait = 0;
        } catch (Throwable error) { fail(mc, "step=" + step + " " + error); }
    }
    private static void requireEmptyHost(EquipmentAssemblyMenu menu) {
        if (!menu.equipmentStack().isEmpty() || !menu.getCarried().is(Items.IRON_SWORD))
            throw new IllegalStateException("Equipment pickup or retired-center isolation failed");
    }
    private static void click(EquipmentAssemblyScreen screen, int x, int y) {
        screen.mouseClicked(screen.getGuiLeft() + x, screen.getGuiTop() + y, 0);
        screen.mouseReleased(screen.getGuiLeft() + x, screen.getGuiTop() + y, 0);
    }
    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), ignored -> {});
    }
    private static void fail(Minecraft mc, String reason) {
        EquipmentStructureApiMod.LOGGER.error("RELEASE_SMOKE_FAILED: {}", reason);
        shot(mc, "failure.png"); done = true; mc.stop();
    }
}
