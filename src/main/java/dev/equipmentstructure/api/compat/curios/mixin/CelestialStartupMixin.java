package dev.equipmentstructure.api.compat.curios.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.equipmentstructure.api.compat.curios.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/** Preserve Celestial's optional mandatory start, including retry when installation is rejected. */
@Pseudo
@Mixin(targets = "com.xiaoyue.celestial_artifacts.events.StartUpGiveHandler", remap = false)
abstract class CelestialStartupMixin {
    @WrapOperation(method = "tickPlayer", at = @At(value = "INVOKE", target = "Ltop/theillusivec4/curios/api/type/inventory/IDynamicStackHandler;setStackInSlot(ILnet/minecraft/world/item/ItemStack;)V"))
    private static void equipment$bindScroll(IDynamicStackHandler handler, int index, ItemStack stack,
            Operation<Void> original, @Local(argsOnly = true) PlayerTickEvent.Post event,
            @Share("equipment$grantRejected") LocalBooleanRef rejected) {
        var player = event.getEntity();
        if (!CuriosArmorCompat.managesType(player, "catastrophe")) {
            original.call(handler, index, stack);
            return;
        }
        rejected.set(!PlayerBoundCurios.install(player, new CuriosSlotKey("catastrophe", index, false), stack));
    }

    @WrapOperation(method = "tickPlayer", at = @At(value = "INVOKE", target = "Lcom/xiaoyue/celestial_core/content/generic/PlayerFlagData;addFlag(Ljava/lang/String;)V"))
    private static void equipment$recordSuccessfulGrant(@Coerce Object data, String flag, Operation<Void> original,
            @Share("equipment$grantRejected") LocalBooleanRef rejected) {
        if (!flag.equals("cs") || !rejected.get()) original.call(data, flag);
    }
}
