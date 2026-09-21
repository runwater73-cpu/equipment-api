package dev.equipmentstructure.api.event;

import dev.equipmentstructure.api.EquipmentStructure;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Cancellable layout-only edit/initialization. No component installation or removal is implied. */
public final class EquipmentGridChangeEvent extends Event implements ICancellableEvent {
    private final ItemStack stack;
    private final EquipmentStructure previous;
    private final EquipmentStructure proposed;

    public EquipmentGridChangeEvent(ItemStack stack, EquipmentStructure previous, EquipmentStructure proposed) {
        this.stack = stack;
        this.previous = previous;
        this.proposed = proposed;
    }
    public ItemStack stack() { return stack; }
    public EquipmentStructure previous() { return previous; }
    public EquipmentStructure proposed() { return proposed; }
}
