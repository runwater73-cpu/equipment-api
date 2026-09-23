package dev.equipmentstructure.api.compat.curios.mixin;

import dev.equipmentstructure.api.compat.curios.CuriosArmorCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.inventory.ArmorSlot")
abstract class ArmorSlotMixin {
    @Inject(method = "mayPickup", at = @At("RETURN"), cancellable = true)
    private void equipment$curioRestrictions(Player player, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValue() && !CuriosArmorCompat.canRemoveArmor(((Slot) (Object) this).getItem(), player))
            result.setReturnValue(false);
    }
}
