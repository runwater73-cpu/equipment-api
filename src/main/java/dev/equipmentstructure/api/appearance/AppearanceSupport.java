package dev.equipmentstructure.api.appearance;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Capabilities and fully prepared assets supplied by the owning render adapter for one generation.
 * A custom geometry asset existing on disk is NOT enough to put its asset into
 * preparedAssets. The adapter must validate its geometry/materials, calibration
 * and resource lifetime first. Data-driven sprite attachments follow the same
 * rule after their PNG has been validated. No callbacks run here.
 */
public record AppearanceSupport(long generation, Set<ResourceLocation> preparedAssets,
                                Set<ResourceLocation> capabilities, boolean canReplaceElements) {
    public AppearanceSupport {
        if (generation < 0) throw new IllegalArgumentException("Negative resource generation");
        preparedAssets = Set.copyOf(preparedAssets);
        capabilities = Set.copyOf(capabilities);
    }
}
