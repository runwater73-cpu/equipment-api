package dev.equipmentstructure.api.ui;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Result returned by a component information provider.
 *
 * <p>Providers normally append their lines to the API's standard type,
 * interface, attribute and lifecycle lines. A provider can explicitly replace
 * those standard lines when it owns the complete presentation.</p>
 */
public record EquipmentComponentDisplay(
        boolean replaceDefaults,
        List<EquipmentComponentDisplayLine> lines
) {
    public EquipmentComponentDisplay {
        Objects.requireNonNull(lines, "lines");
        lines = lines.stream().map(Objects::requireNonNull).toList();
    }

    public static EquipmentComponentDisplay append(EquipmentComponentDisplayLine... lines) {
        Objects.requireNonNull(lines, "lines");
        return new EquipmentComponentDisplay(false, Arrays.asList(lines.clone()));
    }

    public static EquipmentComponentDisplay replace(EquipmentComponentDisplayLine... lines) {
        Objects.requireNonNull(lines, "lines");
        return new EquipmentComponentDisplay(true, Arrays.asList(lines.clone()));
    }
}
