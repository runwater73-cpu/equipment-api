package dev.equipmentstructure.api.appearance;

import dev.equipmentstructure.api.client.appearance.AppearanceGuiAssetRegistry;
import dev.equipmentstructure.api.client.appearance.AppearanceRuntime;
import dev.equipmentstructure.api.EquipmentStructure;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Registry lifecycle is testable without constructing GuiGraphics or a GL context. */
class AppearanceGuiAssetRegistryContractTest {
    @AfterEach
    void cleanup() {
        AppearanceGuiAssetRegistry.clear();
    }

    @Test
    void unregisterInvalidatesTheAppearancePlanCache() {
        AppearanceRuntime.clearCache();
        var asset = ResourceLocation.fromNamespaceAndPath("gui_registry_test", "asset");
        var host = ResourceLocation.fromNamespaceAndPath("gui_registry_test", "host");
        AppearanceGuiAssetRegistry.register(asset, context -> fail("Lifecycle test must not draw"));
        var snapshot = AppearanceGuiAssetRegistry.support();
        AppearanceRuntime.resolve(new EquipmentStructure(host, host, List.of(), Map.of()), snapshot);
        assertEquals(1, AppearanceRuntime.cachedPlanCount());
        assertTrue(AppearanceGuiAssetRegistry.unregister(asset));
        assertEquals(0, AppearanceRuntime.cachedPlanCount());
        assertFalse(AppearanceGuiAssetRegistry.unregister(asset));
        assertFalse(AppearanceGuiAssetRegistry.support().preparedAssets().contains(asset));
        assertTrue(snapshot.preparedAssets().contains(asset), "Earlier support snapshots must remain immutable");
    }
}
