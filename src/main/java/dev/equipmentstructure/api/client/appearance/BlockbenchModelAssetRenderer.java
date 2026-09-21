package dev.equipmentstructure.api.client.appearance;

/** Draws one prepared Blockbench model asset for an appearance placement. */
@FunctionalInterface
public interface BlockbenchModelAssetRenderer {
    void render(AppearanceModelAssetRenderContext context, BlockbenchModelDefinition model);
}
