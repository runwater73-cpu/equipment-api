package dev.equipmentstructure.api.appearance;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Non-mutating validation between optional client appearance definitions and
 * the authoritative component registry. A missing visual must never reject a
 * component or change its installed data.
 */
public final class AppearanceCatalogValidation {
    private AppearanceCatalogValidation() {}

    /** Returns sorted appearance IDs that do not map to an existing component definition. */
    public static List<ResourceLocation> unknownComponentIds(
            AppearanceCatalog catalog, Predicate<ResourceLocation> registeredComponent) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(registeredComponent, "registeredComponent");
        return catalog.components().keySet().stream()
                .filter(id -> !registeredComponent.test(id))
                .sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                .toList();
    }
}
