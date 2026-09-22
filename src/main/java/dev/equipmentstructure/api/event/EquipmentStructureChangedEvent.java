package dev.equipmentstructure.api.event;

import dev.equipmentstructure.api.EquipmentStructure;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

/** 结构成功改变后触发，用于刷新缓存、属性或客户端展示。 */
public final class EquipmentStructureChangedEvent extends Event {

    public enum ChangeType { INSTALLED, REMOVED, EXTENDED, MIGRATED, APPEARANCE_UPDATED, COMPONENT_DATA_UPDATED, GRID_UPDATED, REPLACED, SLOTS_RECONCILED }

    private final ItemStack stack;
    private final EquipmentStructure previous;
    private final EquipmentStructure current;
    private final ChangeType changeType;

    public EquipmentStructureChangedEvent(
            ItemStack stack,
            EquipmentStructure previous,
            EquipmentStructure current,
            ChangeType changeType
    ) {
        this.stack = stack;
        this.previous = previous;
        this.current = current;
        this.changeType = changeType;
    }

    public ItemStack stack() { return stack; }
    public EquipmentStructure previous() { return previous; }
    public EquipmentStructure current() { return current; }
    public ChangeType changeType() { return changeType; }
}
