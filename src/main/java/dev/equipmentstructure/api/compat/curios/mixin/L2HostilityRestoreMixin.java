package dev.equipmentstructure.api.compat.curios.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.equipmentstructure.api.compat.curios.ArmorCurioStackHandler;
import dev.equipmentstructure.api.compat.curios.CuriosArmorCompat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/** L2's restoration pocket returns a previously removed item through its authoritative slot access. */
@Pseudo
@Mixin(targets = "dev.xkmc.l2hostility.compat.curios.CurioCompat$CurioSlotAccess", remap = false)
abstract class L2HostilityRestoreMixin {
    @Shadow @Final private LivingEntity player;
    @Shadow @Final private String id;

    @WrapOperation(method = "set", at = @At(value = "INVOKE", target = "Ltop/theillusivec4/curios/api/type/inventory/IDynamicStackHandler;setStackInSlot(ILnet/minecraft/world/item/ItemStack;)V"))
    private void equipment$returnRestoredItem(IDynamicStackHandler handler, int slot, ItemStack incoming,
            Operation<Void> original) {
        if (!player.level().isClientSide() && CuriosArmorCompat.managesType(player, id)
                && handler instanceof ArmorCurioStackHandler managed && !incoming.isEmpty()
                && handler.getStackInSlot(slot).isEmpty()) {
            // Reuse backup restoration: return to its armor if possible, otherwise recover once to inventory.
            managed.restore(slot, incoming);
        } else original.call(handler, slot, incoming);
    }
}
