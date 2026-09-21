package dev.equipmentstructure.api.client.appearance;

import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.appearance.AppearanceCatalog;
import dev.equipmentstructure.api.appearance.AppearancePlan;
import dev.equipmentstructure.api.appearance.AppearanceResolver;
import dev.equipmentstructure.api.appearance.AppearanceSupport;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Objects;

/**
 * Shared client entry point for GUI previews and world/item render adapters.
 * The caller owns asset preparation and supplies the capabilities available to
 * the current render adapter; this class only resolves immutable placement data.
 */
@OnlyIn(Dist.CLIENT)
public final class AppearanceRuntime {
    private static final AppearancePlanCache CACHE = new AppearancePlanCache();

    private AppearanceRuntime() {}

    public static AppearanceCatalog catalog() {
        return AppearanceResourceReloadListener.catalog();
    }

    public static AppearancePlan resolve(EquipmentStructure structure, AppearanceSupport support) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(support, "support");
        AppearanceCatalog catalog = catalog();
        return CACHE.getOrCompute(structure, support,
                () -> AppearanceResolver.resolve(structure, catalog, support));
    }

    /** Clears all plans after a renderer/adapter drops prepared GPU assets. */
    public static void clearCache() {
        CACHE.clear();
    }

    /** Keeps only plans belonging to the active resource generation. */
    public static void retainGeneration(long generation) {
        CACHE.invalidateGeneration(generation);
    }

    public static int cachedPlanCount() {
        return CACHE.size();
    }
}
