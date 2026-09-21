package dev.equipmentstructure.api.client.appearance;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Client-side diagnostics for the optional visual half of a component. */
public final class AppearanceComponentAssetCheck {
    private AppearanceComponentAssetCheck() {}

    /** Returns whether the component logic and a prepared visual asset are available. */
    public static boolean isReady(ResourceLocation componentId, ResourceLocation assetId) {
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(assetId, "assetId");
        return dev.equipmentstructure.api.EquipmentComponentRegistry.isRegistered(componentId)
                && (AppearanceGuiAssetRegistry.get(assetId).isPresent()
                || AppearanceModelAssetRegistry.get(assetId).isPresent());
    }
}
