package dev.equipmentstructure.api.client.appearance;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.appearance.AppearancePlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HorseArmorLayer;
import net.minecraft.client.renderer.entity.layers.WolfArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.function.BiConsumer;

/** Uses vanilla armor layers, including their textures, dye overlays and damage cracks. */
final class EquipmentAnimalArmorPreviewRenderer {
    private final Minecraft minecraft;
    private final HorseArmorLayer horseLayer;
    private final WolfArmorLayer wolfLayer;
    private Horse horse;
    private Wolf wolf;

    EquipmentAnimalArmorPreviewRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
        var models = minecraft.getEntityModels();
        var horseModel = new HorseModel<Horse>(models.bakeLayer(ModelLayers.HORSE));
        var wolfModel = new WolfModel<Wolf>(models.bakeLayer(ModelLayers.WOLF));
        horseModel.young = wolfModel.young = false;
        horseLayer = new HorseArmorLayer(new RenderLayerParent<>() {
            @Override public HorseModel<Horse> getModel() { return horseModel; }
            @Override public ResourceLocation getTextureLocation(Horse entity) {
                return ((AnimalArmorItem) entity.getBodyArmorItem().getItem()).getTexture();
            }
        }, models);
        wolfLayer = new WolfArmorLayer(new RenderLayerParent<>() {
            @Override public WolfModel<Wolf> getModel() { return wolfModel; }
            @Override public ResourceLocation getTextureLocation(Wolf entity) {
                return ((AnimalArmorItem) entity.getBodyArmorItem().getItem()).getTexture();
            }
        }, models);
    }

    void render(ItemStack stack, ResourceLocation selected, PoseStack poses, MultiBufferSource buffers,
                int light, BiConsumer<Matrix4f, AppearancePlan> capture) {
        if (minecraft.level == null || !(stack.getItem() instanceof AnimalArmorItem armor)) return;
        if (armor.getBodyType() == AnimalArmorItem.BodyType.EQUESTRIAN) {
            if (horse == null || horse.level() != minecraft.level) horse = new Horse(EntityType.HORSE, minecraft.level);
            horse.setBodyArmorItem(stack);
            EquipmentAnimalArmorAppearanceBridge.capturePreview(horse.getBodyArmorItem(), selected, capture,
                    () -> horseLayer.render(poses, buffers, light, horse, 0, 0, 0, 0, 0, 0));
        } else {
            if (wolf == null || wolf.level() != minecraft.level) {
                // Vanilla's preview stand-in must accept the CANINE protocol, not just Items.WOLF_ARMOR.
                // This private entity is never spawned and does not change real wolves' equip rules.
                wolf = new Wolf(EntityType.WOLF, minecraft.level) {
                    @Override public boolean hasArmor() {
                        return getBodyArmorItem().getItem() instanceof AnimalArmorItem item
                                && item.getBodyType() == AnimalArmorItem.BodyType.CANINE;
                    }
                };
            }
            wolf.setBodyArmorItem(stack);
            EquipmentAnimalArmorAppearanceBridge.capturePreview(wolf.getBodyArmorItem(), selected, capture,
                    () -> wolfLayer.render(poses, buffers, light, wolf, 0, 0, 0, 0, 0, 0));
        }
    }
}
