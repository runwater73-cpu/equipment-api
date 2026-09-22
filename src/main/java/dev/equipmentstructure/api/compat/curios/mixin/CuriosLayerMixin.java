package dev.equipmentstructure.api.compat.curios.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.compat.curios.client.CuriosRenderBridge;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;
import top.theillusivec4.curios.client.render.CuriosLayer;

@Mixin(value = CuriosLayer.class, remap = false)
abstract class CuriosLayerMixin {
    @WrapOperation(method = "lambda$render$0", at = @At(value = "INVOKE", target = "Ltop/theillusivec4/curios/api/client/ICurioRenderer;render(Lnet/minecraft/world/item/ItemStack;Ltop/theillusivec4/curios/api/SlotContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/entity/RenderLayerParent;Lnet/minecraft/client/renderer/MultiBufferSource;IFFFFFF)V"))
    @SuppressWarnings("unchecked")
    private void equipment$adjustNativeRenderer(ICurioRenderer renderer, ItemStack stack, SlotContext context,
            PoseStack poses, RenderLayerParent parent, MultiBufferSource buffers, int light,
            float swing, float amount, float partial, float age, float yaw, float pitch, Operation<Void> original) {
        poses.pushPose();
        try {
            if (CuriosRenderBridge.apply(stack, context, poses, parent.getModel(), null))
                original.call(renderer, stack, context, poses, parent, buffers, light, swing, amount, partial, age, yaw, pitch);
        } finally { poses.popPose(); }
    }
}
