package dev.equipmentstructure.api.event;

import dev.equipmentstructure.api.EquipmentStructure;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Before dynamic slot reconciliation. Occupied slots and all component/grid data must be preserved. */
public final class EquipmentSlotsReconcileEvent extends Event implements ICancellableEvent {
    private final ItemStack equipment;
    private final EquipmentStructure previous, next;
    public EquipmentSlotsReconcileEvent(ItemStack equipment, EquipmentStructure previous, EquipmentStructure next) {
        this.equipment = equipment; this.previous = previous; this.next = next;
    }
    public ItemStack equipment() { return equipment; }
    public EquipmentStructure previous() { return previous; }
    public EquipmentStructure next() { return next; }
}
