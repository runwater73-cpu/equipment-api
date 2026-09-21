package dev.equipmentstructure.api.ui;

import net.minecraft.network.chat.Component;
import java.util.Objects;

/** Author-defined equipment information row; text remains presentation-only. */
public record EquipmentDisplayRow(Component label, Component value, int color) {
    public EquipmentDisplayRow {
        label = Objects.requireNonNull(label, "label").copy();
        value = Objects.requireNonNull(value, "value").copy();
    }
    public EquipmentDisplayRow(Component label, Component value) { this(label, value, 0xFFFFFFFF); }
    @Override public Component label() { return label.copy(); }
    @Override public Component value() { return value.copy(); }
}
