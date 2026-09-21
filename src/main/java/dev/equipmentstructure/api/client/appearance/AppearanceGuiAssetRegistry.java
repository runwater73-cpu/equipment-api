package dev.equipmentstructure.api.client.appearance;

import dev.equipmentstructure.api.appearance.AppearancePlan;
import dev.equipmentstructure.api.appearance.AppearanceSupport;
import dev.equipmentstructure.api.internal.ExtensionGuard;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit GUI model-asset registry. It never converts an ItemStack into an appearance. */
@OnlyIn(Dist.CLIENT)
public final class AppearanceGuiAssetRegistry {
    private static final Map<ResourceLocation, AppearanceGuiAssetRenderer> RENDERERS =
            new ConcurrentHashMap<>();
    private static final ExtensionGuard<ResourceLocation> GUARD =
            new ExtensionGuard<>("GUI appearance asset");

    private AppearanceGuiAssetRegistry() {}

    public static void register(ResourceLocation asset, AppearanceGuiAssetRenderer renderer) {
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(renderer, "renderer");
        AppearanceGuiAssetRenderer previous = RENDERERS.putIfAbsent(asset, renderer);
        if (previous != null && previous != renderer) {
            throw new IllegalStateException("GUI appearance asset already registered: " + asset);
        }
    }

    public static Optional<AppearanceGuiAssetRenderer> get(ResourceLocation asset) {
        Objects.requireNonNull(asset, "asset");
        return Optional.ofNullable(RENDERERS.get(asset));
    }

    /** Returns a stable snapshot of registered GUI asset IDs for support builders. */
    public static Set<ResourceLocation> assets() {
        return Set.copyOf(RENDERERS.keySet());
    }

    /**
     * Removes one asset during a development reload or addon shutdown.
     * Removing an asset also invalidates resolved plans so a later frame can
     * fall back to the original equipment appearance safely.
     */
    public static boolean unregister(ResourceLocation asset) {
        Objects.requireNonNull(asset, "asset");
        AppearanceGuiAssetRenderer removed = RENDERERS.remove(asset);
        if (removed != null) {
            GUARD.clear();
            AppearanceRuntime.clearCache();
            return true;
        }
        return false;
    }

    public static AppearanceSupport support() {
        return new AppearanceSupport(AppearanceResourceReloadListener.generation(),
                Set.copyOf(RENDERERS.keySet()),
                Set.of(ResourceLocation.fromNamespaceAndPath("equipment_structure_api", "gui_model")),
                true);
    }

    public static int render(GuiGraphics graphics, Font font, AppearancePlan plan,
                             int anchorX, int anchorY) {
        Objects.requireNonNull(graphics, "graphics");
        Objects.requireNonNull(font, "font");
        if (plan == null) return 0;
        // GUI assets are prepared per resource generation just like 3D assets.
        // Never draw a plan captured before a reload, even if its asset ID is still registered.
        if (plan.generation() != AppearanceResourceReloadListener.generation()) return 0;
        int rendered = 0;
        for (AppearancePlan.Placement placement : plan.orderedPlacements()) {
            if (!placement.visible()) continue;
            // The shared GUI preview always draws the original ItemStack first;
            // a replacement request would otherwise double-draw the original part.
            if (!placement.replacedElements().isEmpty()) continue;
            AppearanceGuiAssetRenderer renderer = RENDERERS.get(placement.asset());
            if (renderer == null) continue;
            if (GUARD.call(placement.asset(), () -> {
                renderer.render(new AppearanceGuiAssetRenderContext(placement, graphics, font,
                        anchorX, anchorY));
                return true;
            }, false)) rendered++;
        }
        return rendered;
    }

    public static void clear() {
        RENDERERS.clear();
        GUARD.clear();
        AppearanceRuntime.clearCache();
    }
}
