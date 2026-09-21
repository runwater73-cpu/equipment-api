package dev.equipmentstructure.api.appearance;

import dev.equipmentstructure.api.client.appearance.EquipmentAppearanceRenderer;
import dev.equipmentstructure.api.client.appearance.EquipmentAppearanceRendererRegistry;
import dev.equipmentstructure.api.client.appearance.EquipmentAppearanceScene;
import dev.equipmentstructure.api.client.appearance.AppearanceModelAssetRegistry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.item.ItemDisplayContext;

class EquipmentAppearanceRendererRegistryTest {
    private static final ResourceLocation HOST = ResourceLocation.fromNamespaceAndPath("test", "host");

    @AfterEach
    void cleanup() {
        EquipmentAppearanceRendererRegistry.clear();
    }

    @Test
    void materializesDeferredRendererOnce() {
        AtomicInteger factories = new AtomicInteger();
        EquipmentAppearanceRenderer renderer = context -> true;
        EquipmentAppearanceRendererRegistry.register(HOST, () -> {
            factories.incrementAndGet();
            return renderer;
        });
        // A renderer may be requested before the client setup load hook runs.
        assertSame(renderer, EquipmentAppearanceRendererRegistry.get(HOST).orElseThrow());
        EquipmentAppearanceRendererRegistry.load();
        EquipmentAppearanceRendererRegistry.load();
        assertSame(renderer, EquipmentAppearanceRendererRegistry.get(HOST).orElseThrow());
        assertEquals(1, factories.get());
    }

    @Test
    void failingOrRecursiveFactoriesAreIsolated() {
        ResourceLocation failing = ResourceLocation.fromNamespaceAndPath("test", "failing");
        ResourceLocation recursive = ResourceLocation.fromNamespaceAndPath("test", "recursive");
        ResourceLocation healthy = ResourceLocation.fromNamespaceAndPath("test", "healthy");
        EquipmentAppearanceRendererRegistry.register(failing, () -> { throw new IllegalStateException("boom"); });
        EquipmentAppearanceRendererRegistry.register(recursive, () -> {
            assertTrue(EquipmentAppearanceRendererRegistry.get(recursive).isEmpty());
            return context -> true;
        });
        EquipmentAppearanceRenderer good = context -> true;
        EquipmentAppearanceRendererRegistry.register(healthy, () -> good);

        assertTrue(EquipmentAppearanceRendererRegistry.get(failing).isEmpty());
        assertTrue(EquipmentAppearanceRendererRegistry.get(recursive).isPresent());
        assertSame(good, EquipmentAppearanceRendererRegistry.get(healthy).orElseThrow());
    }

    @Test
    void duplicateHostRegistrationIsRejected() {
        EquipmentAppearanceRendererRegistry.register(HOST, () -> context -> true);
        assertThrows(IllegalStateException.class,
                () -> EquipmentAppearanceRendererRegistry.register(HOST, () -> context -> false));
    }

    @Test
    void renderersCanOptOutOfScenes() {
        EquipmentAppearanceRenderer renderer = new EquipmentAppearanceRenderer() {
            @Override
            public boolean supports(EquipmentAppearanceScene scene) {
                return scene == EquipmentAppearanceScene.GUI;
            }

            @Override
            public boolean render(dev.equipmentstructure.api.client.appearance.EquipmentAppearanceRenderContext context) {
                return true;
            }
        };
        EquipmentAppearanceRendererRegistry.register(HOST, () -> renderer);
        EquipmentAppearanceRendererRegistry.load();
        assertTrue(renderer.supports(EquipmentAppearanceScene.GUI));
        assertFalse(renderer.supports(EquipmentAppearanceScene.ARMOR));
    }

    @Test
    void mapsVanillaDisplayContextsToStableScenes() {
        assertEquals(EquipmentAppearanceScene.GUI,
                EquipmentAppearanceScene.from(ItemDisplayContext.GUI));
        assertEquals(EquipmentAppearanceScene.FIRST_PERSON,
                EquipmentAppearanceScene.from(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND));
        assertEquals(EquipmentAppearanceScene.THIRD_PERSON,
                EquipmentAppearanceScene.from(ItemDisplayContext.THIRD_PERSON_LEFT_HAND));
        assertEquals(EquipmentAppearanceScene.GROUND,
                EquipmentAppearanceScene.from(ItemDisplayContext.GROUND));
    }

    @Test
    void clearingCompleteRenderersDoesNotClearIndependentModelAssets() {
        ResourceLocation asset = ResourceLocation.fromNamespaceAndPath("test", "asset");
        AppearanceModelAssetRegistry.register(asset, context -> { });
        EquipmentAppearanceRendererRegistry.register(HOST, () -> context -> true);

        EquipmentAppearanceRendererRegistry.clear();

        assertTrue(AppearanceModelAssetRegistry.get(asset).isPresent());
        AppearanceModelAssetRegistry.unregister(asset);
    }
}
