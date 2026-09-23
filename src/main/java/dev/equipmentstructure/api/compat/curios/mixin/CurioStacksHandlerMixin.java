package dev.equipmentstructure.api.compat.curios.mixin;

import dev.equipmentstructure.api.compat.curios.ArmorCurioStackHandler;
import dev.equipmentstructure.api.compat.curios.CuriosArmorCompat;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.common.inventory.CurioStacksHandler;
import top.theillusivec4.curios.common.inventory.DynamicStackHandler;
import java.util.function.Function;

@Mixin(value = CurioStacksHandler.class, remap = false)
abstract class CurioStacksHandlerMixin implements dev.equipmentstructure.api.compat.curios.CuriosBindingCapacity {
    @Shadow private int baseSize;
    @Shadow @Final private java.util.Map<net.minecraft.resources.ResourceLocation, net.minecraft.world.entity.ai.attributes.AttributeModifier> persistentModifiers;
    @Unique private int equipment$clientStableCapacity = -1;
    @Override public int equipment$stableCapacity() {
        if (itemHandler != null && itemHandler.getWearer().level().isClientSide() && equipment$clientStableCapacity >= 0)
            return equipment$clientStableCapacity;
        // Curios 9.5.1 operation semantics, excluding transient grants. This is an installation
        // safety bound, not a replacement for Curios' live capacity or modifier processing.
        double size = baseSize;
        for (var modifier : persistentModifiers.values()) {
            if (dev.equipmentstructure.api.compat.curios.CuriosCompatibility.conditionalSlotModifier(modifier.id())) continue;
            if (modifier.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) size += modifier.amount();
            else if (modifier.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE) size += baseSize * modifier.amount();
        }
        for (var modifier : persistentModifiers.values())
            if (!dev.equipmentstructure.api.compat.curios.CuriosCompatibility.conditionalSlotModifier(modifier.id())
                    && modifier.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) size *= modifier.amount();
        return Math.max(0, Math.min(128, (int) size));
    }
    @Inject(method = "getSyncTag", at = @At("RETURN"))
    private void equipment$syncCapacity(CallbackInfoReturnable<net.minecraft.nbt.CompoundTag> callback) {
        callback.getReturnValue().putInt("equipment_structure_api:stable_capacity", equipment$stableCapacity());
    }
    @Inject(method = "applySyncTag", at = @At("RETURN"))
    private void equipment$readCapacity(net.minecraft.nbt.CompoundTag tag, CallbackInfo callback) {
        equipment$clientStableCapacity = tag.contains("equipment_structure_api:stable_capacity")
                ? tag.getInt("equipment_structure_api:stable_capacity") : -1;
    }
    @Shadow @Final private ICuriosItemHandler itemHandler;
    @Shadow @Final private String identifier;
    @Shadow @Final @Mutable private IDynamicStackHandler stackHandler;
    @Shadow @Final @Mutable private IDynamicStackHandler cosmeticStackHandler;
    @Shadow public abstract net.minecraft.core.NonNullList<Boolean> getRenders();
    @Inject(method = "<init>(Ltop/theillusivec4/curios/api/type/capability/ICuriosItemHandler;Ljava/lang/String;IZZZLtop/theillusivec4/curios/api/type/capability/ICurio$DropRule;)V", at = @At("RETURN"))
    private void equipment$storage(ICuriosItemHandler handler, String type, int size,
            boolean visible, boolean cosmetic, boolean toggles, ICurio.DropRule dropRule, CallbackInfo ci) {
        stackHandler = new ArmorCurioStackHandler(size, index -> handler == null ? null : new SlotContext(type, handler.getWearer(), index, false, getRenders().get(index)));
        cosmeticStackHandler = new ArmorCurioStackHandler(size, index -> handler == null ? null : new SlotContext(type, handler.getWearer(), index, true, getRenders().get(index)));
    }
}
