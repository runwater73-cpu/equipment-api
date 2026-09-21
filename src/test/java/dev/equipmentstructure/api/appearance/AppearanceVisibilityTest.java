package dev.equipmentstructure.api.appearance;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.client.appearance.AppearanceModelAssetRegistry;
import dev.equipmentstructure.api.client.appearance.EquipmentAppearanceScene;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.equipmentstructure.api.appearance.AppearanceDefinitions.*;
import static org.junit.jupiter.api.Assertions.*;

class AppearanceVisibilityTest {
    private static final ResourceLocation HOST = id("host"), ROOT = id("root"), CHILD = id("child"),
            PART = id("part"), ASSET = id("asset"), EXIT = id("exit"), ORIGINAL = id("original");

    @AfterEach void clear() { AppearanceModelAssetRegistry.clear(); }

    @Test void hiddenComponentSurvivesSerializationAndPoseEditsWithoutTouchingAuthorData() {
        var data = new CompoundTag(); data.putString("author:value", "keep");
        var part = new EquipmentComponentInstance(PART, PART, data);
        assertTrue(AppearanceVisibilityStorage.componentVisible(part));
        var hidden = AppearanceVisibilityStorage.withComponentVisible(part, false);
        hidden = AppearancePoseStorage.with(hidden, new AppearancePose(1, 2, 3, AppearanceRotation.IDENTITY, 1.5));
        var encoded = EquipmentComponentInstance.CODEC.encodeStart(NbtOps.INSTANCE, hidden).getOrThrow();
        var restored = EquipmentComponentInstance.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertFalse(AppearanceVisibilityStorage.componentVisible(restored));
        assertTrue(AppearanceVisibilityStorage.componentVisible(part));
        assertEquals("keep", restored.data().getString("author:value"));
        var shown = AppearanceVisibilityStorage.withComponentVisible(restored, true);
        assertTrue(AppearanceVisibilityStorage.componentVisible(shown));
        assertEquals(AppearancePoseStorage.read(restored), AppearancePoseStorage.read(shown));
    }

    @Test void hiddenFixedParentKeepsChildFrameAndDoesNotClaimOriginalGeometry() {
        var joint = new Joint(HOST, ANCHOR_ONLY, AppearanceTransform.IDENTITY);
        var host = new Host(HOST, Map.of(ROOT, new Port(AppearanceTransform.IDENTITY),
                CHILD, Port.onComponent(ROOT, EXIT, AppearanceTransform.IDENTITY)),
                Map.of(ROOT, new Binding(ROOT, joint, Set.of(ORIGINAL), PlacementFreedom.FIXED),
                        CHILD, new Binding(CHILD, joint, Set.of())), Set.of(ORIGINAL));
        var part = new Part(PART, ASSET, AppearanceTransform.IDENTITY, Set.of(), RenderProfile.DEFAULT,
                Map.of(EXIT, new AppearanceTransform(new AppearanceVector(0, 5, 0), AppearanceRotation.IDENTITY)));
        var catalog = new AppearanceCatalog(0, Map.of(HOST, host), Map.of(PART, part));
        var support = new AppearanceSupport(0, Set.of(ASSET), Set.of(), true);
        var installed = new EquipmentComponentInstance(PART, PART);
        var structure = new EquipmentStructure(HOST, HOST,
                List.of(EquipmentSlotDefinition.of(ROOT, PART), EquipmentSlotDefinition.of(CHILD, PART)),
                Map.of(ROOT, List.of(installed), CHILD, List.of(installed)));
        var shown = AppearanceResolver.resolve(structure, catalog, support);
        var hidden = AppearanceResolver.resolve(structure.withComponent(ROOT,
                AppearanceVisibilityStorage.withComponentVisible(installed, false)), catalog, support);
        assertEquals(2, hidden.placements().size());
        assertTrue(hidden.hiddenOriginalElements().isEmpty());
        assertEquals(shown.placements().stream().filter(p -> p.slotId().equals(CHILD)).findFirst().orElseThrow(),
                hidden.placements().stream().filter(p -> p.slotId().equals(CHILD)).findFirst().orElseThrow());
        var calls = new AtomicInteger();
        AppearanceModelAssetRegistry.register(ASSET, context -> {
            assertEquals(CHILD, context.placement().slotId()); calls.incrementAndGet();
        });
        assertEquals(1, AppearanceModelAssetRegistry.render(hidden, support, EquipmentAppearanceScene.GUI,
                new PoseStack(), type -> null, 0, 0));
        assertEquals(1, calls.get());
    }

    @Test void visibilityOnlyPresentationPreservesAbsentOrMalformedPoseAndAuthorData() {
        var data = new CompoundTag(); data.putString("author:value", "keep");
        data.putString("equipment_structure_api:appearance_pose", "old-custom-data");
        var part = new EquipmentComponentInstance(PART, PART, data);
        var hidden = new AppearancePartPresentation(AppearancePose.IDENTITY, false).apply(part);
        assertEquals("old-custom-data", hidden.data().getString("equipment_structure_api:appearance_pose"));
        assertEquals("keep", hidden.data().getString("author:value"));
        assertFalse(AppearanceVisibilityStorage.componentVisible(hidden));
        var restored = new AppearancePartPresentation(AppearancePose.IDENTITY, true).apply(hidden);
        assertEquals(part, restored);
    }

    private static ResourceLocation id(String path) { return ResourceLocation.parse("visibility_test:" + path); }
}
