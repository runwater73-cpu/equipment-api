package dev.equipmentstructure.api.smoke;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.grid.*;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import dev.equipmentstructure.api.client.EquipmentAssemblyScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.inventory.ClickType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Exercises the real screen, vanilla cursor sync and grid packets against a dedicated server. */
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID, value = Dist.CLIENT)
public final class SmokeClient {
    private static int age, step, wait;
    private static boolean done;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (done) return;
        var mc = Minecraft.getInstance();
        if (++age > 2400) { fail(mc, "Timed out waiting for assembly smoke test"); return; }
        if (!(mc.screen instanceof EquipmentAssemblyScreen screen)
                || !(mc.player.containerMenu instanceof EquipmentAssemblyMenu menu)) return;
        if (++wait < 30) return;
        try {
            var structure = EquipmentStructureApi.structure(menu.equipmentStack()).orElseThrow();
            var definitions = GridDefinitionSync.current();
            var board = definitions.hosts().get(BuiltinEquipmentTemplates.SWORD);
            if (board == null || board.area().area() != 81) throw new IllegalStateException("Missing synchronized 9x9 sword board");
            var view = GridTransactions.resolve(structure, definitions);
            if (view.layout().isEmpty()) throw new IllegalStateException("Invalid grid: " + view.status());
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
                    EquipmentStructureApiMod.LOGGER.info("RELEASE_SMOKE_PASSED: synchronized sword board, install, footprint, remove, stale selection, item return");
                    done = true; mc.stop();
                }
            }
            step++; wait = 0;
        } catch (Throwable error) { fail(mc, error.toString()); }
    }
    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), ignored -> {});
    }
    private static void fail(Minecraft mc, String reason) {
        EquipmentStructureApiMod.LOGGER.error("RELEASE_SMOKE_FAILED: {}", reason);
        shot(mc, "failure.png"); done = true; mc.stop();
    }
}
