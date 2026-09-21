package dev.equipmentstructure.api.ui;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Read-only context passed to a component information provider. */
public record EquipmentComponentDisplayContext(
        ItemStack equipment,
        EquipmentSlotDefinition slot,
        EquipmentComponentInstance component
) {
    public EquipmentComponentDisplayContext {
        Objects.requireNonNull(equipment, "equipment");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(component, "component");
        equipment = equipment.copy();
    }

    @Override
    public ItemStack equipment() {
        return equipment.copy();
    }
}
