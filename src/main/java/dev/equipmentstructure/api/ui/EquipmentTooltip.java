package dev.equipmentstructure.api.ui;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

/** Author-controlled content for the API-owned tooltip section only. */
public record EquipmentTooltip(boolean replaceDefaults, List<Component> lines) {
    public EquipmentTooltip {
        lines = Objects.requireNonNull(lines, "lines").stream()
                .map(line -> (Component) Objects.requireNonNull(line, "line").copy()).toList();
    }

    @Override
    public List<Component> lines() { return lines.stream().map(line -> (Component) line.copy()).toList(); }

    public static EquipmentTooltip append(Component... lines) { return new EquipmentTooltip(false, List.of(lines)); }
    public static EquipmentTooltip replace(Component... lines) { return new EquipmentTooltip(true, List.of(lines)); }
    public static EquipmentTooltip hidden() { return replace(); }
}
