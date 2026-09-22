package dev.equipmentstructure.api.compat.curios.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.compat.curios.client.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import top.theillusivec4.curios.api.CuriosApi;

@Pseudo
@Mixin(targets = "auviotre.enigmatic.legacy.client.renderer.layer.EnigmaticElytraLayer", remap = false)
abstract class EnigmaticElytraMixin {
    @WrapMethod(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V")
    private void equipment$externalPose(PoseStack poses, MultiBufferSource buffers, int light, LivingEntity entity,
            float swing, float amount, float partial, float age, float yaw, float pitch, Operation<Void> original) {
        if (CuriosLayerPreviewRegistry.previewing()) {
            original.call(poses, buffers, light, entity, swing, amount, partial, age, yaw, pitch); return;
        }
        var accessory = CuriosApi.getCuriosInventory(entity).flatMap(inv -> inv.findFirstCurio(item -> {
            var id = BuiltInRegistries.ITEM.getKey(item.getItem());
            return id.getNamespace().equals("enigmaticlegacyplus") && (id.getPath().equals("majestic_elytra") || id.getPath().equals("chaos_elytra"));
        })).orElse(null);
        poses.pushPose();
        try {
            if (accessory == null || CuriosRenderBridge.apply(accessory.stack(), accessory.slotContext(), poses,
                    ((RenderLayer<?, ?>) (Object) this).getParentModel(), null))
                original.call(poses, buffers, light, entity, swing, amount, partial, age, yaw, pitch);
        } finally { poses.popPose(); }
    }
}
