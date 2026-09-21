package dev.equipmentstructure.api.event;

import dev.equipmentstructure.api.EquipmentStructure;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Before a complete container transaction. Snapshots cannot mutate the live carried item. */
public final class EquipmentSpaceChangeEvent extends Event implements ICancellableEvent {
    private final EquipmentStructure previous, current;
    private final ItemStack equipment;
    private final ResourceLocation slot, space;
    public EquipmentSpaceChangeEvent(ItemStack equipment, EquipmentStructure previous, EquipmentStructure current, ResourceLocation slot, ResourceLocation space) {
        this.equipment = equipment.copy(); this.previous = previous; this.current = current; this.slot = slot; this.space = space;
    }
    public ItemStack equipment() { return equipment.copy(); }
    public EquipmentStructure previous() { return previous; }
    public EquipmentStructure current() { return current; }
    public ResourceLocation slot() { return slot; }
    public ResourceLocation space() { return space; }
}
