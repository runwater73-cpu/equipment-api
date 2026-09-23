package dev.equipmentstructure.api.compat.curios.mixin;

import dev.equipmentstructure.api.compat.curios.ArmorCurioStackHandler;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Flush mutable third-party items before vanilla copies armor into another container or drop. */
@Mixin(ItemStack.class)
abstract class ItemStackCopyMixin {
    @Inject(method = "copy", at = @At("HEAD"))
    private void equipment$flushBeforeCopy(CallbackInfoReturnable<ItemStack> cir) {
        ArmorCurioStackHandler.beforeCopy((ItemStack) (Object) this);
    }
}
