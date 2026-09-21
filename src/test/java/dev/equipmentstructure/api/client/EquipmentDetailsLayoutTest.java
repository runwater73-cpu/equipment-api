package dev.equipmentstructure.api.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.equipmentstructure.api.client.EquipmentDetailsLayout.*;

class EquipmentDetailsLayoutTest {
    @Test void detailRectanglesFitExistingScreenAndHaveExactHitBounds() {
        for (var rect : new Rect[]{HOME, ATTRIBUTES, COMPONENTS, ATTRIBUTE_TEXT, COMPONENT_LIST, COMPONENT_TEXT}) {
            assertTrue(PANEL.contains(rect.x(), rect.y()));
            assertTrue(PANEL.contains(rect.right() - 1, rect.bottom() - 1));
            assertFalse(rect.contains(rect.right(), rect.y()));
            assertFalse(rect.contains(rect.x(), rect.bottom()));
        }
        assertTrue(HOME.right() < ATTRIBUTES.x());
        assertTrue(ATTRIBUTES.right() < COMPONENTS.x());
        assertTrue(COMPONENT_LIST.right() < COMPONENT_TEXT.x());
        assertTrue(ATTRIBUTES.bottom() < ATTRIBUTE_TEXT.y());
    }

    @Test void scrollClampsAtBothEndsAndAfterDataShrinks() {
        var scroll = new EquipmentDetailsScroll();
        scroll.measure(600, 146);
        scroll.move(1000);
        assertEquals(454, scroll.offset());
        scroll.move(-1000);
        assertEquals(0, scroll.offset());
        scroll.end();
        scroll.measure(50, 146);
        assertEquals(0, scroll.maximum());
        assertEquals(0, scroll.offset());
        scroll.move(Double.NaN);
        scroll.move(Double.POSITIVE_INFINITY);
        assertEquals(0, scroll.offset());
    }

    @Test void invalidSizesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Rect(0, 0, 0, 8));
        var scroll = new EquipmentDetailsScroll();
        assertThrows(IllegalArgumentException.class, () -> scroll.measure(-1, 20));
        assertThrows(IllegalArgumentException.class, () -> scroll.measure(20, 0));
    }
}
