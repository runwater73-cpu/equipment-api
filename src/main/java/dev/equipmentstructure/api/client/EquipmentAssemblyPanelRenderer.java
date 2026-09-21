package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Resizes the empty authored panel without stretching its corners or adding row rules. */
final class EquipmentAssemblyPanelRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            EquipmentStructureApiMod.MOD_ID, "textures/gui/monochrome.png");
    private static final int BORDER = 8;

    private EquipmentAssemblyPanelRenderer() {
    }

    static void render(GuiGraphics graphics, int left, int top, EquipmentAssemblyUiLayout.Panel panel) {
        int[] sourceWidths = {BORDER, 176 - 2 * BORDER, BORDER};
        int[] sourceHeights = {BORDER, 90 - 2 * BORDER, BORDER};
        int[] widths = {BORDER, panel.width() - 2 * BORDER, BORDER};
        int[] heights = {BORDER, panel.height() - 2 * BORDER, BORDER};
        int y = top + panel.y();
        int v = 148;
        for (int row = 0; row < 3; row++) {
            int x = left + panel.x();
            int u = 2;
            for (int column = 0; column < 3; column++) {
                graphics.blit(TEXTURE, x, y, widths[column], heights[row], u, v,
                        sourceWidths[column], sourceHeights[row], 512, 512);
                x += widths[column];
                u += sourceWidths[column];
            }
            y += heights[row];
            v += sourceHeights[row];
        }
    }
}
