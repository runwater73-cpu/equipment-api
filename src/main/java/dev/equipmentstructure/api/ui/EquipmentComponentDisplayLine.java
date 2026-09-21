package dev.equipmentstructure.api.ui;

import net.minecraft.network.chat.Component;

import java.util.Objects;

/** One author-provided line in a component information panel. */
public record EquipmentComponentDisplayLine(Component text, int color) {
    public EquipmentComponentDisplayLine {
        Objects.requireNonNull(text, "text");
    }

    public EquipmentComponentDisplayLine(Component text) {
        this(text, 0xFFAAAAAA);
    }
}
