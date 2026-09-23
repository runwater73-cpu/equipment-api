package dev.equipmentstructure.api.compat.curios.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.client.appearance.AppearancePreviewSelection;
import dev.equipmentstructure.api.compat.curios.CuriosSlotKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import java.util.function.BiConsumer;

/** Isolated, unticked client player. Renderer queries never see or mutate the real player's inventory. */
public final class CuriosArmorPreview {
    private RemotePlayer wearer;
    private PlayerModel<AbstractClientPlayer> model;
    private ItemStack observed = ItemStack.EMPTY;
    private int nativeStateHash;
    private RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent;
    private final java.util.Map<net.minecraft.world.item.Item, net.minecraft.client.renderer.entity.layers.RenderLayer> externalLayers = new java.util.HashMap<>();
    public void render(ItemStack armor, EquipmentSlot position, PoseStack poses, MultiBufferSource buffers,
            int light, BiConsumer<ResourceLocation, Matrix4f> capture) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (wearer == null || wearer.level() != mc.level) {
            wearer = new RemotePlayer(mc.level, mc.player.getGameProfile());
            model = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
            observed = ItemStack.EMPTY;
            externalLayers.clear();
            parent = new RenderLayerParent<>() {
                @Override public PlayerModel<AbstractClientPlayer> getModel() { return model; }
                @Override public ResourceLocation getTextureLocation(AbstractClientPlayer entity) { return entity.getSkin().texture(); }
            };
        }
        var nativeInventory = CuriosApi.getCuriosInventory(mc.player).orElse(null);
        int stateHash = nativeInventory == null ? 0 : nativeInventory.getCurios().entrySet().stream()
                .mapToInt(e -> java.util.Objects.hash(e.getKey(), e.getValue().getSlots(), e.getValue().getRenders(), e.getValue().getActiveStates())).sum();
        if (!ItemStack.matches(observed, armor) || nativeStateHash != stateHash) {
            nativeStateHash = stateHash;
            observed = armor.copy();
            for (var slot : EquipmentSlot.values()) wearer.setItemSlot(slot, ItemStack.EMPTY);
            wearer.setItemSlot(position, observed);
            CuriosApi.getCuriosInventory(wearer).ifPresent(inv -> {
                inv.reset();
                inv.getCurios().forEach((type, h) -> {
                    if (nativeInventory != null) nativeInventory.getStacksHandler(type).ifPresent(original -> {
                        var state = original.getSyncTag().copy();
                        state.remove("Stacks"); state.remove("Cosmetics");
                        h.applySyncTag(state);
                        h.getSlots(); // Resolve native modifiers before filling additional indices.
                    });
                    for (int i = 0; i < h.getSlots(); i++) {
                        h.getStacks().setStackInSlot(i, ItemStack.EMPTY);
                        h.getCosmeticStacks().setStackInSlot(i, ItemStack.EMPTY);
                    }
                });
                var structure = EquipmentStructureApi.structure(observed).orElse(null);
                if (structure != null) structure.components().forEach((id, parts) -> CuriosSlotKey.parse(id).ifPresent(key ->
                    inv.getStacksHandler(key.type()).ifPresent(h -> {
                        if (key.index() < h.getSlots()) (key.cosmetic() ? h.getCosmeticStacks() : h.getStacks())
                                .setStackInSlot(key.index(), EquipmentComponentRegistry.createItemStack(parts.getFirst()).orElse(ItemStack.EMPTY));
                    })));
            });
        }
        model.young = false;
        model.setupAnim(wearer, 0, 0, 0, 0, 0);
        CuriosPreviewModels.render(wearer, model, () -> CuriosApi.getCuriosInventory(wearer).ifPresent(inv -> inv.getCurios().forEach((type, handler) -> {
            for (int i = 0; i < handler.getSlots(); i++) {
                var item = handler.getCosmeticStacks().getStackInSlot(i);
                boolean cosmetic = !item.isEmpty();
                boolean renderable = i < handler.getRenders().size() && handler.getRenders().get(i);
                if (!cosmetic && renderable) item = handler.getStacks().getStackInSlot(i);
                if (item.isEmpty()) continue;
                var renderer = CuriosRendererRegistry.getRenderer(item.getItem()).orElse(null);
                var key = new CuriosSlotKey(type, i, cosmetic);
                var target = buffers instanceof AppearancePreviewSelection selection ? selection.forPart(key.slotId()) : buffers;
                var context = new SlotContext(type, wearer, i, cosmetic, renderable);
                if (renderer != null) {
                    CuriosRenderBridge.render(renderer, item, context, poses, parent, target, light, 0, 0, 0, 0, 0, 0, capture);
                } else {
                    var displayItem = item;
                    if (!externalLayers.containsKey(item.getItem())) externalLayers.put(item.getItem(), CuriosLayerPreviewRegistry.create(item, parent));
                    var layer = externalLayers.get(item.getItem());
                    if (layer == null) {
                        CuriosRenderBridge.render(CuriosItemModelRenderer.INSTANCE, item, context, poses, parent, target,
                                light, 0, 0, 0, 0, 0, 0, capture);
                        continue;
                    }
                    poses.pushPose();
                    try {
                        if (CuriosRenderBridge.apply(displayItem, context, poses, model, capture))
                            CuriosLayerPreviewRegistry.preview(() -> layer.render(poses, target, light, wearer, 0, 0, 0, 0, 0, 0));
                    } finally { poses.popPose(); }
                }
            }
        })));
    }
}
