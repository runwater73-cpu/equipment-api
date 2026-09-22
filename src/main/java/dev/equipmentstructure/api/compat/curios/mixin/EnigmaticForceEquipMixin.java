package dev.equipmentstructure.api.compat.curios.mixin;

import dev.equipmentstructure.api.compat.curios.CuriosArmorCompat;
import dev.equipmentstructure.api.compat.curios.PlayerBoundCurios;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve mandatory native player binding; reject ordinary forced insertion into armor. */
@Pseudo
@Mixin(targets = "auviotre.enigmatic.legacy.handlers.EnigmaticHandler", remap = false)
interface EnigmaticForceEquipMixin {
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "tryForceEquip")
    private static boolean equipment$playerBinding(LivingEntity entity, ItemStack item,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        if (!(entity instanceof Player) || !CuriosArmorCompat.definitions(entity).enabled()) return original.call(entity, item);
        if (!PlayerBoundCurios.eligible(item, entity)) return false;
        if (entity.level().isClientSide()) return false;
        // The addon assumes a direct setter always succeeds and then consumes the source.
        // Use the same checked native transaction as the panel and report its actual result.
        for (var key : dev.equipmentstructure.api.compat.curios.CuriosBindingActions.keys((Player) entity)) {
            if (PlayerBoundCurios.install(entity, key, item)) {
                top.theillusivec4.curios.api.CuriosApi.getCurio(item).ifPresent(curio -> curio.onEquipFromUse(
                        new top.theillusivec4.curios.api.SlotContext(key.type(), entity, key.index(), false, true)));
                return true;
            }
        }
        return false;
    }
}
