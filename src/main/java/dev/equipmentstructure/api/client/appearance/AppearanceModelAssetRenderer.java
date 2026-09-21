package dev.equipmentstructure.api.client.appearance;

/** Renderer supplied by an addon for one calibrated 3D model asset ID. */
@FunctionalInterface
public interface AppearanceModelAssetRenderer {
    void render(AppearanceModelAssetRenderContext context);
}
