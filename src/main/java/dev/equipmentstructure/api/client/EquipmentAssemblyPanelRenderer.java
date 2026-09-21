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
        int y = top + panel.y();
        int v = 148;
        for (int row = 0; row < 3; row++) {
            int height = row == 1 ? panel.height() - 2 * BORDER : BORDER;
            int sourceHeight = row == 1 ? 90 - 2 * BORDER : BORDER;
            int x = left + panel.x();
            int u = 2;
            for (int column = 0; column < 3; column++) {
                int width = column == 1 ? panel.width() - 2 * BORDER : BORDER;
                int sourceWidth = column == 1 ? 176 - 2 * BORDER : BORDER;
                graphics.blit(TEXTURE, x, y, width, height, u, v, sourceWidth, sourceHeight, 512, 512);
                x += width;
                u += sourceWidth;
            }
            y += height;
            v += sourceHeight;
        }
    }
}
