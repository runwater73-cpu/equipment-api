package dev.equipmentstructure.api.compat.curios.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.equipmentstructure.api.compat.curios.*;
import net.minecraft.core.NonNullList;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.theillusivec4.curios.common.event.CuriosEventHandler;
import top.theillusivec4.curios.api.type.capability.ICurio.DropRule;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import java.util.*;
import java.util.function.Predicate;

@Mixin(value = CuriosEventHandler.class, remap = false)
abstract class CuriosLifecycleMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method = "curioRightClick", at = @At("HEAD"), cancellable = true)
    private void equipment$noQuickEquip(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        // Skip only Curios' listener; do not cancel the item's normal right-click action.
        if (CuriosArmorCompat.definitions(event.getEntity()).enabled()) callback.cancel();
    }
    @WrapMethod(method = "onDatapackSync")
    private void equipment$reload(OnDatapackSyncEvent event, Operation<Void> original) {
        CuriosArmorCompat.beginNativeLoad();
        try { original.call(event); } finally { CuriosArmorCompat.endNativeLoad(); }
    }
    @WrapMethod(method = "entityConstructing")
    private void equipment$construct(EntityEvent.EntityConstructing event, Operation<Void> original) {
        CuriosArmorCompat.beginNativeLoad();
        try { original.call(event); } finally { CuriosArmorCompat.endNativeLoad(); }
    }
    @WrapMethod(method = "handleDrops")
    private static void equipment$nestedDrops(String type, LivingEntity entity,
            List<Tuple<Predicate<ItemStack>, DropRule>> rules, NonNullList<Boolean> renders,
            IDynamicStackHandler stacks, boolean cosmetic, Collection<ItemEntity> drops,
            boolean keep, LivingDropsEvent event, Operation<Void> original) {
        original.call(type, entity, CuriosArmorLifecycle.deathRules(type, entity, rules, stacks, cosmetic, keep, event),
                renders, stacks, cosmetic, drops, keep, event);
    }
}
