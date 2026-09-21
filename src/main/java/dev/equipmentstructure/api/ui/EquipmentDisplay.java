package dev.equipmentstructure.api.ui;

import java.util.List;

/** Controls only the left equipment information panel, not gameplay values. */
public record EquipmentDisplay(boolean replaceDefaults, List<EquipmentDisplayRow> rows) {
    public EquipmentDisplay { rows = List.copyOf(rows); }
    public static EquipmentDisplay append(EquipmentDisplayRow... rows) { return new EquipmentDisplay(false, List.of(rows)); }
    public static EquipmentDisplay replace(EquipmentDisplayRow... rows) { return new EquipmentDisplay(true, List.of(rows)); }
}
