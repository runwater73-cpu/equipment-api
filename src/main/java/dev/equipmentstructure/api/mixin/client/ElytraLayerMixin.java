package dev.equipmentstructure.api.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.client.appearance.EquipmentElytraAppearanceBridge;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraLayer.class)
abstract class ElytraLayerMixin<T extends LivingEntity> {
    @Shadow @Final private ElytraModel<T> elytraModel;

    // Draw before vanilla acquires its consumer: changing RenderType can flush and
    // invalidate a consumer already held by ElytraLayer (notably on immediate buffers).
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;getArmorFoilBuffer(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;Z)Lcom/mojang/blaze3d/vertex/VertexConsumer;"), cancellable = true)
    private void equipmentStructureApi$renderAttached(PoseStack poses, MultiBufferSource buffers, int light,
                                                       T entity, float limbSwing, float limbSwingAmount, float partialTicks,
                                                       float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        ItemStack armor = entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        if (EquipmentElytraAppearanceBridge.renderLayer(armor, elytraModel, poses, buffers, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, entity, partialTicks)) {
            // The original method has already pushed its layer frame at this injection point.
            poses.popPose();
            ci.cancel();
        }
    }
}
