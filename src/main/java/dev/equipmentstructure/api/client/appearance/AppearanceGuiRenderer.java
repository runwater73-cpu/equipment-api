package dev.equipmentstructure.api.client.appearance;

import dev.equipmentstructure.api.appearance.AppearancePlan;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Draws explicitly registered custom model assets in a GUI preview. */
@OnlyIn(Dist.CLIENT)
public final class AppearanceGuiRenderer {
    private AppearanceGuiRenderer() {}

    /**
     * Draws ready placements around the supplied anchor. Returns the number
     * of assets drawn; zero means the caller should draw the original item.
     */
    public static int render(GuiGraphics graphics, Font font, AppearancePlan plan,
                             int anchorX, int anchorY) {
        if (graphics == null || font == null) throw new NullPointerException("graphics/font");
        return AppearanceGuiAssetRegistry.render(graphics, font, plan, anchorX, anchorY);
    }
}
