package dev.equipmentstructure.api.appearance;

import dev.equipmentstructure.api.client.appearance.AppearanceModelAssetRegistry;
import dev.equipmentstructure.api.client.appearance.BlockbenchModelAssetRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockbenchModelAssetRegistryTest {
    private static final net.minecraft.resources.ResourceLocation ASSET = id("blade");
    private static final net.minecraft.resources.ResourceLocation MODEL = id("models/blade.bbmodel");

    @AfterEach
    void cleanup() {
        BlockbenchModelAssetRegistry.clear();
        AppearanceModelAssetRegistry.clear();
    }

    @Test
    void registrationIsStableAndUnregisterRemovesPreparedRenderer() {
        var renderer = (dev.equipmentstructure.api.client.appearance.BlockbenchModelAssetRenderer)
                (context, model) -> {};
        BlockbenchModelAssetRegistry.register(ASSET, MODEL, renderer);
        assertTrue(BlockbenchModelAssetRegistry.preparedAssets().isEmpty());
        assertTrue(AppearanceModelAssetRegistry.get(ASSET).isEmpty());
        assertThrows(IllegalStateException.class, () -> BlockbenchModelAssetRegistry.register(
                ASSET, MODEL, (context, model) -> {}));
        assertTrue(BlockbenchModelAssetRegistry.unregister(ASSET));
        assertFalse(BlockbenchModelAssetRegistry.unregister(ASSET));
        assertTrue(BlockbenchModelAssetRegistry.prepared(ASSET).isEmpty());
    }

    private static net.minecraft.resources.ResourceLocation id(String path) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blockbench_registry_test", path);
    }
}
