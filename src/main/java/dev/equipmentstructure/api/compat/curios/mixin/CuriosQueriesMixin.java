package dev.equipmentstructure.api.compat.curios.mixin;

import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.compat.curios.CuriosQueryCache;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.common.capability.*;

@Mixin(value = CurioInventoryCapability.class, remap = false)
abstract class CuriosQueriesMixin implements CuriosQueryCache {
    @Shadow @Final LivingEntity livingEntity;
    @Shadow @Final CurioInventory curioInventory;
    @Override public void equipment$invalidateQueries() { ((CuriosQueryCache) curioInventory).equipment$invalidateQueries(); }
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "saveInventory")
    private net.minecraft.nbt.ListTag equipment$flushBackup(boolean clear, com.llamalad7.mixinextras.injector.wrapoperation.Operation<net.minecraft.nbt.ListTag> original) {
        dev.equipmentstructure.api.compat.curios.CuriosArmorCompat.flush(livingEntity);
        curioInventory.asMap().values().forEach(h -> {
            for (int i = 0; i < h.getSlots(); i++) { h.getStacks().getStackInSlot(i); h.getCosmeticStacks().getStackInSlot(i); }
        });
        return original.call(clear);
    }
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "loadStacks")
    private void equipment$restoreBackup(top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler handler,
            net.neoforged.neoforge.items.ItemStackHandler loaded, top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler stacks,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        if (!(livingEntity instanceof net.minecraft.world.entity.player.Player)
                || livingEntity.level().isClientSide()
                || !dev.equipmentstructure.api.compat.curios.CuriosArmorCompat.managesType(livingEntity, handler.getIdentifier())
                || !(stacks instanceof dev.equipmentstructure.api.compat.curios.ArmorCurioStackHandler armor)) {
            original.call(handler, loaded, stacks); return;
        }
        for (int i = 0; i < loaded.getSlots(); i++) {
            var item = loaded.getStackInSlot(i);
            if (i < stacks.getSlots()) armor.restore(i, item);
            else if (!item.isEmpty()) ((net.minecraft.world.entity.player.Player) livingEntity).getInventory().placeItemBackInInventory(item.copy());
        }
    }
    @Unique private final Object[] equipment$observed = new Object[8];
    @Unique private static final EquipmentSlot[] equipment$armor = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    @Inject(method = {"findFirstCurio(Ljava/util/function/Predicate;ZLjava/lang/String;)Ljava/util/Optional;",
            "findCurios(Ljava/util/function/Predicate;ZLjava/lang/String;)Ljava/util/List;"}, at = @At("HEAD"))
    private void equipment$refreshQueries(CallbackInfoReturnable<?> result) {
        boolean changed = false;
        for (int i = 0; i < equipment$armor.length; i++) {
            var armor = livingEntity.getItemBySlot(equipment$armor[i]);
            var structure = EquipmentStructureApi.structure(armor).orElse(null);
            changed |= equipment$observed[i * 2] != armor || equipment$observed[i * 2 + 1] != structure;
            equipment$observed[i * 2] = armor; equipment$observed[i * 2 + 1] = structure;
        }
        if (changed) ((CuriosQueryCache) curioInventory).equipment$invalidateQueries();
    }
}
