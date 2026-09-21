package dev.equipmentstructure.api.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EquipmentComponentDisplayRegistryTest {
    private static final ResourceLocation COMPONENT = id("tempered_blade");
    @AfterEach
    void clear() {
        EquipmentComponentDisplayRegistry.clear();
    }

    @Test
    void providerRegistrationIsAvailableAndDisplayLinesRemainAuthorControlled() {
        EquipmentComponentDisplayProvider provider = context -> EquipmentComponentDisplay.append(
                new EquipmentComponentDisplayLine(net.minecraft.network.chat.Component.literal("quality: 4")));
        EquipmentComponentDisplayRegistry.register(COMPONENT, provider);

        var display = EquipmentComponentDisplay.append(
                new EquipmentComponentDisplayLine(net.minecraft.network.chat.Component.literal("quality: 4")));

        assertTrue(!display.replaceDefaults());
        assertEquals("quality: 4", display.lines().get(0).text().getString());
        assertSame(provider, EquipmentComponentDisplayRegistry.get(COMPONENT).orElseThrow());
    }

    @Test
    void conflictingProviderRegistrationIsRejected() {
        EquipmentComponentDisplayProvider first = context -> EquipmentComponentDisplay.append();
        EquipmentComponentDisplayRegistry.register(COMPONENT, first);

        assertThrows(IllegalStateException.class, () ->
                EquipmentComponentDisplayRegistry.register(COMPONENT,
                        context -> EquipmentComponentDisplay.replace()));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("equipment_display_test", path);
    }
}
