package dev.equipmentstructure.api.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/** Small eye toggle with the same nine-slice GUI skin as the placement commands. */
final class EquipmentVisibilityButton extends Button {
    private final String target;
    private final BooleanSupplier shown;
    private boolean previousShown;
    private boolean initialized;

    EquipmentVisibilityButton(int x, int y, String target, BooleanSupplier shown, OnPress onPress) {
        super(Button.builder(Component.empty(), onPress).bounds(x, y, 49, 20));
        this.target = target;
        this.shown = shown;
        refresh();
    }

    void refresh() {
        if (initialized && previousShown == shown.getAsBoolean()) return;
        initialized = true;
        previousShown = shown.getAsBoolean();
        var message = Component.translatable("gui.equipment_structure_api.placement."
                + (previousShown ? "hide_" : "show_") + target);
        setMessage(message);
        setTooltip(Tooltip.create(message));
    }

    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float tick) {
        if (previousShown != shown.getAsBoolean()) refresh();
        EquipmentStructureButton.blitNineSlice(g, getX(), getY(), width, height,
                !active ? 80 : isHoveredOrFocused() ? 28 : 2);
        int color = !active ? 0xFF737B7A : previousShown ? 0xFFE7EFED : 0xFFE9BA70;
        int x = getX() + 5, y = getY() + 6;
        // Pixel eye keeps the monochrome texture's sharp edges at every GUI scale.
        g.fill(x + 3, y, x + 8, y + 1, color);
        g.fill(x + 3, y + 7, x + 8, y + 8, color);
        for (int i = 0; i < 3; i++) {
            g.fill(x + 2 - i, y + 1 + i, x + 3 - i, y + 2 + i, color);
            g.fill(x + 8 + i, y + 1 + i, x + 9 + i, y + 2 + i, color);
            g.fill(x + i, y + 4 + i, x + i + 1, y + 5 + i, color);
            g.fill(x + 10 - i, y + 4 + i, x + 11 - i, y + 5 + i, color);
        }
        if (previousShown) g.fill(x + 4, y + 2, x + 7, y + 6, color);
        else for (int i = 0; i < 10; i++) g.fill(x + i, y + 8 - i, x + i + 1, y + 10 - i, color);
        var font = Minecraft.getInstance().font;
        var label = Component.translatable("gui.equipment_structure_api.placement.visibility_" + target);
        g.drawString(font, font.plainSubstrByWidth(label.getString(), 27), getX() + 19,
                getY() + (height - font.lineHeight) / 2, color, false);
    }
}
