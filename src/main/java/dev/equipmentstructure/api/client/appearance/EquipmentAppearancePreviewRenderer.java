package dev.equipmentstructure.api.client.appearance;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.appearance.AppearancePlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.function.BiConsumer;

/** Selects native render protocols, never mod IDs or resource-file naming conventions. */
public final class EquipmentAppearancePreviewRenderer {
    public enum Kind { ITEM, HUMANOID_ARMOR, HORSE_ARMOR, WOLF_ARMOR }

    private final Minecraft minecraft;
    private EquipmentArmorPreviewRenderer humanoid;
    private EquipmentAnimalArmorPreviewRenderer animal;
    private long generation = -1;

    public EquipmentAppearancePreviewRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public static Kind kind(ItemStack stack) {
        if (stack.getItem() instanceof AnimalArmorItem armor) {
            return switch (armor.getBodyType()) {
                case EQUESTRIAN -> Kind.HORSE_ARMOR;
                case CANINE -> Kind.WOLF_ARMOR;
            };
        }
        return EquipmentArmorPreviewRenderer.equipmentSlot(stack).isPresent() ? Kind.HUMANOID_ARMOR : Kind.ITEM;
    }

    /** The caller owns the viewport and centre; this renderer owns camera and native model orientation. */
    public void render(ItemStack stack, ResourceLocation selected, PoseStack poses, MultiBufferSource buffers,
                       float zoom, float yaw, float pitch, int light, int overlay,
                       BiConsumer<Matrix4f, AppearancePlan> capture) {
        long current = AppearanceResourceReloadListener.generation();
        if (generation != current) {
            humanoid = null;
            animal = null;
            generation = current;
        }
        Kind kind = kind(stack);
        poses.pushPose();
        try {
            poses.scale(zoom, kind == Kind.ITEM ? -zoom : zoom, kind == Kind.ITEM ? zoom : -zoom);
            poses.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(yaw), (float) Math.toRadians(pitch), 0));
            Lighting.setupFor3DItems();
            if (kind == Kind.HUMANOID_ARMOR) {
                poses.translate(0, -0.5, 0);
                if (humanoid == null) humanoid = new EquipmentArmorPreviewRenderer(minecraft);
                humanoid.render(stack, EquipmentArmorPreviewRenderer.equipmentSlot(stack).orElseThrow(),
                        selected, poses, buffers, light, overlay, capture);
            } else if (kind == Kind.HORSE_ARMOR || kind == Kind.WOLF_ARMOR) {
                poses.translate(0, kind == Kind.HORSE_ARMOR ? -0.5 : -1, 0);
                if (animal == null) animal = new EquipmentAnimalArmorPreviewRenderer(minecraft);
                animal.render(stack, selected, poses, buffers, light, capture);
            } else {
                // ItemRenderer resolves overrides, camera transforms, baked passes and BEWLR itself.
                // isGui3d is a display hint, not a reliable test for custom-renderer geometry.
                EquipmentItemAppearanceOverlays.capturePreview(stack, capture, () ->
                        minecraft.getItemRenderer().renderStatic(minecraft.player, stack, ItemDisplayContext.GUI,
                                false, poses, buffers, minecraft.level, light, overlay, 0));
            }
        } finally {
            poses.popPose();
        }
    }
}
