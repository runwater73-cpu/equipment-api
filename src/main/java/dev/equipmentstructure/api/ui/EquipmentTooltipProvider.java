package dev.equipmentstructure.api.ui;

/** Optional client presentation only; must not perform I/O, mutation or recursive tooltip queries. */
@FunctionalInterface
public interface EquipmentTooltipProvider {
    EquipmentTooltip describe(EquipmentTooltipContext context);
}
