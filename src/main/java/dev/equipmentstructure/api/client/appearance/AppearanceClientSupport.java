package dev.equipmentstructure.api.client.appearance;

import dev.equipmentstructure.api.appearance.AppearanceSupport;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Builds one immutable capability snapshot for GUI and world appearance adapters. */
@OnlyIn(Dist.CLIENT)
public final class AppearanceClientSupport {
    private AppearanceClientSupport() {}

    /** Combines every prepared GUI, sprite, and custom model asset into one support snapshot. */
    public static AppearanceSupport registered(Set<ResourceLocation> capabilities,
                                               boolean canReplaceElements) {
        Objects.requireNonNull(capabilities, "capabilities");
        Set<ResourceLocation> assets = new HashSet<>();
        assets.addAll(AppearanceGuiAssetRegistry.assets());
        assets.addAll(AppearanceModelAssetRegistry.assets());
        return new AppearanceSupport(AppearanceResourceReloadListener.generation(),
                assets, capabilities, canReplaceElements);
    }
}
