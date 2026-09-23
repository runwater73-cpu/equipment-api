package dev.equipmentstructure.api.compat.curios;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.registries.BuiltInRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

/** Explicit ownership exception. Native Curios inventory remains the sole save/effect owner. */
public final class PlayerBoundCurios {
    private static final String OWNER = "equipment_structure_api:bound_player";
    private static final ThreadLocal<Boolean> GRANT = ThreadLocal.withInitial(() -> false);
    private PlayerBoundCurios() {}
    public static <T> T nativeGrant(java.util.function.Supplier<T> operation) {
        boolean previous = GRANT.get(); GRANT.set(true);
        try { return operation.get(); } finally { GRANT.set(previous); }
    }
    static boolean granting() { return GRANT.get(); }
    public static boolean stableSlot(LivingEntity wearer, CuriosSlotKey key) {
        return CuriosApi.getCuriosInventory(wearer).flatMap(inv -> inv.getStacksHandler(key.type()))
                .filter(handler -> handler instanceof CuriosBindingCapacity capacity && key.index() < capacity.equipment$stableCapacity())
                .filter(handler -> key.index() < handler.getSlots()).isPresent();
    }
    public static boolean install(LivingEntity wearer, CuriosSlotKey key, ItemStack input) {
        if (wearer.level().isClientSide() || key.cosmetic() || !eligible(input, wearer) || !stableSlot(wearer, key)) return false;
        var handler = CuriosApi.getCuriosInventory(wearer).flatMap(inv -> inv.getStacksHandler(key.type())).orElse(null);
        if (handler == null || key.index() >= handler.getSlots() || !(handler.getStacks() instanceof ArmorCurioStackHandler stacks)) return false;
        var context = CuriosArmorCompat.context(wearer, key);
        if (!CuriosArmorCompat.manages(context) || !stacks.getStackInSlot(key.index()).isEmpty()
                || !CuriosArmorCompat.canEquipOriginal(context, input)) return false;
        var installed = input.copyWithCount(1);
        nativeGrant(() -> { stacks.setStackInSlot(key.index(), installed); return null; });
        boolean success = stacks.getStackInSlot(key.index()) == installed;
        if (success) invalidate(wearer);
        return success;
    }
    public static boolean eligible(ItemStack item, LivingEntity wearer) {
        return !item.isEmpty() && CuriosArmorCompat.definitions(wearer).playerBoundItems()
                .getOrDefault(BuiltInRegistries.ITEM.getKey(item.getItem()), false);
    }
    public static boolean bound(ItemStack item, LivingEntity wearer) {
        var data = item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (item.isEmpty() || !data.contains(OWNER)) return false;
        var tag = data.copyTag();
        return !item.isEmpty() && tag.hasUUID(OWNER) && tag.getUUID(OWNER).equals(wearer.getUUID());
    }
    static void mark(ItemStack item, LivingEntity wearer) {
        CustomData.update(DataComponents.CUSTOM_DATA, item, tag -> tag.putUUID(OWNER, wearer.getUUID()));
    }
    static ItemStack released(ItemStack item) {
        if (!item.isEmpty()) CustomData.update(DataComponents.CUSTOM_DATA, item, tag -> tag.remove(OWNER));
        return item;
    }
    public static ItemStack item(LivingEntity wearer, ResourceLocation id) {
        if (wearer == null) return ItemStack.EMPTY;
        var key = CuriosSlotKey.parse(id).orElse(null);
        if (key == null || key.cosmetic()) return ItemStack.EMPTY;
        var handler = CuriosApi.getCuriosInventory(wearer).flatMap(inv -> inv.getStacksHandler(key.type())).orElse(null);
        if (handler == null || key.index() >= handler.getStacks().getSlots()) return ItemStack.EMPTY;
        var item = handler.getStacks().getStackInSlot(key.index());
        return bound(item, wearer) ? item : ItemStack.EMPTY;
    }
    public static ItemStack remove(LivingEntity wearer, ResourceLocation id) {
        if (wearer == null || wearer.level().isClientSide() || item(wearer, id).isEmpty()) return ItemStack.EMPTY;
        var key = CuriosSlotKey.parse(id).orElseThrow();
        var result = CuriosApi.getCuriosInventory(wearer).flatMap(inv -> inv.getStacksHandler(key.type()))
                .map(handler -> handler.getStacks().extractItem(key.index(), 1, false)).orElse(ItemStack.EMPTY);
        if (!result.isEmpty()) invalidate(wearer);
        return result;
    }
    static void invalidate(LivingEntity wearer) {
        CuriosApi.getCuriosInventory(wearer).ifPresent(inv -> {
            if (inv instanceof CuriosQueryCache cache) cache.equipment$invalidateQueries();
        });
    }
}
