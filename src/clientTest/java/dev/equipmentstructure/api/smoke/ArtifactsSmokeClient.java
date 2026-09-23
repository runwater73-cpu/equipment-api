package dev.equipmentstructure.api.smoke;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.appearance.AppearancePoseStorage;
import dev.equipmentstructure.api.client.*;
import dev.equipmentstructure.api.compat.curios.CuriosSlotKey;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.inventory.ClickType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import top.theillusivec4.curios.api.CuriosApi;

/** Real screen and network test, independent of the generic sword fixture. */
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID, value = Dist.CLIENT)
public final class ArtifactsSmokeClient {
    private static final CuriosSlotKey KEY = new CuriosSlotKey("head", 0, false);
    private static int age, wait, stage, screenAge;
    private static boolean done, serverPassed;
    @SubscribeEvent public static void chat(ClientChatReceivedEvent.System event) {
        if (!Boolean.getBoolean("equipment_structure_api.artifactsSmoke")) return;
        var message = event.getMessage().getString();
        if (message.startsWith(ArtifactsSmokeFixtures.PREFIX + "FAILED")) finish(message);
        else if (message.startsWith(ArtifactsSmokeFixtures.PREFIX + "PASSED")) serverPassed = true;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("equipment_structure_api.artifactsSmoke") || done) return;
        var mc = Minecraft.getInstance();
        if (++age > 3300) { finish("FAILED client timeout stage=" + stage); return; }
        if (mc.player == null || ++wait < 30) return;
        mc.getToasts().clear();
        try {
            if (stage == 0 && mc.screen instanceof EquipmentAssemblyScreen screen) {
                if (screen.getMenu().equipmentStack().isEmpty() || ++screenAge < 10) return;
                var equipmentTab = screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button b
                        && b.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("gui.equipment_structure_api.curios.equipment_tab").getString()))
                        .map(c -> (net.minecraft.client.gui.components.Button)c).findFirst().orElseThrow();
                if (equipmentTab.active) throw new AssertionError("initial equipment tab after container sync");
                var menu = screen.getMenu();
                mc.gameMode.handleInventoryMouseClick(menu.containerId, EquipmentAssemblyMenu.containerSize(), 0, ClickType.QUICK_MOVE, mc.player);
            } else if (stage == 1 && mc.screen instanceof EquipmentAssemblyScreen screen) {
                var menu = screen.getMenu();
                if (EquipmentStructureApi.component(menu.equipmentStack(), KEY.slotId()).isEmpty()) throw new AssertionError("real UI installation");
                shot("artifacts-01-installed.png"); screen.requestPlacement(KEY.slotId());
            } else if (stage == 2 && mc.screen instanceof EquipmentAppearancePlacementScreen screen) {
                ArtifactsSmokeRenderProbe.verify(screen.getMenu().equipmentStack());
                shot("artifacts-02-native-model.png");
                var fields = screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.EditBox)
                        .map(c -> (net.minecraft.client.gui.components.EditBox)c).toList();
                fields.getFirst().setValue("2");
            } else if (stage == 3 && mc.screen instanceof EquipmentAppearancePlacementScreen screen) {
                shot("artifacts-03-adjusted-model.png"); screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
            } else if (stage == 4 && mc.screen instanceof EquipmentAssemblyScreen screen) {
                var part = EquipmentStructureApi.component(screen.getMenu().equipmentStack(), KEY.slotId()).orElseThrow();
                if (AppearancePoseStorage.read(part).orElseThrow().transform().position().x() != 2) throw new AssertionError("saved client pose");
                mc.player.closeContainer();
            } else if (stage == 5 && serverPassed) {
                if (CuriosApi.getCuriosInventory(mc.player).orElseThrow().findCurios(ArtifactsSmokeFixtures.item("snorkel")).size() != 1) return;
                finish("PASSED client UI insertion, real addon mesh, pose network and native client query"); return;
            } else return;
            stage++; wait = 0;
        } catch (Throwable failure) {
            EquipmentStructureApiMod.LOGGER.error("ARTIFACTS_SMOKE_CLIENT_FAILURE", failure);
            finish("FAILED client stage=" + stage + " " + failure);
        }
    }
    private static void shot(String name) { var mc = Minecraft.getInstance(); Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), ignored -> {}); }
    private static void finish(String result) { done = true; EquipmentStructureApiMod.LOGGER.info("ARTIFACTS_SMOKE_{}", result); Minecraft.getInstance().stop(); }
}
