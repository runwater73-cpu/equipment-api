package dev.equipmentstructure.api.client.appearance;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.appearance.AppearanceCatalog;
import dev.equipmentstructure.api.appearance.AppearanceDefinitions;
import dev.equipmentstructure.api.appearance.AppearanceResolver;
import dev.equipmentstructure.api.appearance.AppearanceRotation;
import dev.equipmentstructure.api.appearance.AppearanceSupport;
import dev.equipmentstructure.api.appearance.AppearanceTransform;
import dev.equipmentstructure.api.appearance.AppearanceVector;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentItemAppearanceCompletenessTest {
    private static final ResourceLocation HOST = id("host");
    private static final ResourceLocation READY_SLOT = id("ready_slot");
    private static final ResourceLocation MISSING_SLOT = id("missing_slot");
    private static final ResourceLocation READY_PART = id("ready_part");
    private static final ResourceLocation MISSING_PART = id("missing_part");
    private static final ResourceLocation ASSET = id("asset");
    private static final AppearanceSupport SUPPORT = new AppearanceSupport(0, Set.of(ASSET), Set.of(), false);

    @AfterEach
    void cleanup() {
        AppearanceModelAssetRegistry.unregister(ASSET);
    }

    @Test
    void incompleteInstalledComponentsPreventHidingOriginal() {
        AppearanceModelAssetRegistry.register(ASSET, context -> { });
        var plan = resolve(Set.of(READY_PART));

        assertFalse(EquipmentItemAppearanceOverlays.complete(plan, 0, 0, false));
    }

    @Test
    void allInstalledComponentsReadyAllowsHidePreflight() {
        AppearanceModelAssetRegistry.register(ASSET, context -> { });
        var plan = resolve(Set.of(READY_PART, MISSING_PART));

        assertTrue(EquipmentItemAppearanceOverlays.complete(plan, 0, 0, false));
    }

    private static dev.equipmentstructure.api.appearance.AppearancePlan resolve(Set<ResourceLocation> parts) {
        var identity = new AppearanceTransform(new AppearanceVector(0, 0, 0), AppearanceRotation.IDENTITY);
        var bindings = Map.of(
                READY_SLOT, binding(READY_SLOT, identity),
                MISSING_SLOT, binding(MISSING_SLOT, identity));
        var host = new AppearanceDefinitions.Host(HOST,
                Map.of(READY_SLOT, new AppearanceDefinitions.Port(identity),
                        MISSING_SLOT, new AppearanceDefinitions.Port(identity)), bindings, Set.of());
        var definitions = new java.util.HashMap<ResourceLocation, AppearanceDefinitions.Part>();
        if (parts.contains(READY_PART)) definitions.put(READY_PART,
                new AppearanceDefinitions.Part(READY_PART, ASSET, identity, Set.of()));
        if (parts.contains(MISSING_PART)) definitions.put(MISSING_PART,
                new AppearanceDefinitions.Part(MISSING_PART, ASSET, identity, Set.of()));
        var structure = new EquipmentStructure(HOST, HOST,
                List.of(EquipmentSlotDefinition.of(READY_SLOT, READY_PART),
                        EquipmentSlotDefinition.of(MISSING_SLOT, MISSING_PART)),
                Map.of(READY_SLOT, List.of(new EquipmentComponentInstance(READY_PART, READY_PART)),
                        MISSING_SLOT, List.of(new EquipmentComponentInstance(MISSING_PART, MISSING_PART))));
        return AppearanceResolver.resolve(structure,
                new AppearanceCatalog(0, Map.of(HOST, host), Map.copyOf(definitions)), SUPPORT);
    }

    private static AppearanceDefinitions.Binding binding(ResourceLocation slot,
                                                         AppearanceTransform identity) {
        return new AppearanceDefinitions.Binding(slot,
                new AppearanceDefinitions.Joint(HOST, AppearanceDefinitions.ANCHOR_ONLY, identity), Set.of());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("appearance_completeness", path);
    }
}
