package dev.equipmentstructure.api.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.client.appearance.EquipmentArmorAppearanceBridge;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
abstract class HumanoidArmorLayerMixin {
    @Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/extensions/common/IClientItemExtensions;getDefaultDyeColor(Lnet/minecraft/world/item/ItemStack;)I"),
            cancellable = true,
            require = 0)
    private void equipmentStructureApi$armor(PoseStack poses, MultiBufferSource buffers, LivingEntity entity,
            EquipmentSlot slot, int light, HumanoidModel<?> fallbackModel, float limbSwing, float limbSwingAmount,
            float partialTick, float age, float headYaw, float headPitch, CallbackInfo ci, @Local Model model) {
        if (EquipmentArmorAppearanceBridge.renderLayer(entity.getItemBySlot(slot), slot, model,
                poses, buffers, light, OverlayTexture.NO_OVERLAY, entity, partialTick)) ci.cancel();
    }
}
