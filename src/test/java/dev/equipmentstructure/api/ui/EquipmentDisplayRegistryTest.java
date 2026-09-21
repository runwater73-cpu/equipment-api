package dev.equipmentstructure.api.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentDisplayRegistryTest {
    @AfterEach void clear() { EquipmentDisplayRegistry.clear(); }

    @Test void conflictingRegistrationRequiresExplicitUnregister() {
        var id = ResourceLocation.fromNamespaceAndPath("test", "host");
        EquipmentDisplayRegistry.register(id, view -> EquipmentDisplay.append());
        assertThrows(IllegalStateException.class, () -> EquipmentDisplayRegistry.register(id, view -> EquipmentDisplay.append()));
        assertTrue(EquipmentDisplayRegistry.unregister(id));
        assertFalse(EquipmentDisplayRegistry.unregister(id));
    }

    @Test void textAndRowListsAreIsolated() {
        var label = Component.literal("Charge");
        var row = new EquipmentDisplayRow(label, Component.literal("42"));
        label.append("changed");
        assertEquals("Charge", row.label().getString());
        var display = EquipmentDisplay.append(row);
        assertThrows(UnsupportedOperationException.class, () -> display.rows().clear());
        assertTrue(EquipmentDisplay.replace().replaceDefaults());
    }
}
