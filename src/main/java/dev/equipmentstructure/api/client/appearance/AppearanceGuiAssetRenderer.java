package dev.equipmentstructure.api.client.appearance;

/** Renderer supplied by an addon for one Blockbench/model asset ID in GUI. */
@FunctionalInterface
public interface AppearanceGuiAssetRenderer {
    void render(AppearanceGuiAssetRenderContext context);
}
