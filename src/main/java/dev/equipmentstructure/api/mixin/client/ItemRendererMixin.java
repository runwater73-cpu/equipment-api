package dev.equipmentstructure.api.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.equipmentstructure.api.client.appearance.EquipmentItemAppearanceOverlays;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks the two vanilla item draw paths while their item-local pose is still active. */
@Mixin(ItemRenderer.class)
abstract class ItemRendererMixin {
    // Resolve requested hiding before either branch obtains a VertexConsumer. Drawing attachments
    // inside renderModelLists would flush/invalidate that consumer on immediate buffers.
    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/BakedModel;isCustomRenderer()Z"), require = 0)
    private void equipmentStructureApi$prepareVisibility(ItemStack stack, ItemDisplayContext context,
            boolean leftHand, PoseStack poses, MultiBufferSource buffers, int light, int overlay,
            BakedModel model, CallbackInfo ci,
            @Share("appearanceResult") LocalRef<EquipmentItemAppearanceOverlays.RenderResult> result) {
        result.set(EquipmentItemAppearanceOverlays.renderBeforeItem(stack, context, poses, buffers, light, overlay));
    }

    @WrapOperation(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderModelLists(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;IILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"),
            require = 0)
    private void equipmentStructureApi$visibleBakedItem(ItemRenderer renderer, BakedModel model, ItemStack stack,
            int light, int overlay, PoseStack poses, VertexConsumer vertices, Operation<Void> draw,
            @Share("appearanceResult") LocalRef<EquipmentItemAppearanceOverlays.RenderResult> result,
            @Share("appearanceBakedPath") LocalBooleanRef bakedPath) {
        bakedPath.set(true);
        if (result.get() == null || !result.get().hideOriginal()) {
            draw.call(renderer, model, stack, light, overlay, poses, vertices);
        }
    }

    @WrapOperation(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BlockEntityWithoutLevelRenderer;renderByItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"),
            require = 0)
    private void equipmentStructureApi$visibleCustomItem(BlockEntityWithoutLevelRenderer renderer, ItemStack stack,
            ItemDisplayContext context, PoseStack poses, MultiBufferSource buffers, int light, int overlay, Operation<Void> draw,
            @Share("appearanceResult") LocalRef<EquipmentItemAppearanceOverlays.RenderResult> result) {
        if (result.get() == null || !result.get().hideOriginal()) {
            draw.call(renderer, stack, context, poses, buffers, light, overlay);
        }
    }

    /**
     * Draw overlays once per item, after all vanilla render passes and while the
     * item-local pose is still active. Rendering from the model-pass wrapper
     * would duplicate an overlay for every render type/direction.
     */
    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", ordinal = 0),
            require = 0)
    private void equipmentStructureApi$renderOverlay(ItemStack stack, ItemDisplayContext context,
                                                     boolean leftHand, PoseStack poses,
                                                     MultiBufferSource buffers, int light, int overlay,
                                                     BakedModel model, CallbackInfo callback,
                                                     @Share("appearanceResult") LocalRef<EquipmentItemAppearanceOverlays.RenderResult> result,
                                                     @Share("appearanceBakedPath") LocalBooleanRef bakedPath) {
        if (result.get() == null) {
            EquipmentItemAppearanceOverlays.renderAfterItem(stack, context, poses, buffers, light, overlay, bakedPath.get());
        }
    }
}
