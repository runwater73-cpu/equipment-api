package dev.equipmentstructure.api.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentAssemblyNumericLayoutTest {
    @Test
    void durabilityFitsWithoutBreakingItsDigits() {
        float scale = EquipmentAssemblyInfoLayout.statValueScale("250/250", 42, 40);
        assertTrue(scale < 1.0F);
        assertTrue(42 * scale <= 40.001F);
    }

    @Test
    void shortNumbersAndProseKeepNormalSize() {
        assertEquals(1.0F, EquipmentAssemblyInfoLayout.statValueScale("1.6", 18, 40));
        assertEquals(1.0F, EquipmentAssemblyInfoLayout.statValueScale("long authored description", 150, 40));
    }

    @Test
    void extremeNumbersKeepMinimumReadableScaleAndCanWrap() {
        assertEquals(0.75F, EquipmentAssemblyInfoLayout.statValueScale("123456789/123456789", 110, 40));
    }
}
