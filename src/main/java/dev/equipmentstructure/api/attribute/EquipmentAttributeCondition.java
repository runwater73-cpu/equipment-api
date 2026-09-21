package dev.equipmentstructure.api.attribute;

import java.util.Objects;

/** A dynamic predicate evaluated whenever an equipment attribute is queried. */
@FunctionalInterface
public interface EquipmentAttributeCondition {
    boolean test(EquipmentAttributeContext context);

    EquipmentAttributeCondition ALWAYS = context -> true;

    /** Combines two conditions without losing an existing provider condition. */
    default EquipmentAttributeCondition and(EquipmentAttributeCondition other) {
        Objects.requireNonNull(other, "other");
        return context -> test(context) && other.test(context);
    }

    /** Combines two conditions where either predicate may enable the contribution. */
    default EquipmentAttributeCondition or(EquipmentAttributeCondition other) {
        Objects.requireNonNull(other, "other");
        return context -> test(context) || other.test(context);
    }

    /** Negates this condition. */
    default EquipmentAttributeCondition negate() {
        return context -> !test(context);
    }
}
