package dev.equipmentstructure.api.event;

import dev.equipmentstructure.api.EquipmentStructure;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** 追加空接口写入前触发；取消事件即可拒绝新增。原有接口、部件和模式版本保持不变。 */
public final class EquipmentStructureExtensionEvent extends Event implements ICancellableEvent {

    private final ItemStack stack;
    private final EquipmentStructure previous;
    private final EquipmentStructure proposed;

    public EquipmentStructureExtensionEvent(
            ItemStack stack,
            EquipmentStructure previous,
            EquipmentStructure proposed
    ) {
        this.stack = stack;
        this.previous = previous;
        this.proposed = proposed;
    }

    public ItemStack stack() { return stack; }
    public EquipmentStructure previous() { return previous; }
    public EquipmentStructure proposed() { return proposed; }
}
