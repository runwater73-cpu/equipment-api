package dev.equipmentstructure.api.attribute;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;

/**
 * Consistent multi-attribute view for one equipment slot.
 *
 * <p>GUI and tooltip code can request all rows in one pass, so every row is
 * based on the same structure and component state. The map keeps the caller's
 * order and is immutable after construction.</p>
 */
public record EquipmentAttributeSnapshot(
        Map<Holder<Attribute>, EquipmentAttributeValue> values
) {
    public EquipmentAttributeSnapshot {
        Objects.requireNonNull(values, "values");
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public EquipmentAttributeValue value(Holder<Attribute> attribute) {
        return values.get(Objects.requireNonNull(attribute, "attribute"));
    }
}
