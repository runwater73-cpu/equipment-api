package dev.equipmentstructure.api.client.appearance;

import net.minecraft.resources.ResourceLocation;

/** A small, data-driven 2D attachment sprite. Width and height are model units. */
public record SpriteModelDefinition(ResourceLocation asset, ResourceLocation texture,
                                    float width, float height, boolean extrude) {
    public SpriteModelDefinition(ResourceLocation asset, ResourceLocation texture, float width, float height) {
        this(asset, texture, width, height, true);
    }
    public SpriteModelDefinition {
        if (asset == null || texture == null) throw new NullPointerException();
        String path = texture.getPath();
        if (!path.startsWith("textures/")) path = "textures/" + path;
        if (!path.endsWith(".png")) path += ".png";
        texture = ResourceLocation.fromNamespaceAndPath(texture.getNamespace(), path);
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0
                || width > 64 || height > 64) {
            throw new IllegalArgumentException("Sprite dimensions must be finite and in (0,64]");
        }
    }
}
