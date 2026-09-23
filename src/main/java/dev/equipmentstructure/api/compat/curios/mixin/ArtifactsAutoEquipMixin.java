package dev.equipmentstructure.api.compat.curios.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.equipmentstructure.api.compat.curios.CuriosArmorCompat;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import top.theillusivec4.curios.api.SlotContext;

/** The addon assumes setStackInSlot succeeds; managed armor requires a UI transaction. */
@Pseudo
@Mixin(targets = "artifacts.neoforge.integration.curios.CuriosSlotProvider", remap = false)
abstract class ArtifactsAutoEquipMixin {
    @WrapOperation(method = "tryEquipItem", at = @At(value = "INVOKE", target = "Ltop/theillusivec4/curios/api/CuriosApi;isStackValid(Ltop/theillusivec4/curios/api/SlotContext;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean equipment$useAssemblyForManagedSlots(SlotContext context, ItemStack stack, Operation<Boolean> original) {
        return !CuriosArmorCompat.manages(context) && original.call(context, stack);
    }
}
