package dev.equipmentstructure.api.event;

import dev.equipmentstructure.api.EquipmentStructure;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired once after the entire pure migration chain is validated, before writing the item.
 * Cancellation leaves the original snapshot in place. Listeners should inspect/cancel, not
 * recursively migrate or mutate the item; a replaced snapshot makes the outer operation conflict.
 */
public final class EquipmentStructureMigrationEvent extends Event implements ICancellableEvent {
    private final ItemStack stack;
    private final EquipmentStructure previous;
    private final EquipmentStructure proposed;

    public EquipmentStructureMigrationEvent(ItemStack stack, EquipmentStructure previous,
                                            EquipmentStructure proposed) {
        this.stack = stack;
        this.previous = previous;
        this.proposed = proposed;
    }

    public ItemStack stack() { return stack; }
    public EquipmentStructure previous() { return previous; }
    public EquipmentStructure proposed() { return proposed; }
}
