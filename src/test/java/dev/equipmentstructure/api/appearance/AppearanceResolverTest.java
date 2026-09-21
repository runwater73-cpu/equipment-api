package dev.equipmentstructure.api.appearance;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructure;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static dev.equipmentstructure.api.appearance.AppearanceDefinitions.*;
import static dev.equipmentstructure.api.appearance.AppearanceMathTest.*;
import static dev.equipmentstructure.api.appearance.AppearancePlan.*;
import static org.junit.jupiter.api.Assertions.*;

class AppearanceResolverTest {
    private static final ResourceLocation HOST = id("host"), VISUAL = id("visual"), PART = id("part");
    private static final ResourceLocation SLOT = id("slot"), SLOT_B = id("slot_b"), PORT = id("port"), ASSET = id("asset");
    private static final ResourceLocation ELEMENT = id("original"), ELEMENT_B = id("original_b"), OWNER = id("owner");
    private static final Part APPEARANCE = new Part(id("part_visual"), ASSET, translated(0, 1, 0), Set.of());

    @Test void noHostAppearanceRetainsOriginalWithoutWarningsOrHiddenElements() {
        var plan = AppearanceResolver.resolve(structure(Map.of(SLOT, PART)), new AppearanceCatalog(0, Map.of(), Map.of()), support());
        assertOriginal(plan, Reason.HOST_UNCONFIGURED, Status.UNCONFIGURED);
        assertTrue(plan.hostAppearanceId().isEmpty());
    }

    @Test void unconfiguredPartDoesNotClaimOriginalEvenIfItsBindingIsBroken() {
        var broken = new Binding(id("missing"), new Joint(OWNER, id("unknown_strategy"), AppearanceTransform.IDENTITY), Set.of(id("unknown_element")));
        var host = new Host(VISUAL, Map.of(), Map.of(SLOT, broken), Set.of());
        var plan = AppearanceResolver.resolve(structure(Map.of(SLOT, PART)), catalog(host, Map.of()), support());
        assertOriginal(plan, Reason.COMPONENT_UNCONFIGURED, Status.UNCONFIGURED);
    }

    @Test void dynamicSlotWithoutVisualBindingDoesNotGuessFromTypeOrUiPosition() {
        var host = new Host(VISUAL, Map.of(PORT, new Port(translated(0, 7, 0))), Map.of(), Set.of(ELEMENT));
        assertOriginal(resolve(host), Reason.BINDING_UNCONFIGURED, Status.UNCONFIGURED);
    }

    @Test void emptySlotDoesNotHideDefaultElements() {
        var empty = new EquipmentStructure(HOST, HOST, List.of(EquipmentSlotDefinition.of(SLOT, PART)), Map.of());
        var plan = AppearanceResolver.resolve(empty, catalog(host(), Map.of(PART, APPEARANCE)), support());
        assertTrue(plan.usesOriginalAppearance());
        assertTrue(plan.outcomes().isEmpty());
        assertTrue(plan.hiddenOriginalElements().isEmpty());
    }

    @Test void preparedPlacementHasExplicitAnchorOwnerAndReplacementSet() {
        var plan = resolve(host());
        assertFalse(plan.usesOriginalAppearance());
        assertEquals(Set.of(ELEMENT), plan.hiddenOriginalElements());
        assertEquals(Status.READY, plan.outcomes().getFirst().status());
        assertEquals(Reason.NONE, plan.outcomes().getFirst().reason());
        var placed = plan.placements().getFirst();
        assertEquals(OWNER, placed.seamOwner());
        assertEquals(APPEARANCE.id(), placed.appearanceId());
        assertVector(new AppearanceVector(2, 10, 0), placed.transform().apply(new AppearanceVector(2, 4, 0)));
    }

    @Test void preparedPlacementRetainsComponentRenderProfile() {
        var profile = new RenderProfile(RenderMode.SURFACE, RenderLayer.SURFACE, 23, 0.25F);
        var part = new Part(APPEARANCE.id(), ASSET, APPEARANCE.mount(), Set.of(), profile);
        var plan = AppearanceResolver.resolve(structure(Map.of(SLOT, PART)),
                catalog(host(), Map.of(PART, part)), support());
        assertEquals(profile, plan.placements().getFirst().renderProfile());
    }

    @Test void fixedPlacementIgnoresStoredPlayerPoseAndOrbitOverrides() {
        var component = new EquipmentComponentInstance(PART, PART);
        component = AppearancePoseStorage.with(component, new AppearancePose(3, 2, 1,
                AppearanceRotation.IDENTITY, 1.5D));
        var settings = new AppearanceMotionSettings(AppearanceMotionSettings.Center.LOCAL, new AppearanceRotation(1, 0, 0, 1));
        component = settings.apply(component);
        var structure = new EquipmentStructure(HOST, HOST,
                List.of(EquipmentSlotDefinition.of(SLOT, PART)),
                Map.of(SLOT, List.of(component)));
        var fixedHost = new Host(VISUAL, host().ports(),
                Map.of(SLOT, new Binding(PORT,
                        new Joint(OWNER, ANCHOR_ONLY, AppearanceTransform.IDENTITY),
                        Set.of(ELEMENT), PlacementFreedom.FIXED)),
                Set.of(ELEMENT));
        var motion = new AppearanceOrbitMotion(AppearanceVector.ZERO, new AppearanceVector(0, 1, 0), 30,
                AppearanceOrbitMotion.Direction.POSITIVE, 0, true);
        var animated = new Part(APPEARANCE.id(), ASSET, APPEARANCE.mount(), Set.of(),
                APPEARANCE.renderProfile(), Map.of(), true, Optional.of(motion));
        var placed = AppearanceResolver.resolve(structure,
                catalog(fixedHost, Map.of(PART, animated)), support()).placements().getFirst();
        assertEquals(new AppearanceVector(0, 6, 0), placed.transform().position());
        assertEquals(1.0D, placed.scale());
        assertEquals(AppearanceVector.ZERO, placed.motion().orElseThrow().pivot());
        assertEquals(motion.axis(), placed.motion().orElseThrow().axis());
        assertEquals(settings, AppearanceMotionSettings.read(structure.component(SLOT).orElseThrow()));
    }

    @Test void missingAssetAndUnsupportedCapabilityFallBackBeforeHiding() {
        var structure = structure(Map.of(SLOT, PART));
        var catalog = catalog(host(), Map.of(PART, APPEARANCE));
        var missing = AppearanceResolver.resolve(structure, catalog, new AppearanceSupport(0, Set.of(), Set.of(), true));
        assertOriginal(missing, Reason.ASSET_NOT_READY, Status.FALLBACK);
        assertEquals(Optional.of(ASSET), missing.outcomes().getFirst().resource());
        var advanced = new Part(APPEARANCE.id(), ASSET, APPEARANCE.mount(), Set.of(id("cap_b"), id("cap_a")));
        var unsupported = AppearanceResolver.resolve(structure, catalog(host(), Map.of(PART, advanced)), support());
        assertOriginal(unsupported, Reason.UNSUPPORTED_CAPABILITY, Status.FALLBACK);
        assertEquals(Optional.of(id("cap_a")), unsupported.outcomes().getFirst().resource());
    }

    @Test void staleGenerationAndUnsplittableAdapterRetainWholeOriginal() {
        var structure = structure(Map.of(SLOT, PART));
        var catalog = catalog(host(), Map.of(PART, APPEARANCE));
        assertOriginal(AppearanceResolver.resolve(structure, catalog, new AppearanceSupport(1, Set.of(ASSET), Set.of(), true)),
                Reason.STALE_RESOURCES, Status.FALLBACK);
        assertOriginal(AppearanceResolver.resolve(structure, catalog, new AppearanceSupport(0, Set.of(ASSET), Set.of(), false)),
                Reason.UNSUPPORTED_ADAPTER, Status.FALLBACK);
    }

    @Test void unimplementedSeamStrategyCannotMasqueradeAsSimpleAlignment() {
        var joint = new Joint(OWNER, id("local_mask"), AppearanceTransform.IDENTITY);
        var host = new Host(VISUAL, host().ports(), Map.of(SLOT, new Binding(PORT, joint, Set.of(ELEMENT))), Set.of(ELEMENT));
        assertOriginal(resolve(host), Reason.UNSUPPORTED_JOINT, Status.FALLBACK);
    }

    @Test void hostMustExplicitlyAuthorizeReplacementTargets() {
        var host = new Host(VISUAL, host().ports(), host().bindings(), Set.of());
        assertOriginal(resolve(host), Reason.UNDECLARED_REPLACEMENT, Status.FALLBACK);
    }

    @Test void missingPortAndCycleAffectOnlyTheirDependentBranches() {
        for (boolean cyclic : new boolean[]{false, true}) {
            var ports = new HashMap<>(host().ports());
            ports.put(id("bad"), new Port(Optional.of(id("parent")), AppearanceTransform.IDENTITY));
            if (cyclic) ports.put(id("parent"), new Port(Optional.of(id("bad")), AppearanceTransform.IDENTITY));
            var host = new Host(VISUAL, ports, Map.of(SLOT, binding(PORT, ELEMENT), SLOT_B, binding(id("bad"), ELEMENT_B)), Set.of(ELEMENT, ELEMENT_B));
            var plan = AppearanceResolver.resolve(structure(Map.of(SLOT, PART, SLOT_B, PART)), catalog(host, Map.of(PART, APPEARANCE)), support());
            assertEquals(List.of(SLOT), plan.placements().stream().map(Placement::slotId).toList());
            assertEquals(Set.of(ELEMENT), plan.hiddenOriginalElements());
            assertEquals(cyclic ? Reason.CYCLIC_PORT : Reason.MISSING_PORT, plan.outcomes().get(1).reason());
        }
    }

    @Test void hostParentRotationAlsoRotatesChildTranslation() {
        double q = Math.sqrt(0.5);
        var ports = Map.of(id("parent"), new Port(new AppearanceTransform(new AppearanceVector(4, 8, 0), new AppearanceRotation(0, 0, q, q))),
                PORT, new Port(Optional.of(id("parent")), translated(0, 2, 0)));
        var host = new Host(VISUAL, ports, host().bindings(), Set.of(ELEMENT));
        var placed = resolve(host).placements().getFirst();
        assertVector(new AppearanceVector(2, 8, 0), placed.transform().apply(APPEARANCE.mount().position()));
    }

    @Test void longPortChainsUseIterativeTraversal() {
        Map<ResourceLocation, Port> ports = new HashMap<>();
        ResourceLocation previous = null;
        for (int i = 0; i < 10_000; i++) {
            ResourceLocation current = id("p_" + i);
            ports.put(current, new Port(Optional.ofNullable(previous), translated(0, 1, 0)));
            previous = current;
        }
        var host = new Host(VISUAL, ports, Map.of(SLOT, binding(previous, ELEMENT)), Set.of(ELEMENT));
        assertVector(new AppearanceVector(0, 10_000, 0), resolve(host).placements().getFirst().transform().apply(APPEARANCE.mount().position()));
    }

    @Test void duplicatePartIdsAreSeparateInstancesAtTheirOwnPorts() {
        var host = twoPortHost();
        var plan = AppearanceResolver.resolve(structure(Map.of(SLOT_B, PART, SLOT, PART)), catalog(host, Map.of(PART, APPEARANCE)), support());
        assertEquals(List.of(SLOT, SLOT_B), plan.placements().stream().map(Placement::slotId).toList());
        assertVector(new AppearanceVector(0, 7, 0), plan.placements().get(0).transform().apply(APPEARANCE.mount().position()));
        assertVector(new AppearanceVector(5, 7, 0), plan.placements().get(1).transform().apply(APPEARANCE.mount().position()));
    }

    @Test void unconfiguredPartPreservesItsOriginalWhileOtherPartCanReplace() {
        var plan = AppearanceResolver.resolve(structure(Map.of(SLOT, PART, SLOT_B, id("no_visual"))),
                catalog(twoPortHost(), Map.of(PART, APPEARANCE)), support());
        assertEquals(Set.of(ELEMENT), plan.hiddenOriginalElements());
        assertEquals(Status.UNCONFIGURED, plan.outcomes().get(1).status());
        assertEquals(1, plan.placements().size());
    }

    @Test void replacementConflictRejectsAllOverlappingGroupsNotJustLastWriter() {
        var host = new Host(VISUAL, host().ports(), Map.of(
                SLOT, binding(PORT, ELEMENT, ELEMENT_B), SLOT_B, binding(PORT, ELEMENT_B)), Set.of(ELEMENT, ELEMENT_B));
        var plan = AppearanceResolver.resolve(structure(Map.of(SLOT_B, PART, SLOT, PART)), catalog(host, Map.of(PART, APPEARANCE)), support());
        assertTrue(plan.usesOriginalAppearance());
        assertTrue(plan.hiddenOriginalElements().isEmpty());
        assertTrue(plan.outcomes().stream().allMatch(value -> value.reason() == Reason.REPLACEMENT_CONFLICT));
    }

    @Test void aNonReplacingFloatingElementIsAllowedWithoutAutomaticHiding() {
        var host = new Host(VISUAL, host().ports(), Map.of(SLOT, binding(PORT)), Set.of());
        var plan = AppearanceResolver.resolve(structure(Map.of(SLOT, PART)),
                catalog(host, Map.of(PART, APPEARANCE)),
                new AppearanceSupport(0, Set.of(ASSET), Set.of(), false));
        assertFalse(plan.usesOriginalAppearance());
        assertTrue(plan.hiddenOriginalElements().isEmpty());
    }

    @Test void overflowingPortChainAndFinalPlacementFallBack() {
        var huge = translated(Double.MAX_VALUE, 0, 0);
        var ports = Map.of(PORT, new Port(Optional.of(id("root")), huge), id("root"), new Port(huge));
        assertOriginal(resolve(new Host(VISUAL, ports, host().bindings(), Set.of(ELEMENT))), Reason.INVALID_TRANSFORM, Status.FALLBACK);
        var host = new Host(VISUAL, Map.of(PORT, new Port(huge)), host().bindings(), Set.of(ELEMENT));
        var part = new Part(APPEARANCE.id(), ASSET, translated(-Double.MAX_VALUE, 0, 0), Set.of());
        assertOriginal(AppearanceResolver.resolve(structure(Map.of(SLOT, PART)), catalog(host, Map.of(PART, part)), support()),
                Reason.INVALID_TRANSFORM, Status.FALLBACK);
    }

    @Test void repeatedCallsDoNotCacheInstallationOrMutateSavedData() {
        var original = structure(Map.of(SLOT, PART));
        var before = original.component(SLOT).orElseThrow().data();
        var catalog = catalog(host(), Map.of(PART, APPEARANCE));
        assertFalse(AppearanceResolver.resolve(original, catalog, support()).usesOriginalAppearance());
        var removed = new EquipmentStructure(HOST, HOST, original.slots(), Map.of());
        assertTrue(AppearanceResolver.resolve(removed, catalog, support()).usesOriginalAppearance());
        assertFalse(AppearanceResolver.resolve(original, catalog, support()).usesOriginalAppearance());
        assertEquals(before, original.component(SLOT).orElseThrow().data());
        assertEquals(1, original.components(SLOT).size());
    }

    @Test void callerCollectionsAndReturnedPlanCannotAlterDefinitions() {
        var bindings = new HashMap<>(host().bindings());
        var elements = new java.util.HashSet<>(Set.of(ELEMENT));
        var host = new Host(VISUAL, host().ports(), bindings, elements);
        var parts = new HashMap<>(Map.of(PART, APPEARANCE));
        var catalog = catalog(host, parts);
        bindings.clear();
        elements.clear();
        parts.clear();
        var plan = AppearanceResolver.resolve(structure(Map.of(SLOT, PART)), catalog, support());
        assertEquals(Set.of(ELEMENT), plan.hiddenOriginalElements());
        assertThrows(UnsupportedOperationException.class, () -> plan.placements().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.outcomes().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.hiddenOriginalElements().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.placements().getFirst().replacedElements().clear());
        assertThrows(UnsupportedOperationException.class, () -> catalog.hosts().clear());
    }

    @Test void malformedContractsRejectNullsAndNegativeGenerations() {
        assertThrows(IllegalArgumentException.class, () -> new AppearanceCatalog(-1, Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new AppearanceSupport(-1, Set.of(), Set.of(), true));
        assertThrows(NullPointerException.class, () -> new Part(id("p"), null, AppearanceTransform.IDENTITY, Set.of()));
        assertThrows(NullPointerException.class, () -> new Joint(null, ANCHOR_ONLY, AppearanceTransform.IDENTITY));
        assertThrows(NullPointerException.class, () -> new Port(null, AppearanceTransform.IDENTITY));
    }

    private static AppearancePlan resolve(Host host) {
        return AppearanceResolver.resolve(structure(Map.of(SLOT, PART)), catalog(host, Map.of(PART, APPEARANCE)), support());
    }

    private static Host host() {
        return new Host(VISUAL, Map.of(PORT, new Port(translated(0, 7, 0))), Map.of(SLOT, binding(PORT, ELEMENT)), Set.of(ELEMENT));
    }

    private static Host twoPortHost() {
        return new Host(VISUAL, Map.of(PORT, new Port(translated(0, 7, 0)), id("right"), new Port(translated(5, 7, 0))),
                Map.of(SLOT, binding(PORT, ELEMENT), SLOT_B, binding(id("right"), ELEMENT_B)), Set.of(ELEMENT, ELEMENT_B));
    }

    private static Binding binding(ResourceLocation port, ResourceLocation... elements) {
        return new Binding(port, new Joint(OWNER, ANCHOR_ONLY, AppearanceTransform.IDENTITY), Set.of(elements));
    }

    private static AppearanceCatalog catalog(Host host, Map<ResourceLocation, Part> parts) {
        return new AppearanceCatalog(0, Map.of(HOST, host), parts);
    }

    private static AppearanceSupport support() { return new AppearanceSupport(0, Set.of(ASSET), Set.of(), true); }

    private static EquipmentStructure structure(Map<ResourceLocation, ResourceLocation> installed) {
        var slots = new ArrayList<EquipmentSlotDefinition>();
        var parts = new HashMap<ResourceLocation, List<EquipmentComponentInstance>>();
        installed.forEach((slot, part) -> {
            slots.add(EquipmentSlotDefinition.of(slot, PART));
            var data = new net.minecraft.nbt.CompoundTag();
            data.putInt("author_seed", slot.hashCode());
            parts.put(slot, List.of(new EquipmentComponentInstance(part, PART, data)));
        });
        return new EquipmentStructure(HOST, HOST, slots, parts);
    }

    private static void assertOriginal(AppearancePlan plan, Reason reason, Status status) {
        assertTrue(plan.usesOriginalAppearance());
        assertTrue(plan.hiddenOriginalElements().isEmpty());
        assertEquals(reason, plan.outcomes().getFirst().reason());
        assertEquals(status, plan.outcomes().getFirst().status());
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("appearance_test", path); }
}
