package dev.equipmentstructure.api.grid.space;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.grid.*;
import dev.equipmentstructure.api.event.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class ComponentSpaceTransactions {
    private ComponentSpaceTransactions() {}
    public enum Action { CLICK, MOVE }
    public record Result(boolean applied, String reason) {}
    public static GridFootprint footprint(ComponentSpaceDefinition definition, ItemStack item) {
        return definition.itemShapes().getOrDefault(BuiltInRegistries.ITEM.getKey(item.getItem()), GridFootprint.fixed(GridShape.rectangle(1, 1)));
    }
    public static boolean fits(ComponentSpaceDefinition definition, List<ComponentSpaceContents.Entry> entries,
            int excluded, GridFootprint footprint, GridPlacement placement) {
        if (!footprint.rotations().contains(placement.rotation())) return false;
        var cells = new GridLayout.Part(footprint, placement).cells();
        if (!cells.stream().allMatch(definition.shape()::contains)) return false;
        for (int i = 0; i < entries.size(); i++) if (i != excluded && !Collections.disjoint(cells, entries.get(i).cells())) return false;
        return true;
    }
    public static boolean valid(ComponentSpaceDefinition definition, EquipmentComponentInstance part,
            ComponentSpaceContents.Space space, HolderLookup.Provider registries) {
        if (!definition.shape().equals(space.shape())) return false;
        for (int i = 0; i < space.entries().size(); i++) {
            var entry = space.entries().get(i); var item = entry.stack(registries);
            if (!ComponentSpaceRegistry.accepts(definition, part, item) || item.getCount() > Math.min(item.getMaxStackSize(), definition.stackLimit())
                    || !entry.footprint().equals(footprint(definition, item)) || !fits(definition, space.entries(), i, entry.footprint(), entry.placement())) return false;
        }
        return true;
    }

    /** Server thread. Only owned container setters may be passed as transfer; it must not throw. */
    public static Result execute(ItemStack equipment, ItemStack carried, ResourceLocation source, ResourceLocation spaceId,
            String token, Action action, int entryIndex, GridPlacement target, boolean single,
            HolderLookup.Provider registries, BooleanSupplier guard, Consumer<ItemStack> transfer) {
        var previous = EquipmentStructureApi.structure(equipment).orElse(null);
        if (previous == null) return rejected("no_structure");
        var part = previous.component(source).orElse(null);
        if (part == null) return rejected("missing_source");
        var definitions = GridDefinitions.registered();
        long generation = ComponentSpaceRegistry.generation();
        var definition = definitions.spaces().getOrDefault(part.id(), List.of()).stream().filter(d -> d.id().equals(spaceId)).findFirst().orElse(null);
        EquipmentStructure changed;
        ItemStack remainder = carried.copy();
        try {
            var all = ComponentSpaceContents.read(part);
            if (!all.token().equals(token)) return rejected("stale_source");
            var stored = all.spaces().getOrDefault(spaceId, definition == null ? null : new ComponentSpaceContents.Space(definition.shape(), List.of()));
            if (stored == null) return rejected("missing_space");
            var entries = new ArrayList<>(stored.entries());
            int hit = action == Action.MOVE ? entryIndex : entryIndex >= 0 ? entryIndex : stored.at(new GridCell(target.x(), target.y()));
            if (hit >= entries.size() || hit < -1) return rejected("missing_entry");
            boolean recovery = definition == null || !valid(definition, part, stored, registries)
                    || GridTransactions.resolve(previous, definitions).status() != GridTransactions.Status.VALID;
            if (action == Action.MOVE) {
                if (recovery || !carried.isEmpty() || hit < 0) return rejected("move_rejected");
                var entry = entries.get(hit);
                if (!fits(definition, entries, hit, entry.footprint(), target)) return rejected("collision");
                entries.set(hit, entry.moved(target));
            } else if (carried.isEmpty()) {
                if (hit < 0) return new Result(true, "unchanged");
                var entry = entries.get(hit); var item = entry.stack(registries);
                if (item.isEmpty()) return rejected("unknown_item_retained");
                int count = single ? (item.getCount() + 1) / 2 : item.getCount();
                remainder = item.copyWithCount(count); item.shrink(count);
                if (item.isEmpty()) entries.remove(hit);
                else entries.set(hit, entry(item, entry.footprint(), entry.placement(), registries));
            } else {
                if (recovery || entryIndex >= 0 || !ComponentSpaceRegistry.accepts(definition, part, carried)) return rejected("filter_or_recovery");
                var footprint = footprint(definition, carried);
                int limit = Math.min(carried.getMaxStackSize(), definition.stackLimit());
                if (hit >= 0) {
                    var old = entries.get(hit); var item = old.stack(registries);
                    if (ItemStack.isSameItemSameComponents(item, carried)) {
                        int amount = Math.min(single ? 1 : carried.getCount(), limit - item.getCount());
                        if (amount <= 0) return rejected("full");
                        item.grow(amount); remainder.shrink(amount);
                        entries.set(hit, entry(item, old.footprint(), old.placement(), registries));
                    } else {
                        var placement = new GridPlacement(old.placement().x(), old.placement().y(), target.rotation());
                        if (single || carried.getCount() > limit || !fits(definition, entries, hit, footprint, placement)) return rejected("swap_rejected");
                        entries.set(hit, entry(carried, footprint, placement, registries)); remainder = item;
                    }
                } else {
                    if (entries.size() >= ComponentSpaceContents.MAX_ENTRIES || !fits(definition, entries, -1, footprint, target)) return rejected("collision_or_capacity");
                    int amount = Math.min(single ? 1 : carried.getCount(), limit);
                    entries.add(entry(carried.copyWithCount(amount), footprint, target, registries)); remainder.shrink(amount);
                }
            }
            var updated = all.with(spaceId, new ComponentSpaceContents.Space(entries.isEmpty() && definition != null ? definition.shape() : stored.shape(), entries)).apply(part);
            var parts = new HashMap<>(previous.components()); parts.put(source, List.of(updated));
            changed = new EquipmentStructure(previous.hostId(), previous.equipmentType(),
                    previous.slots(), parts, previous.version(), previous.grid());
        } catch (RuntimeException invalid) {
            EquipmentStructureApiMod.LOGGER.warn("Component space transaction failed before transfer: {}/{}", source, spaceId, invalid);
            return rejected("invalid_data");
        }
        ItemStack output = remainder;
        return commit(equipment, previous, changed, definitions, generation, source, spaceId, guard, () -> transfer.accept(output));
    }
    /** Exact server-side consumption for author behaviors. Never callable by a client space packet. */
    public static Result consume(ItemStack equipment, EquipmentStructure expected, ResourceLocation source, ResourceLocation spaceId,
            int entryIndex, int amount, HolderLookup.Provider registries, BooleanSupplier guard) {
        if (amount < 1 || EquipmentStructureApi.structure(equipment).orElse(null) != expected || expected == null) return rejected("conflict");
        var definitions = GridDefinitions.registered(); long generation = ComponentSpaceRegistry.generation();
        var part = expected.component(source).orElse(null); if (part == null) return rejected("missing_source");
        var definition = definitions.spaces().getOrDefault(part.id(), List.of()).stream().filter(d -> d.id().equals(spaceId)).findFirst().orElse(null);
        EquipmentStructure changed;
        try {
            var all = ComponentSpaceContents.read(part); var stored = all.spaces().get(spaceId);
            if (definition == null || stored == null || entryIndex < 0 || entryIndex >= stored.entries().size()
                    || !valid(definition, part, stored, registries) || GridTransactions.resolve(expected, definitions).status() != GridTransactions.Status.VALID) return rejected("invalid_space");
            var entries = new ArrayList<>(stored.entries()); var entry = entries.get(entryIndex); var stack = entry.stack(registries);
            if (stack.getCount() < amount) return rejected("insufficient_items");
            stack.shrink(amount);
            if (stack.isEmpty()) entries.remove(entryIndex); else entries.set(entryIndex, entry(stack, entry.footprint(), entry.placement(), registries));
            var updated = all.with(spaceId, new ComponentSpaceContents.Space(stored.shape(), entries)).apply(part);
            var parts = new HashMap<>(expected.components()); parts.put(source, List.of(updated));
            changed = new EquipmentStructure(expected.hostId(), expected.equipmentType(),
                    expected.slots(), parts, expected.version(), expected.grid());
        } catch (RuntimeException invalid) { return rejected("invalid_data"); }
        return commit(equipment, expected, changed, definitions, generation, source, spaceId, guard, () -> {});
    }
    private static Result commit(ItemStack equipment, EquipmentStructure previous, EquipmentStructure changed, GridDefinitions definitions,
            long generation, ResourceLocation source, ResourceLocation space, BooleanSupplier guard, Runnable transfer) {
        long beforeBytes = ComponentSpaceContents.storedBytes(previous), afterBytes = ComponentSpaceContents.storedBytes(changed);
        // Bound the whole host, while allowing extraction from an oversized snapshot.
        if (afterBytes > ComponentSpaceContents.MAX_BYTES && afterBytes >= beforeBytes) return rejected("host_storage_limit");
        if (NeoForge.EVENT_BUS.post(new EquipmentSpaceChangeEvent(equipment, previous, changed, source, space)).isCanceled()) return rejected("cancelled");
        if (!guard.getAsBoolean() || EquipmentStructureApi.structure(equipment).orElse(null) != previous
                || generation != ComponentSpaceRegistry.generation() || !definitions.equals(GridDefinitions.registered())) return rejected("conflict");
        EquipmentStructureApi.setStructure(equipment, changed); transfer.run();
        NeoForge.EVENT_BUS.post(new EquipmentStructureChangedEvent(equipment, previous, changed, EquipmentStructureChangedEvent.ChangeType.COMPONENT_DATA_UPDATED));
        return new Result(true, "applied");
    }
    private static ComponentSpaceContents.Entry entry(ItemStack item, GridFootprint footprint, GridPlacement placement, HolderLookup.Provider registries) {
        return new ComponentSpaceContents.Entry((CompoundTag) item.save(registries), footprint, placement);
    }
    private static Result rejected(String reason) { return new Result(false, reason); }
}
