package dev.equipmentstructure.api.client.appearance;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.appearance.AppearancePlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/** Renders the real worn armor layer inside the placement editor. */
public final class EquipmentArmorPreviewRenderer {
    private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private final Minecraft minecraft;
    private final HumanoidModel<ArmorStand> parent;
    private final HumanoidArmorLayer<ArmorStand, HumanoidModel<ArmorStand>, HumanoidModel<ArmorStand>> armorLayer;
    private ArmorStand wearer;

    public EquipmentArmorPreviewRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
        var models = minecraft.getEntityModels();
        parent = new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER));
        armorLayer = new HumanoidArmorLayer<>(new RenderLayerParent<>() {
            @Override public HumanoidModel<ArmorStand> getModel() { return parent; }
            @Override public ResourceLocation getTextureLocation(ArmorStand entity) {
                return ResourceLocation.withDefaultNamespace("textures/entity/armorstand/wood.png");
            }
        }, new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), minecraft.getModelManager());
    }

    /** Mirrors HumanoidArmorLayer's own ArmorItem and slot checks. */
    public static Optional<EquipmentSlot> equipmentSlot(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem armor)) return Optional.empty();
        EquipmentSlot slot = armor.getEquipmentSlot();
        if (slot == null || ArmorAttachment.defaults(slot).isEmpty()) return Optional.empty();
        return EquipmentStructureApi.structure(stack).isPresent() ? Optional.of(slot) : Optional.empty();
    }

    public void render(ItemStack stack, EquipmentSlot slot, ResourceLocation selectedComponent,
                       PoseStack poses, MultiBufferSource buffers, int light, int overlay,
                       BiConsumer<Matrix4f, AppearancePlan> capture) {
        if (minecraft.level == null || !ARMOR_SLOTS.contains(slot)) return;
        if (wearer == null || wearer.level() != minecraft.level) {
            wearer = new ArmorStand(minecraft.level, 0, 0, 0);
            parent.young = false;
        }
        for (var armorSlot : ARMOR_SLOTS) wearer.setItemSlot(armorSlot, ItemStack.EMPTY);
        wearer.setItemSlot(slot, stack);
        ItemStack rendered = wearer.getItemBySlot(slot);
        EquipmentArmorAppearanceBridge.capturePreview(rendered, selectedComponent, capture, () ->
                armorLayer.render(poses, buffers, light, wearer, 0, 0, 0, 0, 0, 0));
    }
}
