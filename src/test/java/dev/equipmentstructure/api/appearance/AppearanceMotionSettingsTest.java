package dev.equipmentstructure.api.appearance;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentFeatureConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppearanceMotionSettingsTest {
    private static final AppearanceOrbitMotion MOTION = new AppearanceOrbitMotion(AppearanceVector.ZERO,
            new AppearanceVector(0, 1, 0), 90, AppearanceOrbitMotion.Direction.POSITIVE, 0, true);
    private static final AppearanceTransform ANCHOR = new AppearanceTransform(new AppearanceVector(8, 3, 0), AppearanceRotation.IDENTITY);

    @Test void equipmentCenterDoesNotFollowDifferentAttachmentAnchors() {
        assertEquals(AppearanceVector.ZERO, MOTION.resolve(ANCHOR, AppearanceMotionSettings.DEFAULT).pivot());
        assertEquals(AppearanceVector.ZERO, MOTION.resolve(new AppearanceTransform(new AppearanceVector(-5, 0, 4),
                AppearanceRotation.IDENTITY), AppearanceMotionSettings.DEFAULT).pivot());
        var base = new AppearanceTransform(new AppearanceVector(10, 3, 0), AppearanceRotation.IDENTITY);
        var at90 = MOTION.resolve(ANCHOR, AppearanceMotionSettings.DEFAULT).applyAngles(base, 90, 0);
        assertEquals(0, at90.position().x(), 1e-6);
        assertEquals(3, at90.position().y(), 1e-6);
        assertEquals(-10, at90.position().z(), 1e-6);
    }
    @Test void localCenterIsAnIndependentOptionAndAuthorDefaultCanBeOverridden() {
        var settings = new AppearanceMotionSettings(AppearanceMotionSettings.Center.LOCAL, AppearanceRotation.IDENTITY);
        var resolved = MOTION.resolve(ANCHOR, settings);
        var result = resolved.applyAngles(new AppearanceTransform(new AppearanceVector(10, 3, 0), AppearanceRotation.IDENTITY), 90, 0);
        assertEquals(8, result.position().x(), 1e-6); assertEquals(-2, result.position().z(), 1e-6);
        var authorLocal = MOTION.withPivotSpace(AppearanceOrbitMotion.PivotSpace.LOCAL);
        assertEquals(ANCHOR.position(), authorLocal.resolve(ANCHOR, AppearanceMotionSettings.DEFAULT).pivot());
        assertEquals(AppearanceVector.ZERO, authorLocal.resolve(ANCHOR,
                new AppearanceMotionSettings(AppearanceMotionSettings.Center.HOST, AppearanceRotation.IDENTITY)).pivot());
    }
    @Test void storageAndVisibilityChangesRetainMotionAndAuthorData() {
        var id = ResourceLocation.parse("test:part"); var tag = new CompoundTag(); tag.putString("author:quality", "kept");
        var part = new EquipmentComponentInstance(id, id, id, tag);
        var settings = new AppearanceMotionSettings(AppearanceMotionSettings.Center.LOCAL, new AppearanceRotation(1, 0, 0, 1));
        var edit = AppearancePartPresentation.read(part).withMotion(settings);
        var changed = edit.apply(part);
        assertEquals(edit, AppearancePartPresentation.read(changed));
        var hidden = AppearancePartPresentation.read(changed).withVisible(false).apply(changed);
        assertEquals(settings, AppearanceMotionSettings.read(hidden));
        assertEquals("kept", changed.data().getString("author:quality"));
        assertEquals(AppearanceMotionSettings.DEFAULT, AppearanceMotionSettings.read(part));
        assertEquals(part, AppearanceMotionSettings.DEFAULT.apply(changed));
    }
    @Test void disabledPositionEditingAlsoRejectsOrbitEditsButAllowsVisibility() {
        var rules = new EquipmentFeatureConfig.Rules(false, true, true);
        var current = new AppearancePartPresentation(AppearancePose.IDENTITY, true);
        var changed = current.withMotion(new AppearanceMotionSettings(AppearanceMotionSettings.Center.LOCAL, AppearanceRotation.IDENTITY));
        assertFalse(rules.allowsPart(current, changed));
        assertTrue(rules.allowsPart(changed, changed.withVisible(false)));
    }
    @Test void resourcePivotSpaceRoundTripsAndRejectsTypos() {
        var id = ResourceLocation.parse("test:part");
        var json = JsonParser.parseString("""
                {"format_version":3,"id":"test:part","asset":"test:asset",
                 "motion":{"type":"orbit","pivot_space":"local","speed_degrees_per_second":30}}
                """);
        var resource = AppearanceResourceCodec.COMPONENT.parse(JsonOps.INSTANCE, json).getOrThrow();
        var encoded = AppearanceResourceCodec.COMPONENT.encodeStart(JsonOps.INSTANCE, resource).getOrThrow();
        assertEquals(AppearanceOrbitMotion.PivotSpace.LOCAL, AppearanceResourceDecoder.decodeComponent(id, encoded)
                .getOrThrow().motion().orElseThrow().pivotSpace());
        json.getAsJsonObject().getAsJsonObject("motion").addProperty("pivot_space", "loacl");
        assertTrue(AppearanceResourceCodec.COMPONENT.parse(JsonOps.INSTANCE, json).error().isPresent());
    }
}
