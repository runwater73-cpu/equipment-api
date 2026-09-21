package dev.equipmentstructure.api.ui;

/** Reads a host snapshot to supply custom equipment information; no I/O or mutation. */
@FunctionalInterface
public interface EquipmentDisplayProvider {
    EquipmentDisplay describe(EquipmentItemView item);
}
