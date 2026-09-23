package dev.equipmentstructure.api.smoke;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.appearance.*;
import dev.equipmentstructure.api.client.EquipmentAppearancePlacementScreen;
import dev.equipmentstructure.api.client.appearance.AppearancePreviewSelection;
import dev.equipmentstructure.api.compat.curios.CuriosSlotKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.*;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.client.render.CuriosLayer;

/** Runs the actual mixed-in world layer on an isolated, unticked wearer. Never packaged. */
final class CuriosDisplaySmokeProbe {
    static void verify() {
        var mc = Minecraft.getInstance();
        var armor = ((EquipmentAppearancePlacementScreen)mc.screen).getMenu().equipmentStack().copy();
        var key = new CuriosSlotKey("charm", 0, false);
        var original = EquipmentStructureApi.structure(armor).orElseThrow();
        var part = original.component(key.slotId()).orElseThrow();
        var structure = new EquipmentStructure(original.hostId(), original.equipmentType(), original.slots(),
                java.util.Map.of(key.slotId(), java.util.List.of(part)), original.version(), original.grid());
        EquipmentStructureApi.setStructure(armor, structure);
        var wearer = new RemotePlayer(mc.level, mc.player.getGameProfile());
        wearer.setItemSlot(EquipmentSlot.CHEST, armor);
        var inventory = CuriosApi.getCuriosInventory(wearer).orElseThrow(); inventory.reset();
        var handler = inventory.getStacksHandler(key.type()).orElseThrow();
        handler.getStacks().setStackInSlot(0, EquipmentComponentRegistry.createItemStack(part).orElseThrow());
        var model = new PlayerModel<AbstractClientPlayer>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        model.setupAnim(wearer, 0, 0, 0, 0, 0);
        var parent = new RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>() {
            public PlayerModel<AbstractClientPlayer> getModel() { return model; }
            public ResourceLocation getTextureLocation(AbstractClientPlayer entity) { return entity.getSkin().texture(); }
        };
        var layer = new CuriosLayer<>(parent);
        java.util.function.Supplier<java.util.Optional<AppearancePreviewSelection.Bounds>> render = () -> {
            var capture = new AppearancePreviewSelection(mc.renderBuffers().bufferSource(), key.slotId());
            var poses = new PoseStack(); poses.scale(100, 100, 100);
            layer.render(poses, capture.forPart(key.slotId()), 15728880, wearer, 0, 0, 0, 0, 0, 0);
            mc.renderBuffers().bufferSource().endBatch();
            return capture.bounds(key.slotId());
        };
        var before = render.get().orElseThrow(() -> new AssertionError("world fallback missing"));
        var pose = new AppearancePose(2, 0, 0, AppearanceRotation.IDENTITY, 1);
        EquipmentStructureApi.setStructure(armor, structure.withComponent(key.slotId(), AppearancePoseStorage.with(part, pose)));
        var after = render.get().orElseThrow();
        if (Math.abs(after.centerX() - before.centerX() - 12.5) > .01) throw new AssertionError("world pose differs from editor convention");
        EquipmentStructureApi.setStructure(armor, structure.withComponent(key.slotId(), new AppearancePartPresentation(pose, false).apply(part)));
        if (render.get().isPresent()) throw new AssertionError("hidden component renders in world");
        EquipmentStructureApi.setStructure(armor, structure);
        handler.getRenders().set(0, false);
        if (render.get().isPresent()) throw new AssertionError("native render off ignored");
        handler.getRenders().set(0, true);
        if (render.get().isEmpty()) throw new AssertionError("native render on ignored");
        wearer.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        if (render.get().isPresent()) throw new AssertionError("fallback leaked to unmanaged player item");
        EquipmentStructureApiMod.LOGGER.info("CURIOS_DISPLAY_WORLD_PASSED fallback, pose, visibility, native render toggle, owner guard");
    }
}
