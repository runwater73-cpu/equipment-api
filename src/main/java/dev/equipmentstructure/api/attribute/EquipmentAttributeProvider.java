package dev.equipmentstructure.api.attribute;

import java.util.Collection;

/**
 * Computes the current attribute contributions of one component instance.
 * Providers are queried every time Minecraft asks for item attributes, so
 * mutable component data and durability changes are reflected immediately.
 */
@FunctionalInterface
public interface EquipmentAttributeProvider {
    Collection<EquipmentAttributeContribution> contributions(EquipmentAttributeContext context);
}
