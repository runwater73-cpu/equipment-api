package dev.equipmentstructure.api.compat.curios;

import dev.equipmentstructure.api.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.common.inventory.DynamicStackHandler;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Function;

/** Native Curios working inventory backed by equipment. Previous stacks, validators and extraction remain native. */
public final class ArmorCurioStackHandler extends DynamicStackHandler {
    private static final Map<ItemStack, List<WeakReference<ArmorCurioStackHandler>>> OWNERS = new WeakHashMap<>();
    private final Map<Integer, Lease> leases = new HashMap<>();
    private boolean resolving;
    private boolean flushing;
    private boolean frozen;
    private final Set<Integer> retained = new HashSet<>();
    private static volatile boolean tracked;

    public ArmorCurioStackHandler(int size, Function<Integer, SlotContext> contexts) { super(size, contexts); }
    private boolean managed(int slot) {
        if (CuriosArmorCompat.nativeLoading()) return false;
        var context = ctxBuilder.apply(slot);
        return CuriosArmorCompat.manages(context) && !context.entity().level().isClientSide();
    }
    @Override public ItemStack getStackInSlot(int slot) {
        validateSlotIndex(slot);
        var hypothetical = CuriosValidationView.item(ctxBuilder.apply(slot), stacks.get(slot));
        if (hypothetical != null) return hypothetical;
        if (!resolving && managed(slot)) refresh(slot);
        else if (!resolving && !CuriosArmorCompat.nativeLoading() && leases.containsKey(slot)
                && !ctxBuilder.apply(slot).entity().level().isClientSide()) {
            flush(); leases.remove(slot); stacks.set(slot, ItemStack.EMPTY);
        }
        return super.getStackInSlot(slot);
    }
    private void refresh(int slot) {
        if (retained.contains(slot)) return;
        resolving = true;
        try {
            var context = ctxBuilder.apply(slot);
            if (!context.cosmetic() && !leases.containsKey(slot)) {
                var nativeItem = stacks.get(slot);
                if (PlayerBoundCurios.bound(nativeItem, context.entity())) return;
                if (PlayerBoundCurios.eligible(nativeItem, context.entity())) {
                    PlayerBoundCurios.mark(nativeItem, context.entity());
                    return;
                }
            }
            if (slot >= 128) { // Unsupported capacity is inaccessible, never an independent hidden inventory.
                if (!stacks.get(slot).isEmpty()) returnToPlayer(context, stacks.get(slot));
                stacks.set(slot, ItemStack.EMPTY); return;
            }
            var key = new CuriosSlotKey(context.identifier(), slot, context.cosmetic());
            var old = leases.get(slot);
            if (old != null) flushLease(slot, old);
            if (frozen && old != null) return;
            var owner = CuriosArmorCompat.owner(context);
            var part = owner.isEmpty() ? null : EquipmentStructureApi.component(owner, key.slotId()).orElse(null);
            var definitions = dev.equipmentstructure.api.grid.GridDefinitions.registered();
            if (old != null && old.owner == owner && old.expected == part && old.definitions == definitions) return;
            if (old != null && old.owner == owner && part != null
                    && old.definitions == definitions
                    && ItemStack.matches(old.saved, CuriosComponentItems.decode(part, context.entity().registryAccess()))) {
                old.expected = part; // Pose/grid edits must not replace a third-party live ItemStack.
                return;
            }
            if (old != null) leases.remove(slot);
            else if (!stacks.get(slot).isEmpty()) {
                // First activation after installing this integration: recover pre-existing native inventory.
                var loose = stacks.get(slot);
                stacks.set(slot, ItemStack.EMPTY);
                returnToPlayer(context, loose);
            }
            stacks.set(slot, ItemStack.EMPTY);
            if (owner.isEmpty() || part == null) return;
            if (!dev.equipmentstructure.api.grid.GridTransactions.resolve(
                    EquipmentStructureApi.structure(owner).orElseThrow(), definitions).allowed()) return;
            var item = CuriosComponentItems.decode(part, context.entity().registryAccess());
            if (item.isEmpty()) return;
            // Test with this slot absent; duplicate-item validators must not see their own candidate.
            if (!CuriosArmorCompat.canEquipOriginal(context, item, owner)) return;
            var lease = new Lease(owner, key, part, item, item.copy(), definitions);
            if (!context.cosmetic() && PlayerBoundCurios.eligible(item, context.entity())) {
                if (!PlayerBoundCurios.stableSlot(context.entity(), key)) return;
                // Commit the transfer before Curios runs its first onEquip/curioTick callback.
                discard(lease);
                PlayerBoundCurios.mark(item, context.entity());
                stacks.set(slot, item);
                return;
            }
            leases.put(slot, lease);
            stacks.set(slot, item);
            synchronized (OWNERS) {
                var readers = OWNERS.computeIfAbsent(owner, ignored -> new ArrayList<>());
                readers.removeIf(reference -> reference.get() == null);
                if (readers.stream().noneMatch(reference -> reference.get() == this)) readers.add(new WeakReference<>(this));
                tracked = true;
            }
        } finally { resolving = false; }
    }

    @Override public void setStackInSlot(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        if (resolving || !managed(slot)) { super.setStackInSlot(slot, stack); return; }
        if (slot >= 128) { super.setStackInSlot(slot, ItemStack.EMPTY); return; }
        refresh(slot);
        var nativeContext = ctxBuilder.apply(slot);
        if (PlayerBoundCurios.granting() && !nativeContext.cosmetic() && stacks.get(slot).isEmpty()
                && stack.getCount() == 1 && PlayerBoundCurios.eligible(stack, nativeContext.entity())) {
            PlayerBoundCurios.mark(stack, nativeContext.entity());
            super.setStackInSlot(slot, stack);
            return;
        }
        if (PlayerBoundCurios.bound(stacks.get(slot), nativeContext.entity())) {
            // Native death, consumption and unbinding/transformations retain their original authority.
            // A native transformation keeps the existing player-owned location, even if its new
            // item was not separately declared. The replacement's own removal rules now apply.
            if (!stack.isEmpty()) PlayerBoundCurios.mark(stack, nativeContext.entity());
            super.setStackInSlot(slot, stack);
            PlayerBoundCurios.invalidate(nativeContext.entity());
            return;
        }
        retained.remove(slot);
        var context = ctxBuilder.apply(slot);
        var lease = leases.get(slot);
        var owner = lease != null ? lease.owner : CuriosArmorCompat.owner(context);
        var key = new CuriosSlotKey(context.identifier(), slot, context.cosmetic());
        if (stack.isEmpty()) {
            if (lease != null) discard(lease);
            leases.remove(slot);
            super.setStackInSlot(slot, ItemStack.EMPTY);
            PlayerBoundCurios.invalidate(context.entity());
            return;
        }
        if (lease == null) return; // Direct native setters cannot create a new installation.
        if (owner.isEmpty()) return;
        if (!ItemStack.isSameItem(lease.live, stack) || stack.getCount() != 1) {
            // Setters are also used for consumed/recharged/transformed accessories, not just quick equip.
            // Native authority replaces the old item; a replacement that no longer fits is recovered once.
            var before = EquipmentStructureApi.structure(owner).orElseThrow();
            var replacement = CuriosComponentItems.encode(stack, key, context.entity().registryAccess(), CuriosArmorCompat.definitions(context.entity()))
                    .map(part -> dev.equipmentstructure.api.appearance.AppearancePartPresentation.read(lease.expected).apply(part));
            var plan = replacement.map(part -> dev.equipmentstructure.api.grid.GridTransactions.install(before, key.slotId(), part,
                    Optional.empty(), dev.equipmentstructure.api.grid.GridDefinitions.registered())).orElse(null);
            if (stack.getCount() == 1 && plan != null && plan.allowed()) {
                EquipmentStructureApi.setStructure(owner, plan.structure());
                lease.expected = plan.structure().component(key.slotId()).orElseThrow();
                lease.live = stack; lease.saved = stack.copy();
                super.setStackInSlot(slot, stack);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new dev.equipmentstructure.api.event.EquipmentStructureChangedEvent(
                        owner, before, plan.structure(), dev.equipmentstructure.api.event.EquipmentStructureChangedEvent.ChangeType.REPLACED));
            } else {
                discard(lease); leases.remove(slot); super.setStackInSlot(slot, ItemStack.EMPTY);
                returnToPlayer(context, stack);
            }
            PlayerBoundCurios.invalidate(context.entity());
            return;
        }
        // Third-party data/durability updates keep geometry, visibility and the user's 3D adjustment.
        lease.live = stack;
        flushLease(slot, lease);
        super.setStackInSlot(slot, stack);
        PlayerBoundCurios.invalidate(context.entity());
    }

    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!managed(slot)) return super.insertItem(slot, stack, simulate);
        return stack; // External automation and quick-equip must not bypass the equipment screen.
    }
    @Override public boolean isItemValid(int slot, ItemStack stack) {
        return super.isItemValid(slot, stack);
    }

    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!managed(slot)) return super.extractItem(slot, amount, simulate);
        getStackInSlot(slot);
        if (!leases.containsKey(slot)) return PlayerBoundCurios.released(super.extractItem(slot, amount, simulate));
        // Let Curios validate first, then commit storage before returning any item to the caller.
        var extracted = super.extractItem(slot, amount, true);
        if (!simulate && !extracted.isEmpty()) {
            var lease = leases.get(slot);
            if (lease != null) {
                if (EquipmentStructureApi.component(lease.owner, lease.key.slotId()).orElse(null) != lease.expected
                        || EquipmentStructureApi.remove(lease.owner, lease.key.slotId()).isEmpty()) return ItemStack.EMPTY;
                leases.remove(slot);
                super.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        return extracted;
    }

    public void flush() {
        if (flushing) return;
        for (var entry : List.copyOf(leases.entrySet())) flushLease(entry.getKey(), entry.getValue());
    }
    /** Explicit native backup restoration, separate from player quick-equip and automation. */
    public void restore(int slot, ItemStack incoming) {
        if (incoming.isEmpty()) return;
        if (!managed(slot)) { super.setStackInSlot(slot, incoming); return; }
        var context = ctxBuilder.apply(slot);
        var current = getStackInSlot(slot);
        if (ItemStack.matches(current, incoming)) return; // A snapshot of an unchanged owner is not another item.
        if (current.isEmpty()) {
            var key = new CuriosSlotKey(context.identifier(), slot, context.cosmetic());
            if (PlayerBoundCurios.bound(incoming, context.entity()) && !key.cosmetic()) {
                stacks.set(slot, incoming); PlayerBoundCurios.invalidate(context.entity()); return;
            }
            var owner = CuriosArmorCompat.owner(context);
            if (!owner.isEmpty() && incoming.getCount() == 1) {
                var part = CuriosComponentItems.encode(incoming, key, context.entity().registryAccess(), CuriosArmorCompat.definitions(context.entity()));
                if (part.isPresent() && EquipmentStructureApi.install(owner, key.slotId(), part.get()) == EquipmentStructureApi.InstallResult.INSTALLED) {
                    refresh(slot); PlayerBoundCurios.invalidate(context.entity()); return;
                }
            }
        }
        returnToPlayer(context, incoming);
    }
    private void flushLease(int slot, Lease lease) {
        if (flushing) return;
        flushing = true;
        try {
            if (EquipmentStructureApi.component(lease.owner, lease.key.slotId()).orElse(null) != lease.expected
                    || ItemStack.matches(lease.live, lease.saved)) return;
            if (lease.live.isEmpty()) {
                discard(lease);
                leases.remove(slot);
            } else {
                var encoded = lease.live.save(ctxBuilder.apply(slot).entity().registryAccess());
                var result = EquipmentStructureApi.updateComponentData(lease.owner, lease.key.slotId(), lease.expected,
                        data -> { data.put(CuriosComponentItems.STACK, encoded); return data; });
                if (result == EquipmentStructureApi.ComponentDataResult.UPDATED || result == EquipmentStructureApi.ComponentDataResult.UNCHANGED) {
                    lease.expected = EquipmentStructureApi.component(lease.owner, lease.key.slotId()).orElseThrow();
                    lease.saved = lease.live.copy();
                }
            }
        } finally { flushing = false; }
    }
    private static void discard(Lease lease) {
        var before = EquipmentStructureApi.structure(lease.owner).orElse(null);
        if (before == null || before.component(lease.key.slotId()).orElse(null) != lease.expected) return;
        var after = dev.equipmentstructure.api.grid.GridTransactions.remove(before, lease.key.slotId(),
                dev.equipmentstructure.api.grid.GridDefinitions.registered());
        EquipmentStructureApi.setStructure(lease.owner, after);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new dev.equipmentstructure.api.event.EquipmentStructureChangedEvent(
                lease.owner, before, after, dev.equipmentstructure.api.event.EquipmentStructureChangedEvent.ChangeType.REMOVED));
    }
    @Override public void shrink(int amount) {
        int size = Math.max(0, getSlots() - amount);
        for (int i = size; i < getSlots(); i++) {
            // Curios already recovers functional overflow. It does not recover cosmetic overflow.
            if (managed(i) && ctxBuilder.apply(i).cosmetic()) {
                var item = getStackInSlot(i).copy();
                if (!item.isEmpty()) { setStackInSlot(i, ItemStack.EMPTY); returnToPlayer(ctxBuilder.apply(i), item); }
            }
            leases.remove(i); retained.remove(i);
        }
        super.shrink(amount);
    }
    public void freeze() {
        for (int i = 0; i < getSlots(); i++) getStackInSlot(i);
        flush(); frozen = true;
    }
    public void thaw() { frozen = false; }
    public ItemStack boundOwner(int index) {
        var lease = leases.get(index); return lease == null ? ItemStack.EMPTY : lease.owner;
    }
    public void rebindDroppedOwner(int index, ItemStack dropped) {
        var lease = leases.get(index);
        if (lease != null && dropped != lease.owner) {
            lease.owner = dropped;
            lease.expected = EquipmentStructureApi.component(dropped, lease.key.slotId()).orElse(null);
        }
    }
    /** Transfer ownership to Curios' native retained inventory for its normal player-clone path. */
    public void retainAfterDeath(int index) {
        var item = getStackInSlot(index);
        if (item.isEmpty() || !owns(index)) return;
        setStackInSlot(index, ItemStack.EMPTY);
        stacks.set(index, item);
        retained.add(index);
    }
    public boolean owns(int index) { return leases.containsKey(index); }
    public static void beforeCopy(ItemStack owner) {
        if (!tracked || !owner.has(EquipmentStructureDataComponents.EQUIPMENT_STRUCTURE.get())) return;
        List<WeakReference<ArmorCurioStackHandler>> readers;
        synchronized (OWNERS) { readers = OWNERS.containsKey(owner) ? List.copyOf(OWNERS.get(owner)) : List.of(); }
        for (var reader : readers) { var handler = reader.get(); if (handler != null) handler.flush(); }
    }
    static void clearTracking() { synchronized (OWNERS) { OWNERS.clear(); tracked = false; } }
    private static void returnToPlayer(SlotContext context, ItemStack stack) {
        if (context.entity() instanceof Player player) player.getInventory().placeItemBackInInventory(stack.copy());
    }
    private static final class Lease {
        ItemStack owner; final CuriosSlotKey key;
        EquipmentComponentInstance expected; ItemStack live; ItemStack saved;
        final dev.equipmentstructure.api.grid.GridDefinitions definitions;
        Lease(ItemStack owner, CuriosSlotKey key, EquipmentComponentInstance expected, ItemStack live, ItemStack saved,
                dev.equipmentstructure.api.grid.GridDefinitions definitions) {
            this.owner = owner; this.key = key; this.expected = expected; this.live = live; this.saved = saved;
            this.definitions = definitions;
        }
    }
}
