package dev.equipmentstructure.api.ui;

import dev.equipmentstructure.api.EquipmentComponentInstance;

import java.util.Objects;
import java.util.Optional;

/** A loose component has no invented host or socket; equipment may have a saved structure. */
public record EquipmentTooltipContext(EquipmentItemView item,
                                      Optional<EquipmentComponentInstance> looseComponent,
                                      boolean expanded) {
    public EquipmentTooltipContext {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(looseComponent, "looseComponent");
    }
}
