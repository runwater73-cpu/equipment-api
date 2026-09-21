package dev.equipmentstructure.api.attribute;

import java.util.List;

/** Immutable result of evaluating one attribute for one equipment view. */
public record EquipmentAttributeValue(
        double baseValue,
        double finalValue,
        List<EquipmentAttributeContribution> contributions
) {
    public EquipmentAttributeValue {
        contributions = List.copyOf(contributions);
    }

    public double delta() {
        return finalValue - baseValue;
    }
}
