package dev.equipmentstructure.api.attribute;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.EquipmentStructureApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

/** Read-only context supplied to a dynamic component attribute provider. */
public record EquipmentAttributeContext(
        ItemStack equipment,
        EquipmentSlotDefinition slot,
        EquipmentComponentInstance component
) {
    public EquipmentAttributeContext {
        Objects.requireNonNull(equipment, "equipment");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(component, "component");
        // Attribute providers run from both client and server event paths. A
        // provider must never be able to mutate the live ItemStack while the
        // resolver is calculating modifiers.
        equipment = equipment.copy();
    }

    @Override
    public ItemStack equipment() {
        return equipment.copy();
    }

    /** Returns the complete current structure, when the host is still valid. */
    public Optional<EquipmentStructure> equipmentStructure() {
        return EquipmentStructureApi.structure(equipment);
    }

    /** Returns the host template ID without requiring callers to parse the stack. */
    public Optional<ResourceLocation> hostId() {
        return equipmentStructure().map(EquipmentStructure::hostId);
    }

    /** True when any interface currently contains a component with this ID. */
    public boolean hasComponent(ResourceLocation componentId) {
        Objects.requireNonNull(componentId, "componentId");
        return equipmentStructure().stream()
                .flatMap(structure -> structure.components().values().stream())
                .flatMap(java.util.Collection::stream)
                .anyMatch(component -> component.id().equals(componentId));
    }

    /** True when this equipment has the given concrete interface instance. */
    public boolean hasInterface(ResourceLocation slotId) {
        Objects.requireNonNull(slotId, "slotId");
        return equipmentStructure().flatMap(structure -> structure.slot(slotId)).isPresent();
    }
}
