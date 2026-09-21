package dev.equipmentstructure.api.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.client.appearance.EquipmentAnimalArmorAppearanceBridge;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.WolfArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.animal.Wolf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WolfArmorLayer.class)
abstract class WolfArmorLayerMixin {
    @Shadow @Final private WolfModel<Wolf> model;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/animal/Wolf;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;entityCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"),
            cancellable = true, require = 0)
    private void equipmentStructureApi$armor(PoseStack poses, MultiBufferSource buffers, int light, Wolf wolf,
            float limbSwing, float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch, CallbackInfo ci) {
        if (EquipmentAnimalArmorAppearanceBridge.renderLayer(wolf.getBodyArmorItem(), model,
                poses, buffers, light, OverlayTexture.NO_OVERLAY, wolf, partialTick)) ci.cancel();
    }
}
