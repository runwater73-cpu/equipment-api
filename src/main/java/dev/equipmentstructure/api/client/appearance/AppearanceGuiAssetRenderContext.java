package dev.equipmentstructure.api.client.appearance;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.appearance.AppearancePlan;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

/** Input for a custom GUI asset renderer; no ItemStack icon is inferred. */
public record AppearanceGuiAssetRenderContext(
        AppearancePlan.Placement placement,
        GuiGraphics graphics,
        Font font,
        int anchorX,
        int anchorY
) {
    public AppearanceGuiAssetRenderContext {
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(graphics, "graphics");
        Objects.requireNonNull(font, "font");
    }

    public PoseStack poseStack() {
        return graphics.pose();
    }
}
