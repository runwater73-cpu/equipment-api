package dev.equipmentstructure.api.smoke;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.appearance.AppearancePoseStorage;
import dev.equipmentstructure.api.client.*;
import dev.equipmentstructure.api.compat.curios.CuriosSlotKey;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import net.minecraft.client.*;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.ClickType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import top.theillusivec4.curios.api.*;
import top.theillusivec4.curios.api.client.*;

@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID, value = Dist.CLIENT)
public final class CuriosSmokeClient {
    private static int age, step, wait, previewRenders, worldRenders, hiddenRenders;
    private static boolean done;
    private static final CuriosSlotKey KEY = new CuriosSlotKey("head", 0, false);
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (!Boolean.getBoolean("equipment_structure_api.curiosSmoke")) return;
        CuriosRendererRegistry.register(Items.EMERALD, () -> new ICurioRenderer() {
            @Override public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext context,
                    PoseStack poses, RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light,
                    float swing, float amount, float partial, float age, float yaw, float pitch) {
                if (context.entity().getId() == Minecraft.getInstance().player.getId()) worldRenders++; else previewRenders++;
                poses.pushPose();
                if (parent.getModel() instanceof HumanoidModel<?> humanoid) humanoid.head.translateAndRotate(poses);
                poses.translate(0, -0.55, -0.1); poses.scale(0.5F, 0.5F, 0.5F);
                Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, poses, buffers, context.entity().level(), 0);
                poses.popPose();
            }
        });
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("equipment_structure_api.curiosSmoke") || done) return;
        var mc = Minecraft.getInstance();
        if (++age > 2400) { finish(mc, "FAILED timeout"); return; }
        if (mc.player == null || ++wait < 35) return;
        try {
            if (step == 0 && mc.screen instanceof EquipmentAssemblyScreen assembly) {
                var menu = assembly.getMenu();
                if (!menu.equipmentStack().is(Items.DIAMOND_HELMET)) throw new AssertionError("automatic armor template");
                mc.gameMode.handleInventoryMouseClick(menu.containerId, EquipmentAssemblyMenu.containerSize(), 0, ClickType.QUICK_MOVE, mc.player);
            } else if (step == 1 && mc.screen instanceof EquipmentAssemblyScreen assembly) {
                if (EquipmentStructureApi.component(assembly.getMenu().equipmentStack(), KEY.slotId()).isEmpty()) throw new AssertionError("UI installation");
                shot(mc, "curios-01-installed.png"); assembly.requestPlacement(KEY.slotId());
            } else if (step == 2 && mc.screen instanceof EquipmentAppearancePlacementScreen placement) {
                if (previewRenders == 0) throw new AssertionError("native renderer did not run in preview");
                var fields = placement.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.EditBox)
                        .map(c -> (net.minecraft.client.gui.components.EditBox) c).toList();
                fields.getFirst().setValue("2");
                shot(mc, "curios-02-placement.png");
                placement.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
            } else if (step == 3 && mc.screen instanceof EquipmentAssemblyScreen assembly) {
                var part = EquipmentStructureApi.component(assembly.getMenu().equipmentStack(), KEY.slotId()).orElseThrow();
                if (AppearancePoseStorage.read(part).orElseThrow().transform().position().x() != 2) throw new AssertionError("server pose commit");
                mc.player.closeContainer(); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            } else if (step == 4 && mc.player.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET)) {
                if (worldRenders == 0) return;
                if (!CuriosApi.getCuriosInventory(mc.player).orElseThrow().getStacksHandler("head").orElseThrow().getStacks().getStackInSlot(0).is(Items.EMERALD)) throw new AssertionError("native client sync");
                shot(mc, "curios-03-equipped.png");
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new top.theillusivec4.curios.common.network.client.CPacketToggleRender("head", 0));
            } else if (step == 5) {
                if (CuriosApi.getCuriosInventory(mc.player).orElseThrow().getStacksHandler("head").orElseThrow().getRenders().get(0)) throw new AssertionError("native render flag did not synchronize off");
                hiddenRenders = worldRenders;
            } else if (step == 6) {
                if (worldRenders != hiddenRenders) throw new AssertionError("hidden native model still renders");
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new top.theillusivec4.curios.common.network.client.CPacketToggleRender("head", 0));
            } else if (step == 7) {
                if (worldRenders <= hiddenRenders) throw new AssertionError("native rendering did not resume");
                finish(mc, "PASSED UI install, native preview, pose packet, equip, native menu isolation, slot metadata, sync, render-toggle off/on and world renderer"); return;
            } else return;
            step++; wait = 0;
        } catch (Throwable error) { finish(mc, "FAILED step=" + step + " " + error); }
    }
    private static void shot(Minecraft mc, String name) { Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), ignored -> {}); }
    private static void finish(Minecraft mc, String result) {
        EquipmentStructureApiMod.LOGGER.info("CURIOS_SMOKE_{}", result); done = true; mc.stop();
    }
}
