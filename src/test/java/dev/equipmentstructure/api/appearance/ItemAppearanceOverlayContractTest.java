package dev.equipmentstructure.api.appearance;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.client.appearance.AppearanceModelAssetRegistry;
import dev.equipmentstructure.api.client.appearance.EquipmentAppearanceScene;
import dev.equipmentstructure.api.client.appearance.EquipmentItemAppearanceOverlays;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.equipmentstructure.api.appearance.AppearanceDefinitions.*;
import static org.junit.jupiter.api.Assertions.*;

class ItemAppearanceOverlayContractTest {
    private static final ResourceLocation HOST = id("host"), SLOT = id("slot"), PART = id("part"), ASSET = id("asset");
    private static final AppearanceSupport SUPPORT = new AppearanceSupport(0, Set.of(ASSET), Set.of(), false);

    @AfterEach void cleanup() {
        EquipmentItemAppearanceOverlays.clear();
        AppearanceModelAssetRegistry.clear();
    }

    @Test void registrationRejectsBadUnitsAndNonItemScenesWithoutLeavingEntries() {
        for (float bad : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY, Float.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> EquipmentItemAppearanceOverlays.register(
                    HOST, Set.of(ItemDisplayContext.GUI), bad, () -> SUPPORT));
        }
        assertThrows(IllegalArgumentException.class, () -> EquipmentItemAppearanceOverlays.register(
                HOST, Set.of(ItemDisplayContext.HEAD), 16, () -> SUPPORT));
        EquipmentItemAppearanceOverlays.register(HOST, Set.of(ItemDisplayContext.GUI), 16, () -> SUPPORT);
        assertThrows(IllegalStateException.class, () -> EquipmentItemAppearanceOverlays.register(
                HOST, Set.of(ItemDisplayContext.GUI), 16, () -> SUPPORT));
        assertTrue(EquipmentItemAppearanceOverlays.unregister(HOST));
        assertFalse(EquipmentItemAppearanceOverlays.unregister(HOST));
    }

    @Test void sceneFramesRejectUnsupportedOrEmptyMappingsWithoutReservingHost() {
        var frame = new EquipmentItemAppearanceOverlays.Frame(16, AppearanceTransform.IDENTITY);
        assertThrows(IllegalArgumentException.class, () -> EquipmentItemAppearanceOverlays.registerFrames(HOST, Map.of(), () -> SUPPORT));
        assertThrows(IllegalArgumentException.class, () -> EquipmentItemAppearanceOverlays.registerFrames(HOST,
                Map.of(ItemDisplayContext.HEAD, frame), () -> SUPPORT));
        EquipmentItemAppearanceOverlays.registerFrames(HOST, Map.of(ItemDisplayContext.GUI, frame,
                ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, new EquipmentItemAppearanceOverlays.Frame(32,
                        new AppearanceTransform(new AppearanceVector(-16, -16, -16), AppearanceRotation.IDENTITY))), () -> SUPPORT);
        assertTrue(EquipmentItemAppearanceOverlays.unregister(HOST));
    }

    @Test void modelCallbackGetsCalibratedTransformAndCannotCorruptCallerPose() {
        var pose = new PoseStack();
        pose.translate(1, 2, 3);
        var before = new Matrix4f(pose.last().pose());
        var calls = new AtomicInteger();
        AppearanceModelAssetRegistry.register(ASSET, context -> {
            calls.incrementAndGet();
            Vector3f origin = context.poseStack().last().pose().transformPosition(new Vector3f());
            assertEquals(3, origin.x, 0.0001);
            assertEquals(5, origin.y, 0.0001);
            assertEquals(7, origin.z, 0.0001);
            // Even an unbalanced addon stack is isolated from the vanilla caller.
            context.poseStack().pushPose();
            context.poseStack().translate(100, 100, 100);
        });
        assertEquals(1, AppearanceModelAssetRegistry.render(plan(0), SUPPORT,
                EquipmentAppearanceScene.GUI, pose, type -> null, 0, 0));
        assertEquals(1, calls.get());
        assertEquals(before, pose.last().pose());
        assertTrue(pose.clear());
    }

    @Test void missingUnregisteredAndStaleAssetsNeverReachCallback() {
        var calls = new AtomicInteger();
        AppearanceModelAssetRegistry.register(ASSET, context -> calls.incrementAndGet());
        var poses = new PoseStack();
        var missing = new AppearanceSupport(0, Set.of(), Set.of(), false);
        assertEquals(0, AppearanceModelAssetRegistry.render(plan(0), missing,
                EquipmentAppearanceScene.GUI, poses, type -> null, 0, 0));
        assertEquals(0, AppearanceModelAssetRegistry.render(plan(1),
                new AppearanceSupport(1, Set.of(ASSET), Set.of(), false),
                EquipmentAppearanceScene.GUI, poses, type -> null, 0, 0));
        AppearanceModelAssetRegistry.unregister(ASSET);
        assertEquals(0, AppearanceModelAssetRegistry.render(plan(0), SUPPORT,
                EquipmentAppearanceScene.GUI, poses, type -> null, 0, 0));
        assertEquals(0, calls.get());
    }

    @Test void callbackExceptionDoesNotUnbalanceVanillaPose() {
        var pose = new PoseStack();
        AppearanceModelAssetRegistry.register(ASSET, context -> {
            context.poseStack().popPose();
            throw new IllegalStateException("contract failure fixture");
        });
        assertEquals(0, AppearanceModelAssetRegistry.render(plan(0), SUPPORT,
                EquipmentAppearanceScene.GUI, pose, type -> null, 0, 0));
        assertTrue(pose.clear());
        assertEquals(new Matrix4f(), pose.last().pose());
    }

    private static AppearancePlan plan(long generation) {
        var frame = new AppearanceTransform(new AppearanceVector(2, 3, 4), AppearanceRotation.IDENTITY);
        var host = new Host(HOST, Map.of(SLOT, new Port(frame)),
                Map.of(SLOT, new Binding(SLOT, new Joint(HOST, ANCHOR_ONLY, AppearanceTransform.IDENTITY), Set.of())), Set.of());
        var part = new Part(PART, ASSET, AppearanceTransform.IDENTITY, Set.of());
        var structure = new EquipmentStructure(HOST, HOST, List.of(EquipmentSlotDefinition.of(SLOT, PART)),
                Map.of(SLOT, List.of(new EquipmentComponentInstance(PART, PART))));
        return AppearanceResolver.resolve(structure, new AppearanceCatalog(generation, Map.of(HOST, host), Map.of(PART, part)),
                new AppearanceSupport(generation, Set.of(ASSET), Set.of(), false));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("overlay_test", path);
    }
}
