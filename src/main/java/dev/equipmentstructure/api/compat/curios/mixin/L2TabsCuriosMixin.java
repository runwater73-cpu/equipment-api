package dev.equipmentstructure.api.compat.curios.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.equipmentstructure.api.compat.curios.CuriosArmorCompat;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/** Filter only the duplicate equipment inventory; L2's other tabs and native metadata stay intact. */
@Pseudo
@Mixin(targets = "dev.xkmc.l2tabs.compat.curios.CuriosWrapper", remap = false)
abstract class L2TabsCuriosMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Ltop/theillusivec4/curios/api/type/inventory/ICurioStacksHandler;isVisible()Z"))
    private boolean equipment$visible(ICurioStacksHandler handler, Operation<Boolean> original,
            @Local(argsOnly = true) LivingEntity player) {
        return original.call(handler) && !CuriosArmorCompat.managesType(player, handler.getIdentifier());
    }
}
