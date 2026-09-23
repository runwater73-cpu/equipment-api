package dev.equipmentstructure.api.mixin;

import dev.equipmentstructure.api.EquipmentBreakRecovery;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Consumer;

@Mixin(ItemStack.class)
abstract class EquipmentBreakMixin {
    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"), cancellable = true)
    private void equipment$recoverOnDurabilityBreak(int damage, ServerLevel level, LivingEntity wearer,
            Consumer<Item> onBreak, CallbackInfo callback) {
        var stack = (ItemStack) (Object) this;
        if (stack.getCount() == 1 && !EquipmentBreakRecovery.beforeBreak(stack, wearer)) {
            // Leave a recoverable host when an addon cancels or cannot reconstruct one of its items.
            stack.setDamageValue(Math.max(0, stack.getMaxDamage() - 1));
            callback.cancel();
        }
    }
}
