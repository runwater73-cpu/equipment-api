package dev.equipmentstructure.api.event;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentStructure;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** 在部件移除前触发；取消事件即可保留部件。 */
public final class EquipmentStructureRemoveEvent extends Event implements ICancellableEvent {

    private final ItemStack stack;
    private final EquipmentStructure structure;
    private final ResourceLocation slotId;
    private final EquipmentComponentInstance component;

    public EquipmentStructureRemoveEvent(
            ItemStack stack,
            EquipmentStructure structure,
            ResourceLocation slotId,
            EquipmentComponentInstance component
    ) {
        this.stack = stack;
        this.structure = structure;
        this.slotId = slotId;
        this.component = component;
    }

    public ItemStack stack() { return stack; }
    public EquipmentStructure structure() { return structure; }
    public ResourceLocation slotId() { return slotId; }
    public EquipmentComponentInstance component() { return component; }
}
