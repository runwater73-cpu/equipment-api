package dev.equipmentstructure.api;

import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

/** Compatibility protocol shared by concrete installation points. */
public record EquipmentInterfaceDefinition(ResourceLocation id, ResourceLocation defaultComponentType) {
    public EquipmentInterfaceDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(defaultComponentType, "defaultComponentType");
    }
}
