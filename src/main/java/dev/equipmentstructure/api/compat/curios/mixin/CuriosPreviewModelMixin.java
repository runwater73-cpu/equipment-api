package dev.equipmentstructure.api.compat.curios.mixin;

import dev.equipmentstructure.api.compat.curios.client.CuriosPreviewModels;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
abstract class CuriosPreviewModelMixin {
    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void equipment$isolatedPreviewModel(CallbackInfoReturnable<EntityModel<?>> cir) {
        var model = CuriosPreviewModels.modelFor(this);
        if (model != null) cir.setReturnValue(model);
    }
}
