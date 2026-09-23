package dev.equipmentstructure.api.smoke;

import artifacts.item.WearableArtifactItem;
import artifacts.neoforge.integration.curios.CuriosRenderingHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.appearance.*;
import dev.equipmentstructure.api.client.appearance.AppearancePreviewSelection;
import dev.equipmentstructure.api.compat.curios.*;
import dev.equipmentstructure.api.compat.curios.client.CuriosArmorPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.*;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.client.render.CuriosLayer;
import java.util.*;

/** Captures actual third-party mesh output, not just renderer registration. */
final class ArtifactsSmokeRenderProbe {
    static void verify(ItemStack originalHost) {
        var mc = Minecraft.getInstance();
        int count = 0;
        for (var item : BuiltInRegistries.ITEM) {
            if (!(item instanceof WearableArtifactItem)) continue;
            check(CuriosRendererRegistry.getRenderer(item).isPresent(), "registered renderer " + item);
            String type = ArtifactsSmokeFixtures.TYPES.stream().filter(t -> CuriosApi.isStackValid(
                    CuriosArmorCompat.context(mc.player, new CuriosSlotKey(t, 0, false)), new ItemStack(item))).findFirst().orElseThrow();
            for (int index = 0; index < (type.equals("hands") ? 2 : 1); index++) {
                verifyItem(originalHost, item, new CuriosSlotKey(type, index, false)); count++;
            }
        }
        EquipmentStructureApiMod.LOGGER.info("ARTIFACTS_RENDER_PASSED {} item/index combinations: preview/world vertices, native pose, translation and visibility", count);
    }
    private static void verifyItem(ItemStack originalHost, Item item, CuriosSlotKey key) {
        var mc = Minecraft.getInstance();
        var renderer = (net.minecraft.client.renderer.entity.player.PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(mc.player);
        var nativeModel = renderer.getModel();
        var backup = new PlayerModel<AbstractClientPlayer>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        nativeModel.copyPropertiesTo(backup);
        try {
            // First-person play can leave the shared third-person renderer uninitialized or in another pose.
            nativeModel.young = true; nativeModel.head.y = 16;
            verifyItemWithModel(originalHost, item, key, nativeModel);
        } finally { backup.copyPropertiesTo(nativeModel); }
    }
    private static void verifyItemWithModel(ItemStack originalHost, Item item, CuriosSlotKey key, PlayerModel<AbstractClientPlayer> nativeModel) {
        var mc = Minecraft.getInstance();
        var position = CuriosArmorCompat.definitions(mc.player).profile(key.type()).armorSlot();
        var armor = new ItemStack(switch (position) {
            case HEAD -> Items.DIAMOND_HELMET; case LEGS -> Items.DIAMOND_LEGGINGS;
            case FEET -> Items.DIAMOND_BOOTS; default -> Items.DIAMOND_CHESTPLATE;
        });
        var original = EquipmentStructureApi.structure(originalHost).orElseThrow();
        var data = new CompoundTag();
        data.put("equipment_structure_api:curios_item", new ItemStack(item).save(mc.level.registryAccess()));
        data.putString("equipment_structure_api:curios_type", key.type());
        var part = EquipmentComponentRegistry.get(key.defaultComponent()).orElseThrow().createInstance(data);
        verifyAttributes(item, key, part);
        var structure = new EquipmentStructure(original.hostId(), original.equipmentType(),
                List.of(new EquipmentSlotDefinition(key.slotId(), key.interfaceId())), Map.of(key.slotId(), List.of(part)), original.version(), original.grid());
        EquipmentStructureApi.setStructure(armor, structure);
        var preview = new CuriosArmorPreview();
        var capture = new AppearancePreviewSelection(mc.renderBuffers().bufferSource(), key.slotId());
        var poses = new PoseStack(); poses.scale(100, 100, 100);
        preview.render(armor, position, poses, capture, 15728880, (slot, frame) -> {});
        mc.renderBuffers().bufferSource().endBatch();
        var before = capture.bounds(key.slotId()).orElseThrow(() -> new AssertionError("preview mesh " + item + " " + key));
        check(nativeModel.young && nativeModel.head.y == 16, "preview leaves shared renderer untouched");
        var pose = new AppearancePose(2, 3, 4, AppearanceRotation.IDENTITY, 1);
        EquipmentStructureApi.setStructure(armor, structure.withComponent(key.slotId(), AppearancePoseStorage.with(part, pose)));
        var moved = new AppearancePreviewSelection(mc.renderBuffers().bufferSource(), key.slotId());
        preview.render(armor, position, poses, moved, 15728880, (slot, frame) -> {});
        mc.renderBuffers().bufferSource().endBatch();
        var after = moved.bounds(key.slotId()).orElseThrow();
        check(Math.abs(after.centerX() - before.centerX() - 12.5) < .05, "preview translation " + item);
        check(Math.abs(after.centerY() - before.centerY() + 18.75) < .05, "preview Y translation " + item);
        var sidePoses = new PoseStack(); sidePoses.scale(100, 100, 100);
        sidePoses.mulPose(new org.joml.Quaternionf().rotationY((float)Math.PI / 2));
        var sideMoved = new AppearancePreviewSelection(mc.renderBuffers().bufferSource(), key.slotId());
        preview.render(armor, position, sidePoses, sideMoved, 15728880, (slot, frame) -> {});
        mc.renderBuffers().bufferSource().endBatch();
        EquipmentStructureApi.setStructure(armor, structure);
        var sideBefore = new AppearancePreviewSelection(mc.renderBuffers().bufferSource(), key.slotId());
        preview.render(armor, position, sidePoses, sideBefore, 15728880, (slot, frame) -> {});
        mc.renderBuffers().bufferSource().endBatch();
        check(Math.abs(sideMoved.bounds(key.slotId()).orElseThrow().centerX() - sideBefore.bounds(key.slotId()).orElseThrow().centerX() + 25) < .05, "preview Z translation " + item);

        var wearer = new RemotePlayer(mc.level, mc.player.getGameProfile());
        wearer.setItemSlot(position, armor);
        var inv = CuriosApi.getCuriosInventory(wearer).orElseThrow(); inv.reset();
        var h = inv.getStacksHandler(key.type()).orElseThrow();
        h.getStacks().setStackInSlot(key.index(), new ItemStack(item));
        var model = new PlayerModel<AbstractClientPlayer>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        model.young = false;
        model.setupAnim(wearer, 0, 0, 0, 0, 0);
        model.copyPropertiesTo(nativeModel); // Vanilla configures its actual wearer model before world layers.
        var parent = new RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>() {
            public PlayerModel<AbstractClientPlayer> getModel() { return model; }
            public ResourceLocation getTextureLocation(AbstractClientPlayer player) { return player.getSkin().texture(); }
        };
        var layer = new CuriosLayer<>(parent);
        java.util.function.Supplier<Optional<AppearancePreviewSelection.Bounds>> render = () -> {
            var output = new AppearancePreviewSelection(mc.renderBuffers().bufferSource(), key.slotId());
            layer.render(poses, output.forPart(key.slotId()), 15728880, wearer, 0, 0, 0, 0, 0, 0);
            mc.renderBuffers().bufferSource().endBatch(); return output.bounds(key.slotId());
        };
        var world = render.get().orElseThrow(() -> new AssertionError("world mesh " + item));
        check(Math.abs(world.centerX() - before.centerX()) < .05, "native world/editor alignment " + item);
        check(Math.abs(world.centerY() - before.centerY()) < .05, "native world/editor Y alignment " + item);
        EquipmentStructureApi.setStructure(armor, structure.withComponent(key.slotId(), AppearancePoseStorage.with(part, pose)));
        var worldMoved = render.get().orElseThrow();
        check(Math.abs(worldMoved.centerX() - world.centerX() - 12.5) < .05, "world translation " + item);
        EquipmentStructureApi.setStructure(armor, structure.withComponent(key.slotId(), new AppearancePartPresentation(pose, false).apply(part)));
        check(render.get().isEmpty(), "component visibility " + item);
        EquipmentStructureApi.setStructure(armor, structure);
        h.getRenders().set(key.index(), false); check(render.get().isEmpty(), "native render toggle " + item);
        h.getRenders().set(key.index(), true);
        if (item == ArtifactsSmokeFixtures.item("power_glove")) {
            var arm = key.index() % 2 == 0 ? wearer.getMainArm() : wearer.getMainArm().getOpposite();
            var armCapture = new AppearancePreviewSelection(mc.renderBuffers().bufferSource(), key.slotId());
            new CuriosRenderingHandler().renderArm(poses, armCapture.forPart(key.slotId()), 15728880, wearer, arm);
            mc.renderBuffers().bufferSource().endBatch();
            check(armCapture.bounds(key.slotId()).isPresent(), "first person native glove index " + key.index());
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static void verifyAttributes(Item item, CuriosSlotKey key, EquipmentComponentInstance part) {
        var stack = new ItemStack(item);
        var definition = new EquipmentSlotDefinition(key.slotId(), key.interfaceId());
        var info = new dev.equipmentstructure.api.client.EquipmentAssemblyDisplaySnapshot.ComponentInfoSnapshot(
                definition, Optional.of(part), stack, stack.getHoverName(), List.of());
        var rows = new ArrayList<dev.equipmentstructure.api.client.EquipmentAssemblyDisplaySnapshot.EquipmentStatRow>();
        dev.equipmentstructure.api.compat.curios.client.CuriosDetailPresentation.appendAttributes(
                Map.of(key.slotId(), info), List.of(definition), rows);
        var expected = new ArrayList<net.minecraft.network.chat.Component>();
        artifacts.util.TooltipHelper.addAttributeTooltips(expected::add, stack, Item.TooltipContext.of(Minecraft.getInstance().level));
        for (var line : expected) if (!line.getString().isBlank()) {
            check(rows.stream().anyMatch(row -> row.label().getString().equals(line.getString())), "native attribute tooltip " + item + ": " + line.getString());
        }
    }
}
