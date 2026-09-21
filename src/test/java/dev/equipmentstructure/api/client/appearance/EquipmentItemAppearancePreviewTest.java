package dev.equipmentstructure.api.client.appearance;

import dev.equipmentstructure.api.appearance.AppearanceSupport;
import dev.equipmentstructure.api.appearance.AppearanceTransform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentItemAppearancePreviewTest {
    private static final ResourceLocation HOST = ResourceLocation.parse("test:preview_host");
    private static final AppearanceSupport SUPPORT = new AppearanceSupport(0, Set.of(), Set.of(), false);

    @AfterEach void clear() {
        EquipmentItemAppearanceOverlays.clear();
    }

    @Test void standardBakedModelsShareOneDefaultFrameAcrossEditorAndWorld() {
        assertNull(EquipmentItemAppearanceOverlays.binding(HOST, false));
        var fallback = EquipmentItemAppearanceOverlays.binding(HOST, true);
        assertNotNull(fallback);
        assertEquals(16, fallback.frames().get(ItemDisplayContext.GUI).modelUnitsPerBlock());
        assertEquals(EquipmentItemAppearanceOverlays.ITEM_CONTEXTS, fallback.frames().keySet());
        assertSame(fallback, EquipmentItemAppearanceOverlays.binding(ResourceLocation.parse("test:another_host"), true));
    }

    @Test void explicitSceneCalibrationOverridesTheDefaultWithoutFillingExcludedScenes() {
        var frame = new EquipmentItemAppearanceOverlays.Frame(32, AppearanceTransform.IDENTITY);
        EquipmentItemAppearanceOverlays.registerFrames(HOST,
                Map.of(ItemDisplayContext.GUI, frame), () -> SUPPORT);

        assertSame(frame, EquipmentItemAppearanceOverlays.binding(HOST, true)
                .frames().get(ItemDisplayContext.GUI));
        assertNull(EquipmentItemAppearanceOverlays.binding(HOST, true).frames().get(ItemDisplayContext.GROUND));
        assertSame(frame, EquipmentItemAppearanceOverlays.binding(HOST, false).frames().get(ItemDisplayContext.GUI));
    }

    @Test void selfManagedHostCanDisableAutomaticRenderingAndStillOptIntoExplicitScenes() {
        EquipmentItemAppearanceOverlays.setAutomaticEnabled(HOST, false);
        assertNull(EquipmentItemAppearanceOverlays.binding(HOST, true));
        EquipmentItemAppearanceOverlays.register(HOST, Set.of(ItemDisplayContext.GUI), 32, () -> SUPPORT);
        assertNotNull(EquipmentItemAppearanceOverlays.binding(HOST, false));
        EquipmentItemAppearanceOverlays.unregister(HOST);
        assertNull(EquipmentItemAppearanceOverlays.binding(HOST, true));
        EquipmentItemAppearanceOverlays.setAutomaticEnabled(HOST, true);
        assertNotNull(EquipmentItemAppearanceOverlays.binding(HOST, true));
    }
}
