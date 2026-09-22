package dev.equipmentstructure.api.smoke;

import dev.equipmentstructure.api.*;
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

@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID, value = Dist.CLIENT)
public final class EnigmaticSmokeClient {
    private static int age, step, wait;
    private static boolean done;
    private static net.minecraft.client.gui.screens.Screen previousScreen;
    @SubscribeEvent public static void chat(ClientChatReceivedEvent.System event) {
        if (!Boolean.getBoolean("equipment_structure_api.enigmaticSmoke")) return;
        var message = event.getMessage().getString();
        if (message.startsWith(EnigmaticSmokeFixtures.PREFIX)) finish(message);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("equipment_structure_api.enigmaticSmoke") || done) return;
        var mc = Minecraft.getInstance(); mc.getToasts().clear();
        if (++age > 2400) { finish("FAILED client timeout step=" + step); return; }
        if (mc.screen != previousScreen) { previousScreen = mc.screen; wait = 0; }
        if (mc.player == null || ++wait < 30) return;
        try {
            if (mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen) { mc.player.respawn(); mc.setScreen(null); wait = 0; return; }
            if (Boolean.getBoolean("equipment_structure_api.unbindSmoke")) {
                if (mc.screen instanceof EquipmentAssemblyScreen assembly) {
                    if (!dev.equipmentstructure.api.compat.curios.PlayerBoundCurios.item(mc.player, new CuriosSlotKey("ring", 0, false).slotId()).isEmpty()) throw new AssertionError("removed binding must be empty on client");
                    shot("enigmatic-07-after-survival-unbinding.png"); mc.player.closeContainer();
                }
                return;
            }
            if (Boolean.getBoolean("equipment_structure_api.boundResume")) {
                if (step == 0 && mc.screen instanceof EquipmentAssemblyScreen assembly) {
                    var menu = assembly.getMenu();
                    var key = new CuriosSlotKey("ring", 0, false).slotId();
                    if (dev.equipmentstructure.api.compat.curios.PlayerBoundCurios.item(mc.player, key).isEmpty()) throw new AssertionError("reloaded bound item client sync");
                    shot("eternal-covenant-final.png");
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.equipmentstructure.api.compat.curios.CuriosBindingActionPayload(
                            menu.containerId, menu.getStateId(), new CuriosSlotKey("ring", 0, false), true, menu.getCarried()));
                    step++;
                }
                return;
            }
            if (Boolean.getBoolean("equipment_structure_api.bindingSmoke")) {
                if (mc.screen instanceof EquipmentAssemblyScreen assembly) {
                    var menu = assembly.getMenu();
                    if (step < 2) mc.gameMode.handleInventoryMouseClick(menu.containerId, EquipmentAssemblyMenu.containerSize() + step, 0, ClickType.QUICK_MOVE, mc.player);
                    else if (step == 2) {
                        if (!menu.equipmentStack().isEmpty()) throw new AssertionError("personal panel must work without equipment");
                        if (dev.equipmentstructure.api.compat.curios.PlayerBoundCurios.item(mc.player, new CuriosSlotKey("ring", 1, false).slotId()).isEmpty()) throw new AssertionError("two bindings synchronized");
                        shot("enigmatic-05-two-player-bindings.png"); mc.player.closeContainer();
                    } else if (step == 3) { shot("enigmatic-06-binding-after-death.png"); mc.player.closeContainer(); }
                    else return;
                    step++; wait = 0;
                }
                return;
            }
            if (Boolean.getBoolean("equipment_structure_api.enigmaticResume")) {
                if (mc.screen instanceof EquipmentAssemblyScreen assembly) {
                    var menu = assembly.getMenu();
                    if (step == 0) mc.gameMode.handleInventoryMouseClick(menu.containerId, EquipmentAssemblyMenu.containerSize(), 0, ClickType.QUICK_MOVE, mc.player);
                    else if (step == 1) {
                        if (dev.equipmentstructure.api.compat.curios.PlayerBoundCurios.item(mc.player, new CuriosSlotKey("ring", 0, false).slotId()).isEmpty()) throw new AssertionError("curse UI player installation");
                        mc.player.closeContainer();
                    } else if (step == 2) {
                        if (EquipmentSlotItemAdapters.playerOwnedItem(menu.equipmentStack(), new CuriosSlotKey("ring", 0, false).slotId(), mc.player).isEmpty()) throw new AssertionError("bound sidebar item synced after death");
                        shot("enigmatic-04-player-bound.png"); mc.player.closeContainer();
                    } else return;
                    step++; wait = 0;
                }
                return;
            }
            if (step < 5 && mc.screen instanceof EquipmentAssemblyScreen assembly) {
                var menu = assembly.getMenu();
                mc.gameMode.handleInventoryMouseClick(menu.containerId, EquipmentAssemblyMenu.containerSize() + step, 0, ClickType.QUICK_MOVE, mc.player);
            } else if (step == 5 && mc.screen instanceof EquipmentAssemblyScreen assembly) {
                if (EquipmentStructureApi.structure(assembly.getMenu().equipmentStack()).orElseThrow().components().size() != 5) throw new AssertionError("UI five accessory installation");
                shot("enigmatic-01-five-accessories.png");
                assembly.requestPlacement(new CuriosSlotKey("back", 0, false).slotId());
            } else if (step == 6 && mc.screen instanceof EquipmentAppearancePlacementScreen editor) {
                var fields = editor.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.EditBox).map(c -> (net.minecraft.client.gui.components.EditBox)c).toList();
                fields.getFirst().setValue("2"); shot("enigmatic-02-editor.png");
                editor.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
            } else if (step == 7 && mc.screen instanceof EquipmentAssemblyScreen) {
                mc.player.closeContainer(); mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            } else if ((step == 8 || step == 9) && mc.screen instanceof EquipmentAssemblyScreen assembly) {
                if (EquipmentStructureApi.slots(assembly.getMenu().equipmentStack()).stream().noneMatch(s -> s.id().equals(new CuriosSlotKey("spellstone", 0, false).slotId()))) throw new AssertionError("unlocked slot synchronized");
                mc.gameMode.handleInventoryMouseClick(assembly.getMenu().containerId, EquipmentAssemblyMenu.containerSize() + step - 8, 0, ClickType.QUICK_MOVE, mc.player);
            } else if (step == 10 && mc.screen instanceof EquipmentAssemblyScreen assembly) {
                if (EquipmentStructureApi.structure(assembly.getMenu().equipmentStack()).orElseThrow().components().size() != 7) throw new AssertionError("UI seven accessory installation");
                shot("enigmatic-03-unlocked-slots.png"); mc.player.closeContainer();
            } else return;
            step++; wait = 0;
        } catch (Throwable error) { finish("FAILED client step=" + step + " " + error); }
    }
    private static void shot(String name) { var mc = Minecraft.getInstance(); Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), ignored -> {}); }
    private static void finish(String result) {
        done = true; EquipmentStructureApiMod.LOGGER.info("ENIGMATIC_CLIENT_{}", result); shot("enigmatic-final.png"); Minecraft.getInstance().stop();
    }
}
