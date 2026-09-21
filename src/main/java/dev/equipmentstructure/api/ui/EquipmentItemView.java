package dev.equipmentstructure.api.ui;

import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.EquipmentStructureApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Menu-independent, read-only item snapshot shared by GUI and tooltip presenters. */
public record EquipmentItemView(ItemStack stack, Optional<EquipmentStructure> structure) {
    public EquipmentItemView {
        stack = Objects.requireNonNull(stack, "stack").copy();
        Objects.requireNonNull(structure, "structure");
    }

    public static EquipmentItemView capture(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return new EquipmentItemView(stack, EquipmentStructureApi.structure(stack));
    }

    @Override
    public ItemStack stack() { return stack.copy(); }

    public List<EquipmentSlotDefinition> slots() {
        return structure.map(EquipmentStructure::slots).orElse(List.of());
    }

    public Optional<ResourceLocation> hostId() {
        return structure.map(EquipmentStructure::hostId);
    }

    public int installedCount() {
        return structure.map(value -> value.components().size()).orElse(0);
    }
}
