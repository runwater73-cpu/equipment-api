package dev.equipmentstructure.api.ui;

/** Supplies dynamic, author-controlled component information lines. */
@FunctionalInterface
public interface EquipmentComponentDisplayProvider {
    EquipmentComponentDisplay describe(EquipmentComponentDisplayContext context);
}
