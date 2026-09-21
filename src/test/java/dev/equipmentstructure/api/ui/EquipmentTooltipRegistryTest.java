package dev.equipmentstructure.api.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentTooltipRegistryTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("tooltip_test", "shared");

    @AfterEach
    void clear() { EquipmentTooltipRegistry.clear(); }

    @Test
    void hostAndComponentIdsDoNotConflict() {
        EquipmentTooltipRegistry.registerHost(ID, context -> EquipmentTooltip.hidden());
        EquipmentTooltipRegistry.registerComponent(ID, context -> EquipmentTooltip.hidden());
        assertTrue(EquipmentTooltipRegistry.unregisterHost(ID));
        assertFalse(EquipmentTooltipRegistry.unregisterHost(ID));
        assertTrue(EquipmentTooltipRegistry.unregisterComponent(ID));
    }

    @Test
    void duplicateProviderIsExplicitlyRejected() {
        EquipmentTooltipProvider provider = context -> EquipmentTooltip.hidden();
        EquipmentTooltipRegistry.registerHost(ID, provider);
        assertDoesNotThrow(() -> EquipmentTooltipRegistry.registerHost(ID, provider));
        assertThrows(IllegalStateException.class, () -> EquipmentTooltipRegistry.registerHost(ID,
                context -> EquipmentTooltip.append()));
    }

    @Test
    void returnedTextCannotMutateAuthoredContent() {
        var text = Component.literal("original");
        var tooltip = EquipmentTooltip.append(text);
        text.append("changed");
        tooltip.lines().getFirst().getSiblings().add(Component.literal("changed again"));
        assertEquals("original", tooltip.lines().getFirst().getString());
        assertThrows(UnsupportedOperationException.class, () -> tooltip.lines().clear());
    }

    @Test
    void hiddenSectionReplacesOnlyApiDefaultsWithNoLines() {
        assertTrue(EquipmentTooltip.hidden().replaceDefaults());
        assertTrue(EquipmentTooltip.hidden().lines().isEmpty());
        assertFalse(EquipmentTooltip.append().replaceDefaults());
    }
}
