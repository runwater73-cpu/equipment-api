package dev.equipmentstructure.api.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Compact button skin used by every Equipment Structure API screen. */
public final class EquipmentStructureButton extends Button {
    private static final ResourceLocation GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "equipment_structure_api", "textures/gui/monochrome.png");
    private static final int TILE_SIZE = 24;
    private static final int BORDER = 4;
    public EquipmentStructureButton(Button.Builder builder) {
        super(builder);
    }

    public static Button.Builder builder(Component message, Button.OnPress onPress) {
        return Button.builder(message, onPress);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int textureU = !active ? 80 : isHoveredOrFocused() ? 28 : 2;
        blitNineSlice(graphics, getX(), getY(), getWidth(), getHeight(), textureU);
        int textColor = active ? 0xFFE7EFED : 0xFF737B7A;
        renderScrollingString(graphics, Minecraft.getInstance().font, BORDER, textColor);
    }

    static void blitNineSlice(GuiGraphics graphics, int x, int y, int width, int height, int textureU) {
        int[] source = {BORDER, TILE_SIZE - 2 * BORDER, BORDER};
        int borderX = Math.min(BORDER, width / 2);
        int borderY = Math.min(BORDER, height / 2);
        int[] targetX = {borderX, width - 2 * borderX, borderX};
        int[] targetY = {borderY, height - 2 * borderY, borderY};
        int py = y;
        int v = 296;
        for (int row = 0; row < 3; row++) {
            int px = x;
            int u = textureU;
            for (int column = 0; column < 3; column++) {
                if (targetX[column] > 0 && targetY[row] > 0) {
                    graphics.blit(GUI_TEXTURE, px, py,
                            targetX[column], targetY[row], u, v, source[column], source[row], 512, 512);
                }
                px += targetX[column];
                u += source[column];
            }
            py += targetY[row];
            v += source[row];
        }
    }
}
