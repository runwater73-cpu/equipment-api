package dev.equipmentstructure.api.compat.curios.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.equipmentstructure.api.compat.curios.CuriosArmorCompat;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.inventory.container.CuriosContainer;

/** Hide managed entries only in the old menu; native slot metadata remains observable by addons. */
@Mixin(value = CuriosContainer.class, remap = false)
abstract class CuriosContainerMixin {
    @Shadow @Final public Player player;
    @Unique private boolean equipment$visible(ICurioStacksHandler handler) {
        return handler.isVisible() && !CuriosArmorCompat.manages(new SlotContext(handler.getIdentifier(), player, 0, false, true));
    }
    @WrapOperation(method = "setPage", at = @At(value = "INVOKE", target = "Ltop/theillusivec4/curios/api/type/capability/ICuriosItemHandler;getVisibleSlots()I"))
    private int equipment$visibleCount(ICuriosItemHandler inventory, Operation<Integer> original) {
        return inventory.getCurios().values().stream().filter(this::equipment$visible).mapToInt(ICurioStacksHandler::getSlots).sum();
    }
    @WrapOperation(method = "setPage", at = @At(value = "INVOKE", target = "Ltop/theillusivec4/curios/api/type/inventory/ICurioStacksHandler;isVisible()Z"))
    private boolean equipment$menuVisibility(ICurioStacksHandler handler, Operation<Boolean> original) {
        return equipment$visible(handler);
    }
}
