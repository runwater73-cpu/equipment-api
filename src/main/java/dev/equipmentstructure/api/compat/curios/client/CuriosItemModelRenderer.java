package dev.equipmentstructure.api.compat.curios.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.compat.curios.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.*;
import java.util.*;

/** Original baked item appearance when an armor component has no wearable renderer. No registry replacement. */
public final class CuriosItemModelRenderer implements ICurioRenderer {
    public static final CuriosItemModelRenderer INSTANCE = new CuriosItemModelRenderer();
    private record Layout(EquipmentStructure structure, Map<ResourceLocation, Anchor> anchors) {}
    private record Anchor(double x, double y, double z, float size) {}
    private static final Map<ItemStack, Layout> LAYOUTS = new WeakHashMap<>();
    private CuriosItemModelRenderer() {}
    public static boolean usesFallback(Item item) {
        return CuriosRendererRegistry.getRenderer(item).isEmpty() && !CuriosLayerPreviewRegistry.hasAdapter(item);
    }
    private static Anchor anchor(ItemStack owner, CuriosSlotKey key, EquipmentSlot position) {
        var structure = EquipmentStructureApi.structure(owner).orElseThrow();
        var layout = LAYOUTS.get(owner);
        if (layout == null || layout.structure != structure) {
            // Slot order, rather than occupied-item order, keeps neighbors still when a part is removed.
            var slots = structure.slots().stream().map(s -> CuriosSlotKey.parse(s.id()).orElse(null))
                    .filter(Objects::nonNull).map(k -> new CuriosSlotKey(k.type(), k.index(), false)).distinct().toList();
            int columns = Math.min(3, Math.max(1, slots.size()));
            int rows = Math.max(1, (slots.size() + columns - 1) / columns);
            double width = .54, height = position == EquipmentSlot.FEET ? .28 : .6;
            float size = (float)Math.min(.22, Math.min(width / columns, height / rows) * .9);
            var anchors = new HashMap<ResourceLocation, Anchor>();
            for (int i = 0; i < slots.size(); i++) {
                double y = (i / columns + .5) * height / rows;
                if (position == EquipmentSlot.HEAD) y -= .6;
                if (position == EquipmentSlot.FEET) y += .48;
                anchors.put(slots.get(i).slotId(), new Anchor((i % columns + .5) * width / columns - width / 2,
                        y, position == EquipmentSlot.HEAD ? -.3 : -.2, size));
            }
            layout = new Layout(structure, Map.copyOf(anchors));
            LAYOUTS.put(owner, layout);
        }
        return layout.anchors.getOrDefault(new CuriosSlotKey(key.type(), key.index(), false).slotId(), new Anchor(0, .3, -.2, .2F));
    }
    static void frame(ItemStack item, SlotContext context, EntityModel<?> model, PoseStack poses) {
        var owner = CuriosArmorCompat.owner(context);
        if (owner.isEmpty()) return;
        var key = new CuriosSlotKey(context.identifier(), context.index(), context.cosmetic());
        var position = CuriosArmorCompat.definitions(context.entity()).profile(key.type()).armorSlot();
        CuriosRenderFrames.itemFrame(item, context, position).apply(item, context, model, poses);
        var anchor = anchor(owner, key, position);
        poses.translate(anchor.x, anchor.y, anchor.z);
    }
    @Override public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack item, SlotContext context,
            PoseStack poses, RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light,
            float swing, float amount, float partial, float age, float yaw, float pitch) {
        var owner = CuriosArmorCompat.owner(context);
        if (owner.isEmpty()) return;
        var key = new CuriosSlotKey(context.identifier(), context.index(), context.cosmetic());
        if (EquipmentStructureApi.component(owner, key.slotId()).isEmpty()
                || PlayerBoundCurios.bound(item, context.entity())) return;
        poses.pushPose();
        try {
            frame(item, context, parent.getModel(), poses);
            float size = anchor(owner, key, CuriosArmorCompat.definitions(context.entity()).profile(key.type()).armorSlot()).size;
            poses.scale(size, -size, -size);
            Minecraft.getInstance().getItemRenderer().renderStatic(context.entity(), item, ItemDisplayContext.FIXED,
                    false, poses, buffers, context.entity().level(), light, OverlayTexture.NO_OVERLAY, context.index());
        } finally { poses.popPose(); }
    }
}
