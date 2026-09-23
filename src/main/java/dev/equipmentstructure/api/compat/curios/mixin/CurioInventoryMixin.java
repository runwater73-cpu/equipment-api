package dev.equipmentstructure.api.compat.curios.mixin;

import dev.equipmentstructure.api.compat.curios.ArmorCurioStackHandler;
import dev.equipmentstructure.api.compat.curios.CuriosArmorCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.capability.CurioInventory;
import java.util.Map;

@Mixin(value = CurioInventory.class, remap = false)
abstract class CurioInventoryMixin implements dev.equipmentstructure.api.compat.curios.CuriosQueryCache {
    @Shadow @Final com.google.common.cache.Cache<String, com.mojang.datafixers.util.Pair<Long, java.util.Optional<top.theillusivec4.curios.api.SlotResult>>> firstCurioCache;
    @Shadow @Final com.google.common.cache.Cache<String, com.mojang.datafixers.util.Pair<Long, java.util.List<top.theillusivec4.curios.api.SlotResult>>> findCuriosCache;
    @Override public void equipment$invalidateQueries() { firstCurioCache.invalidateAll(); findCuriosCache.invalidateAll(); }
    @Shadow @Final Map<String, ICurioStacksHandler> curios;
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "init")
    private void equipment$nativeLoad(ICuriosItemHandler handler,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        CuriosArmorCompat.beginNativeLoad();
        try { original.call(handler); } finally { CuriosArmorCompat.endNativeLoad(); }
    }
    @Inject(method = "serializeNBT(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;", at = @At("HEAD"))
    private void equipment$flush(HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
        for (var handler : curios.values()) {
            if (handler.getStacks() instanceof ArmorCurioStackHandler stacks) stacks.flush();
            if (handler.getCosmeticStacks() instanceof ArmorCurioStackHandler stacks) stacks.flush();
        }
    }
    @Inject(method = "serializeNBT(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"), cancellable = true)
    private void equipment$singleOwner(HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
        var result = cir.getReturnValue().copy();
        for (var element : result.getList("Curios", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) element;
            var handler = curios.get(entry.getString("Identifier"));
            if (handler == null) continue;
            var stored = entry.getCompound("StacksHandler");
            if (handler.getStacks() instanceof ArmorCurioStackHandler stacks) strip(stored.getCompound("Stacks"), stacks);
            if (handler.getCosmeticStacks() instanceof ArmorCurioStackHandler stacks) strip(stored.getCompound("Cosmetics"), stacks);
        }
        cir.setReturnValue(result);
    }
    @Unique private static void strip(CompoundTag stored, ArmorCurioStackHandler stacks) {
        stored.getList("Items", Tag.TAG_COMPOUND).removeIf(item -> stacks.owns(((CompoundTag) item).getInt("Slot")));
    }
}
