package dev.equipmentstructure.api.attribute;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EquipmentAttributeRegistryTest {
    private static final ResourceLocation COMPONENT = ResourceLocation.fromNamespaceAndPath(
            "equipment_attribute_test", "blade");

    @AfterEach
    void clear() {
        EquipmentAttributeRegistry.clear();
    }

    @Test
    void providerRegistrationIsAvailable() {
        EquipmentAttributeRegistry.register(COMPONENT, context -> List.of());
        var provider = EquipmentAttributeRegistry.get(COMPONENT).orElseThrow();
        assertEquals(List.of(), provider.contributions(null));
    }

    @Test
    void conflictingRegistrationIsRejected() {
        EquipmentAttributeRegistry.register(COMPONENT, context -> List.of());
        assertThrows(IllegalStateException.class,
                () -> EquipmentAttributeRegistry.register(COMPONENT, context -> List.of()));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("equipment_attribute_test", path);
    }
}
