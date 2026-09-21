package dev.equipmentstructure.api;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

/**
 * Runtime context for a component behavior.
 *
 * <p>The host is a defensive snapshot, including in pre-checks. Changing it
 * cannot consume an input or modify the live equipment. The optional wearer
 * is a live entity supplied during server activation/deactivation/tick; do not retain
 * it. Assembly checks have no wearer and imply no logical side.</p>
 */
public record EquipmentComponentContext(
        ItemStack equipment,
        EquipmentStructure structure,
        EquipmentSlotDefinition slot,
        EquipmentComponentInstance component,
        Optional<LivingEntity> wearer,
        Optional<EquipmentSlot> equipmentSlot
) {
    public EquipmentComponentContext {
        Objects.requireNonNull(equipment, "equipment");
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(wearer, "wearer");
        Objects.requireNonNull(equipmentSlot, "equipmentSlot");
        equipment = equipment.copy();
        if (wearer.isPresent() != equipmentSlot.isPresent()) {
            throw new IllegalArgumentException("Wearer and equipment slot must be supplied together");
        }
    }

    @Override
    public ItemStack equipment() {
        return equipment.copy();
    }

    /** Context for an item being assembled or removed outside a living entity. */
    public EquipmentComponentContext(ItemStack equipment,
                                     EquipmentStructure structure,
                                     EquipmentSlotDefinition slot,
                                     EquipmentComponentInstance component) {
        this(equipment, structure, slot, component, Optional.empty(), Optional.empty());
    }

    public boolean hasWearer() {
        return wearer.isPresent();
    }
    public dev.equipmentstructure.api.grid.synergy.GridRuleResult gridRule(net.minecraft.resources.ResourceLocation id) {
        return dev.equipmentstructure.api.grid.synergy.GridRuleRegistry.result(structure, id, slot.id());
    }

}
