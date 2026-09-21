package dev.equipmentstructure.api.attribute;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * One active attribute contribution together with the component and concrete
 * installation point that produced it.
 *
 * <p>The context contains defensive snapshots, so this type is safe to pass
 * to tooltip, GUI and diagnostic integrations.</p>
 */
public record EquipmentAttributeDetail(
        EquipmentAttributeContext context,
        EquipmentAttributeContribution contribution
) {
    public EquipmentAttributeDetail {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(contribution, "contribution");
    }

    public EquipmentSlotDefinition slot() {
        return context.slot();
    }

    public ResourceLocation slotId() {
        return slot().id();
    }

    public ResourceLocation interfaceType() {
        return slot().interfaceType();
    }

    public EquipmentComponentInstance component() {
        return context.component();
    }

    public ResourceLocation componentId() {
        return component().id();
    }
}
