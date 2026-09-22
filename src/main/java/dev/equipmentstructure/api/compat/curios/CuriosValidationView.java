package dev.equipmentstructure.api.compat.curios;

import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.EquipmentStructureApi;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import java.util.HashMap;
import java.util.function.BooleanSupplier;

/** Read-only hypothetical wearer used by native validators while assembling unworn armor. */
final class CuriosValidationView {
    private record Scope(SlotContext candidate, ItemStack equipment, EquipmentStructure structure,
                         HashMap<CuriosSlotKey, ItemStack> items) {}
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private CuriosValidationView() {}
    static boolean validate(SlotContext context, ItemStack equipment, BooleanSupplier validation) {
        var previous = CURRENT.get();
        CURRENT.set(new Scope(context, equipment, EquipmentStructureApi.structure(equipment).orElse(null), new HashMap<>()));
        invalidate(context);
        try { return validation.getAsBoolean(); }
        finally { CURRENT.set(previous); invalidate(context); }
    }
    private static void invalidate(SlotContext context) {
        CuriosApi.getCuriosInventory(context.entity()).ifPresent(inv -> {
            if (inv instanceof CuriosQueryCache cache) cache.equipment$invalidateQueries();
        });
    }
    /** null delegates to real storage; empty is an intentionally absent hypothetical item. */
    static ItemStack item(SlotContext context, ItemStack nativeItem) {
        var scope = CURRENT.get();
        if (scope == null || context == null || scope.structure == null || context.entity() != scope.candidate.entity()) return null;
        var profile = CuriosArmorCompat.definitions(context.entity()).profile(context.identifier());
        if (profile.armorSlot() != CuriosEquipmentTemplates.position(scope.equipment)
                || profile.template().filter(id -> !id.equals(scope.structure.hostId())).isPresent()) return null;
        if (PlayerBoundCurios.bound(nativeItem, context.entity())) return nativeItem;
        if (context.identifier().equals(scope.candidate.identifier()) && context.index() == scope.candidate.index()
                && context.cosmetic() == scope.candidate.cosmetic()) return ItemStack.EMPTY;
        var key = new CuriosSlotKey(context.identifier(), context.index(), context.cosmetic());
        return scope.items.computeIfAbsent(key, ignored -> scope.structure.component(key.slotId())
                .map(part -> CuriosComponentItems.decode(part, context.entity().registryAccess())).orElse(ItemStack.EMPTY));
    }
}
