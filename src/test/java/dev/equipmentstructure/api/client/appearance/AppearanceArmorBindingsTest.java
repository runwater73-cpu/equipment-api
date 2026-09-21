package dev.equipmentstructure.api.client.appearance;

import dev.equipmentstructure.api.appearance.AppearanceSupport;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class AppearanceArmorBindingsTest {
    private static final ResourceLocation HOST = ResourceLocation.parse("test:armor"), SLOT = ResourceLocation.parse("test:part");
    private static final AppearanceSupport SUPPORT = new AppearanceSupport(0, Set.of(), Set.of(), false);
    @AfterEach void clear() { AppearanceArmorBindings.clear(); }

    @Test void rejectsHandsAnimalBodyAndMismatchedBones() {
        for (var slot : Set.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.BODY)) {
            assertThrows(IllegalArgumentException.class, () -> AppearanceArmorBindings.register(HOST, slot, () -> SUPPORT));
        }
        assertThrows(IllegalArgumentException.class, () -> AppearanceArmorBindings.register(HOST, EquipmentSlot.HEAD,
                16, Map.of(SLOT, Set.of(ArmorAttachment.LEFT_LEG)), () -> SUPPORT));
        assertThrows(IllegalArgumentException.class, () -> AppearanceArmorBindings.register(HOST, EquipmentSlot.HEAD,
                0, Map.of(), () -> SUPPORT));
    }

    @Test void exactSlotOverridesWildcardAndUnregisterRestoresIt() {
        AppearanceArmorBindings.register(HOST, () -> SUPPORT);
        var common = AppearanceArmorBindings.get(HOST, EquipmentSlot.CHEST);
        AppearanceArmorBindings.register(HOST, EquipmentSlot.CHEST, 32,
                Map.of(SLOT, Set.of(ArmorAttachment.LEFT_ARM)), () -> SUPPORT);
        var exact = AppearanceArmorBindings.get(HOST, EquipmentSlot.CHEST);
        assertNotSame(common, exact);
        assertEquals(Set.of(ArmorAttachment.LEFT_ARM), exact.anchors(SLOT, EquipmentSlot.CHEST));
        assertSame(common, AppearanceArmorBindings.get(HOST, EquipmentSlot.HEAD));
        assertTrue(AppearanceArmorBindings.unregister(HOST, EquipmentSlot.CHEST));
        assertSame(common, AppearanceArmorBindings.get(HOST, EquipmentSlot.CHEST));
    }

    @Test void defaultsAreSlotSpecificAndMappingsAreImmutable() {
        var anchors = new java.util.HashSet<>(Set.of(ArmorAttachment.RIGHT_LEG));
        var map = new java.util.HashMap<ResourceLocation, Set<ArmorAttachment>>(); map.put(SLOT, anchors);
        AppearanceArmorBindings.register(HOST, EquipmentSlot.FEET, 16, map, () -> SUPPORT);
        anchors.clear(); map.clear();
        assertEquals(Set.of(ArmorAttachment.RIGHT_LEG), AppearanceArmorBindings.get(HOST, EquipmentSlot.FEET).anchors(SLOT, EquipmentSlot.FEET));
        assertEquals(Set.of(ArmorAttachment.HEAD), ArmorAttachment.defaults(EquipmentSlot.HEAD));
        assertEquals(Set.of(ArmorAttachment.BODY), ArmorAttachment.defaults(EquipmentSlot.CHEST));
        assertEquals(Set.of(ArmorAttachment.LEFT_LEG, ArmorAttachment.RIGHT_LEG), ArmorAttachment.defaults(EquipmentSlot.LEGS));
    }

    @Test void standardHumanoidDefaultMatchesPreviewAndRequiresOptInForHiding() {
        assertNull(AppearanceArmorBindings.resolve(HOST, EquipmentSlot.BODY));
        assertNull(AppearanceArmorBindings.resolve(HOST, EquipmentSlot.MAINHAND));
        var fallback = AppearanceArmorBindings.resolve(HOST, EquipmentSlot.HEAD);
        assertNotNull(fallback);
        assertEquals(16, fallback.modelUnitsPerBlock());
        assertEquals(Set.of(ArmorAttachment.HEAD), fallback.anchors(SLOT, EquipmentSlot.HEAD));
        assertFalse(fallback.canHideOriginal());

        AppearanceArmorBindings.register(HOST, EquipmentSlot.HEAD, 32,
                Map.of(SLOT, Set.of(ArmorAttachment.HEAD)), () -> SUPPORT);
        var registered = AppearanceArmorBindings.resolve(HOST, EquipmentSlot.HEAD);
        assertEquals(32, registered.modelUnitsPerBlock());
        assertNotSame(fallback, registered);
        assertTrue(registered.canHideOriginal());
    }

    @Test void selfManagedArmorCanDisableDefaultBoneMapping() {
        AppearanceArmorBindings.setAutomaticEnabled(HOST, false);
        assertNull(AppearanceArmorBindings.resolve(HOST, EquipmentSlot.CHEST));
        AppearanceArmorBindings.register(HOST, EquipmentSlot.HEAD, () -> SUPPORT);
        assertNotNull(AppearanceArmorBindings.resolve(HOST, EquipmentSlot.HEAD));
        AppearanceArmorBindings.setAutomaticEnabled(HOST, true);
        assertNotNull(AppearanceArmorBindings.resolve(HOST, EquipmentSlot.CHEST));
    }

    @Test void emptyRenderSubjectKeepsLegacyAssetCallbacksRegistryIndependent() {
        var subject = AppearanceRenderSubject.empty();
        assertTrue(subject.structure().isEmpty());
        assertTrue(subject.component(SLOT).isEmpty());
        assertTrue(subject.itemContext().isEmpty());
        assertTrue(subject.equipmentSlot().isEmpty());
        assertEquals(0, subject.partialTick());
    }
}
