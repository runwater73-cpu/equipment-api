package dev.equipmentstructure.api.appearance;

import dev.equipmentstructure.api.client.appearance.AppearanceResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppearanceResourceReloadListenerTest {
    @Test void logicalResourceIdsStripOnlyTheDeclaredDirectoryAndJsonSuffix() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("maker", "curved_blade"),
                AppearanceResourceReloadListener.logicalId(
                        AppearanceResourceReloadListener.COMPONENT_DIRECTORY,
                        ResourceLocation.fromNamespaceAndPath("maker", "equipment_structure_api/appearance/component/curved_blade.json")));
        assertNull(AppearanceResourceReloadListener.logicalId(
                AppearanceResourceReloadListener.HOST_DIRECTORY,
                ResourceLocation.fromNamespaceAndPath("maker", "equipment_structure_api/appearance/component/curved_blade.json")));
        assertNull(AppearanceResourceReloadListener.logicalId(
                AppearanceResourceReloadListener.COMPONENT_DIRECTORY,
                ResourceLocation.fromNamespaceAndPath("maker", "equipment_structure_api/appearance/component/.json")));
        assertNull(AppearanceResourceReloadListener.logicalId(
                AppearanceResourceReloadListener.COMPONENT_DIRECTORY,
                ResourceLocation.fromNamespaceAndPath("maker", "equipment_structure_api/appearance/component/dir//part.json")));
    }

    @Test void initialCatalogIsEmptyAndGenerationIsNonnegative() {
        assertNotNull(AppearanceResourceReloadListener.catalog());
        assertTrue(AppearanceResourceReloadListener.generation() >= 0);
    }
}
