package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.menu.EquipmentAssemblyLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EquipmentAssemblyUiLayoutTest {
    @Test
    void standardPanelsAreSeparated() {
        EquipmentAssemblyUiLayout layout = EquipmentAssemblyUiLayout.standard();

        assertTrue(layout.preview().right() <= layout.workspace().x());
        assertTrue(layout.stats().right() <= layout.workspace().x());
        assertTrue(layout.workspace().right() <= layout.details().x());
        assertTrue(layout.workspace().bottom() <= layout.inventory().y());
    }

    @Test
    void interactiveSlotsStayInsideTheirVisualPanels() {
        EquipmentAssemblyUiLayout layout = EquipmentAssemblyUiLayout.standard();
        int equipmentFrameX = EquipmentAssemblyLayout.EQUIPMENT_SLOT_X
                - EquipmentAssemblyLayout.EQUIPMENT_FRAME_INSET;
        int equipmentFrameY = EquipmentAssemblyLayout.EQUIPMENT_SLOT_Y
                - EquipmentAssemblyLayout.EQUIPMENT_FRAME_INSET;

        assertTrue(equipmentFrameX >= layout.preview().x());
        assertTrue(equipmentFrameY >= layout.preview().y());
        assertTrue(equipmentFrameX + 28 <= layout.preview().right());
        assertTrue(equipmentFrameY + 28 <= layout.preview().bottom());
        // Logical staging has no fixed on-screen location, especially not in the preview.
        assertTrue(EquipmentAssemblyLayout.COMPONENT_SLOT_X < 0);
        assertTrue(EquipmentAssemblyLayout.COMPONENT_SLOT_Y < 0);
    }

    @Test
    void equipmentSlotIsCenteredInPreview() {
        EquipmentAssemblyUiLayout layout = EquipmentAssemblyUiLayout.standard();
        assertEquals(layout.preview().x() + (layout.preview().width() - 16) / 2,
                layout.equipmentSlotX());
        assertEquals(layout.preview().y() + (layout.preview().height() - 16) / 2,
                layout.equipmentSlotY());
    }

    @Test
    void threeColumnsAlignAndRightPreviewMatchesLeft() {
        var layout = EquipmentAssemblyUiLayout.standard();
        assertEquals(layout.preview().y(), layout.workspace().y());
        assertEquals(layout.preview().y(), layout.componentPreview().y());
        assertEquals(layout.preview().width(), layout.componentPreview().width());
        assertEquals(72, layout.componentPreview().height());
        assertEquals(76, layout.componentInfo().y());
        assertEquals(58, layout.componentInfo().height());
        assertEquals(76, layout.details().y());
        assertEquals(58, layout.details().height());
        assertEquals(layout.stats().bottom(), layout.workspace().bottom());
        assertTrue(layout.componentPreview().bottom() < layout.details().y());
        assertEquals(layout.componentInfo(), layout.details());
        assertEquals(176, layout.inventory().width());
        assertEquals(90, layout.inventory().height());
    }

    @Test
    void all36ItemRectanglesMatchTheActualAtlasPixels() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/assets/equipment_structure_api/textures/gui/monochrome.png")) {
            var atlas = javax.imageio.ImageIO.read(java.util.Objects.requireNonNull(stream));
            for (int row = 0; row < 4; row++) {
                for (int column = 0; column < 9; column++) {
                    int x = 180 + EquipmentAssemblyLayout.playerSlotX(column) - EquipmentAssemblyLayout.INVENTORY_X;
                    int y = 148 + EquipmentAssemblyLayout.playerSlotY(row) - EquipmentAssemblyLayout.INVENTORY_Y;
                    // Verify each physical edge, not a second copy of the coordinate formula.
                    assertEquals(0xFF606060, atlas.getRGB(x + 8, y - 1), "top edge row=" + row);
                    assertEquals(0xFF606060, atlas.getRGB(x - 1, y + 8), "left edge col=" + column);
                    assertEquals(0xFF404040, atlas.getRGB(x + 8, y + 16), "bottom edge row=" + row);
                    assertEquals(0xFF404040, atlas.getRGB(x + 16, y + 8), "right edge col=" + column);
                    for (int py = 0; py < 16; py++) {
                        for (int px = 0; px < 16; px++) {
                            assertEquals(0xFF101010, atlas.getRGB(x + px, y + py));
                        }
                    }
                }
            }
        }
        assertEquals(22, EquipmentAssemblyLayout.playerSlotY(3) - EquipmentAssemblyLayout.playerSlotY(2));
    }

    @Test
    void sideInformationGrowsWithoutMovingInventory() {
        assertEquals(64, EquipmentAssemblyInfoLayout.heightForLines(4));
        assertEquals(78, EquipmentAssemblyInfoLayout.heightForLines(5));
        assertEquals(288, EquipmentAssemblyInfoLayout.heightForLines(20));
        assertEquals(64, EquipmentAssemblyInfoLayout.heightForStatLines(5));
        assertEquals(287, EquipmentAssemblyInfoLayout.heightForStatLines(30, 160));
        assertEquals(72, EquipmentAssemblyInfoLayout.heightForComponentInfo(5, 160));
        assertEquals(297, EquipmentAssemblyInfoLayout.heightForComponentInfo(30, 160));
        assertEquals(72, EquipmentAssemblyInfoLayout.heightForStatRows(
                java.util.List.of(1, 1, 1, 1, 1), 156));
        assertEquals(567, EquipmentAssemblyInfoLayout.heightForStatRows(
                java.util.List.of(20, 20, 20), 156));
        assertEquals(166, EquipmentAssemblyLayout.INVENTORY_Y);
    }

    @Test
    void longSidePanelsExtendDownwardWithoutArtificialBoundary() {
        var layout = EquipmentAssemblyUiLayout.standard();
        var longComponent = layout.componentInfo(200);

        assertEquals(200, longComponent.height());
        assertTrue(longComponent.bottom() > EquipmentAssemblyLayout.GUI_HEIGHT);
        assertTrue(longComponent.x() >= layout.inventory().right());
        assertTrue(layout.stats(157).bottom() > EquipmentAssemblyLayout.GUI_HEIGHT);
    }

    @Test
    void defaultEquipmentStatLabelsExposeCombatValues() {
        var texts = dev.equipmentstructure.api.ui.EquipmentStructureUiDefinition.Texts.defaults();
        assertEquals("gui.equipment_structure_api.assembly.attack_damage", texts.attackDamage());
        assertEquals("gui.equipment_structure_api.assembly.attack_speed", texts.attackSpeed());
    }
}
