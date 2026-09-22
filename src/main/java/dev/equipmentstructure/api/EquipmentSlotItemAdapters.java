package dev.equipmentstructure.api;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Destination-aware integrations. An external slot never changes an item's ordinary component binding. */
public final class EquipmentSlotItemAdapters {
    public interface Adapter {
        boolean handles(EquipmentSlotDefinition slot);
        Optional<EquipmentComponentInstance> read(ItemStack input, ItemStack equipment,
                EquipmentSlotDefinition slot, LivingEntity actor);
        /** Empty delegates to the ordinary component round-trip check. */
        default Optional<Boolean> restores(EquipmentComponentInstance part, ItemStack restored) { return Optional.empty(); }
        default void prepare(ItemStack equipment, RegistryAccess registries, LivingEntity actor) {}
        default boolean canRemove(ItemStack equipment, EquipmentSlotDefinition slot, LivingEntity actor) { return true; }
        default void beforeBreak(ItemStack equipment, LivingEntity actor) {}
        /** Read-only display of a player-owned item reserving this external slot. Never stored on equipment. */
        default ItemStack playerOwnedItem(EquipmentSlotDefinition slot, LivingEntity actor) { return ItemStack.EMPTY; }
        /** Must enforce the original item's removal policy and transfer ownership exactly once. */
        default ItemStack removePlayerOwnedItem(EquipmentSlotDefinition slot, LivingEntity actor) { return ItemStack.EMPTY; }
    }

    private static final List<Adapter> ADAPTERS = new CopyOnWriteArrayList<>();
    private record Callback(Adapter adapter, String hook) {}
    private static final dev.equipmentstructure.api.internal.ExtensionGuard<Callback> GUARD =
            new dev.equipmentstructure.api.internal.ExtensionGuard<>("Equipment slot integration");
    private EquipmentSlotItemAdapters() {}

    public static AutoCloseable register(Adapter adapter) {
        java.util.Objects.requireNonNull(adapter);
        ADAPTERS.add(adapter);
        return () -> ADAPTERS.remove(adapter);
    }

    public static Optional<EquipmentComponentInstance> read(ItemStack input, ItemStack equipment,
            ResourceLocation slotId, LivingEntity actor) {
        var slot = EquipmentStructureApi.structure(equipment).flatMap(s -> s.slot(slotId)).orElse(null);
        if (slot == null) return Optional.empty();
        for (var adapter : ADAPTERS) {
            if (GUARD.call(new Callback(adapter, "handles"), () -> adapter.handles(slot), true))
                return GUARD.call(new Callback(adapter, "read"), () -> adapter.read(input.copyWithCount(1), equipment, slot, actor), Optional.empty());
        }
        return EquipmentComponentRegistry.fromItemStack(input);
    }

    public static Optional<Boolean> restores(EquipmentComponentInstance part, ItemStack restored) {
        for (var adapter : ADAPTERS) {
            var answer = GUARD.call(new Callback(adapter, "restores"), () -> adapter.restores(part, restored.copy()), Optional.of(false));
            if (answer.isPresent()) return answer;
        }
        return Optional.empty();
    }

    public static void prepare(ItemStack equipment, RegistryAccess registries, LivingEntity actor) {
        if (equipment.isEmpty() || actor != null && actor.level().isClientSide()) return;
        for (var adapter : ADAPTERS) GUARD.call(new Callback(adapter, "prepare"), () -> { adapter.prepare(equipment, registries, actor); return true; }, false);
    }
    public static boolean canRemove(ItemStack equipment, ResourceLocation slotId, LivingEntity actor) {
        var slot = EquipmentStructureApi.structure(equipment).flatMap(s -> s.slot(slotId)).orElse(null);
        if (slot == null) return false;
        for (var adapter : ADAPTERS) if (!GUARD.call(new Callback(adapter, "canRemove"),
                () -> !adapter.handles(slot) || adapter.canRemove(equipment, slot, actor), false)) return false;
        return true;
    }
    public static boolean beforeBreak(ItemStack equipment, LivingEntity actor) {
        for (var adapter : ADAPTERS) if (!GUARD.call(new Callback(adapter, "beforeBreak"),
                () -> { adapter.beforeBreak(equipment, actor); return true; }, false)) return false;
        return true;
    }
    public static ItemStack playerOwnedItem(ItemStack equipment, ResourceLocation slotId, LivingEntity actor) {
        return playerItem(equipment, slotId, actor, false);
    }
    public static ItemStack removePlayerOwnedItem(ItemStack equipment, ResourceLocation slotId, LivingEntity actor) {
        if (actor == null || actor.level().isClientSide()) return ItemStack.EMPTY;
        return playerItem(equipment, slotId, actor, true);
    }
    private static ItemStack playerItem(ItemStack equipment, ResourceLocation slotId, LivingEntity actor, boolean remove) {
        var slot = EquipmentStructureApi.structure(equipment).flatMap(s -> s.slot(slotId)).orElse(null);
        if (slot == null) return ItemStack.EMPTY;
        for (var adapter : ADAPTERS) if (GUARD.call(new Callback(adapter, "handles"), () -> adapter.handles(slot), false))
            return GUARD.call(new Callback(adapter, remove ? "removePlayerOwned" : "playerOwned"),
                    () -> remove ? adapter.removePlayerOwnedItem(slot, actor) : adapter.playerOwnedItem(slot, actor), ItemStack.EMPTY);
        return ItemStack.EMPTY;
    }
}
