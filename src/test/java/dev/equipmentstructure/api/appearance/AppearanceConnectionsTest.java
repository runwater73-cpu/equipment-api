package dev.equipmentstructure.api.appearance;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.client.appearance.AppearancePlanCache;
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

class AppearanceConnectionsTest {
    private static final ResourceLocation HOST = id("host"), ROOT = id("z_root"), CHILD = id("a_child"), BRANCH = id("b_branch");
    private static final ResourceLocation SHORT = id("short"), LONG = id("long"), TIP = id("tip"), ASSET = id("asset");
    private static final ResourceLocation EXIT = id("tip_exit"), ROOT_PORT = id("root_port"), CHILD_PORT = id("child_port");

    @Test void changingParentLengthMovesChildWithoutChangingLogicalSlotsOrData() {
        var catalog = catalog(host(), parts(), 1);
        var shortEquipment = equipment(Map.of(ROOT, SHORT, CHILD, TIP));
        var longEquipment = shortEquipment.withComponents(ROOT, List.of(new EquipmentComponentInstance(LONG, TIP)));
        var first = AppearanceResolver.resolve(shortEquipment, catalog, support(1));
        var second = AppearanceResolver.resolve(longEquipment, catalog, support(1));
        assertVector(new AppearanceVector(0, 6, 0), placement(first, CHILD).transform().position());
        assertVector(new AppearanceVector(0, 11, 0), placement(second, CHILD).transform().position());
        assertEquals(shortEquipment.slots(), longEquipment.slots());
        assertEquals(SHORT, shortEquipment.component(ROOT).orElseThrow().id());
        assertEquals(TIP, shortEquipment.component(CHILD).orElseThrow().id());
    }

    @Test void mountExitRotationAndJointComposeInModelLocalOrder() {
        var turn = new AppearanceRotation(0, 0, 1, 1);
        var parentMount = translated(2, 1, 0);
        var parentFrame = new AppearanceTransform(new AppearanceVector(3, 4, 0), turn);
        var exitFrame = new AppearanceTransform(new AppearanceVector(2, 7, 1), turn);
        var gap = translated(1, 2, 0);
        var joint = new AppearanceTransform(new AppearanceVector(0, 3, 0), turn);
        var childMount = translated(-1, 2, 1);
        var host = new Host(HOST, Map.of(ROOT_PORT, new Port(parentFrame),
                CHILD_PORT, Port.onComponent(ROOT, EXIT, gap)), Map.of(ROOT, binding(ROOT_PORT), CHILD,
                new Binding(CHILD_PORT, new Joint(id("owner"), ANCHOR_ONLY, joint), Set.of())), Set.of());
        var parts = Map.of(SHORT, new Part(SHORT, ASSET, parentMount, Set.of(), RenderProfile.DEFAULT, Map.of(EXIT, exitFrame)),
                TIP, new Part(TIP, ASSET, childMount, Set.of()));
        var plan = resolve(host, Map.of(ROOT, SHORT, CHILD, TIP), parts);
        var expected = parentFrame.compose(parentMount.inverse()).compose(exitFrame).compose(gap).compose(joint);
        for (var point : List.of(AppearanceVector.ZERO, new AppearanceVector(1, 0, 0), new AppearanceVector(0, 1, 0))) {
            assertVector(expected.apply(point), placement(plan, CHILD).transform().apply(childMount.apply(point)));
        }
    }

    @Test void supportsBranchesMultipleExitsAndSamePartInstances() {
        var host = new Host(HOST, Map.of(ROOT_PORT, new Port(translated(0, 0, 0)),
                CHILD_PORT, Port.onComponent(ROOT, EXIT, AppearanceTransform.IDENTITY),
                id("side_port"), Port.onComponent(ROOT, id("side"), AppearanceTransform.IDENTITY)),
                Map.of(ROOT, binding(ROOT_PORT), CHILD, binding(CHILD_PORT), BRANCH, binding(id("side_port"))), Set.of());
        var parent = new Part(SHORT, ASSET, AppearanceTransform.IDENTITY, Set.of(), RenderProfile.DEFAULT,
                Map.of(EXIT, translated(0, 4, 0), id("side"), translated(3, 2, 0)));
        var plan = resolve(host, Map.of(ROOT, SHORT, CHILD, TIP, BRANCH, TIP), Map.of(SHORT, parent, TIP, parts().get(TIP)));
        assertEquals(3, plan.placements().size());
        assertVector(new AppearanceVector(0, 4, 0), placement(plan, CHILD).transform().position());
        assertVector(new AppearanceVector(3, 2, 0), placement(plan, BRANCH).transform().position());
    }

    @Test void missingOrEmptyParentOnlyRejectsItsBranch() {
        var plan = resolve(host(), Map.of(CHILD, TIP), parts());
        assertTrue(plan.usesOriginalAppearance());
        assertEquals(Reason.MISSING_COMPONENT_SLOT, outcome(plan, CHILD).reason());
        assertEquals(Optional.of(ROOT), outcome(plan, CHILD).resource());
        var full = equipment(Map.of(ROOT, SHORT, CHILD, TIP));
        var emptied = full.withComponents(ROOT, List.of());
        var emptyPlan = AppearanceResolver.resolve(emptied, catalog(host(), parts(), 1), support(1));
        assertEquals(plan.outcomes(), emptyPlan.outcomes());
    }

    @Test void absentParentAppearanceOrAssetsDoNotCreateFloatingChildren() {
        for (boolean missingAppearance : List.of(true, false)) {
            var parts = new HashMap<>(parts());
            if (missingAppearance) parts.remove(SHORT);
            else parts.put(SHORT, new Part(SHORT, id("missing_asset"), AppearanceTransform.IDENTITY,
                    Set.of(), RenderProfile.DEFAULT, Map.of(EXIT, translated(0, 4, 0))));
            var plan = resolve(host(), Map.of(ROOT, SHORT, CHILD, TIP), parts);
            assertTrue(plan.usesOriginalAppearance());
            assertTrue(plan.hiddenOriginalElements().isEmpty());
            assertEquals(Reason.DEPENDENCY_UNAVAILABLE, outcome(plan, CHILD).reason());
        }
    }

    @Test void missingExitPreservesParentAndReportsExactExit() {
        var parts = new HashMap<>(parts());
        parts.put(SHORT, new Part(SHORT, ASSET, AppearanceTransform.IDENTITY, Set.of()));
        var plan = resolve(host(), Map.of(ROOT, SHORT, CHILD, TIP), parts);
        assertEquals(List.of(ROOT), plan.placements().stream().map(Placement::slotId).toList());
        assertEquals(Reason.MISSING_COMPONENT_EXIT, outcome(plan, CHILD).reason());
        assertEquals(Optional.of(EXIT), outcome(plan, CHILD).resource());
    }

    @Test void mixedCyclesAndSelfConnectionsFallBackWithoutAffectingIndependentRoot() {
        for (boolean self : List.of(true, false)) {
            var host = new Host(HOST, Map.of(ROOT_PORT, new Port(AppearanceTransform.IDENTITY),
                    CHILD_PORT, Port.onComponent(self ? CHILD : BRANCH, EXIT, AppearanceTransform.IDENTITY),
                    id("branch_port"), Port.onComponent(CHILD, EXIT, AppearanceTransform.IDENTITY)),
                    Map.of(ROOT, binding(ROOT_PORT), CHILD, binding(CHILD_PORT), BRANCH, binding(id("branch_port"))), Set.of());
            var plan = resolve(host, Map.of(ROOT, SHORT, CHILD, SHORT, BRANCH, SHORT), parts());
            assertEquals(List.of(ROOT), plan.placements().stream().map(Placement::slotId).toList());
            assertEquals(Reason.CYCLIC_CONNECTION, outcome(plan, CHILD).reason());
            assertEquals(Reason.CYCLIC_CONNECTION, outcome(plan, BRANCH).reason());
            assertEquals(Optional.of(CHILD), outcome(plan, CHILD).resource());
        }
    }

    @Test void staticChildPortsCanFollowDynamicExitAndPortIdMayEqualSlotId() {
        var host = new Host(HOST, Map.of(ROOT, new Port(translated(0, 2, 0)),
                CHILD_PORT, Port.onComponent(ROOT, EXIT, translated(0, 1, 0)),
                id("static_child"), new Port(Optional.of(CHILD_PORT), translated(1, 0, 0))),
                Map.of(ROOT, binding(ROOT), CHILD, binding(id("static_child"))), Set.of());
        var plan = resolve(host, Map.of(ROOT, SHORT, CHILD, TIP), parts());
        assertVector(new AppearanceVector(1, 7, 0), placement(plan, CHILD).transform().position());
    }

    @Test void replacementConflictInvalidatesAllDependentPlacementsAndTheirHideRequests() {
        var element = id("exclusive");
        var childElement = id("child_original");
        var host = new Host(HOST, host().ports(), Map.of(ROOT, binding(ROOT_PORT, element),
                BRANCH, binding(ROOT_PORT, element), CHILD, binding(CHILD_PORT, childElement)), Set.of(element, childElement));
        var plan = resolve(host, Map.of(ROOT, SHORT, CHILD, TIP, BRANCH, SHORT), parts());
        assertTrue(plan.usesOriginalAppearance());
        assertTrue(plan.hiddenOriginalElements().isEmpty());
        assertEquals(Reason.REPLACEMENT_CONFLICT, outcome(plan, ROOT).reason());
        assertEquals(Reason.DEPENDENCY_UNAVAILABLE, outcome(plan, CHILD).reason());
        assertEquals(Optional.of(ROOT), outcome(plan, CHILD).resource());
    }

    @Test void deepComponentChainsUseIterativeTraversalAndStableSlotOrder() {
        int count = 10_000;
        var ports = new HashMap<ResourceLocation, Port>();
        var bindings = new HashMap<ResourceLocation, Binding>();
        var installed = new HashMap<ResourceLocation, ResourceLocation>();
        ResourceLocation previous = null;
        for (int i = 0; i < count; i++) {
            // Lexical ordering resolves the deepest dependency first.
            ResourceLocation slot = id(String.format(java.util.Locale.ROOT, "segment_%05d", count - i));
            ports.put(slot, previous == null ? new Port(AppearanceTransform.IDENTITY)
                    : Port.onComponent(previous, EXIT, AppearanceTransform.IDENTITY));
            bindings.put(slot, binding(slot));
            installed.put(slot, SHORT);
            previous = slot;
        }
        var host = new Host(HOST, ports, bindings, Set.of());
        var plan = resolve(host, installed, parts());
        assertEquals(count, plan.placements().size());
        assertVector(new AppearanceVector(0, 4.0 * (count - 1), 0), placement(plan, previous).transform().position());
        var reordered = new java.util.LinkedHashMap<ResourceLocation, ResourceLocation>();
        installed.keySet().stream().sorted(java.util.Comparator.comparing(ResourceLocation::toString).reversed())
                .forEach(slot -> reordered.put(slot, SHORT));
        var again = resolve(host, reordered, parts());
        assertEquals(plan.placements(), again.placements());
        assertEquals(plan.outcomes(), again.outcomes());
    }

    @Test void overflowInExitChainFallsBackWithFiniteIndependentParent() {
        var host = new Host(HOST, Map.of(ROOT_PORT, new Port(translated(Double.MAX_VALUE, 0, 0)),
                CHILD_PORT, Port.onComponent(ROOT, EXIT, AppearanceTransform.IDENTITY)), host().bindings(), Set.of());
        var parent = new Part(SHORT, ASSET, AppearanceTransform.IDENTITY, Set.of(), RenderProfile.DEFAULT,
                Map.of(EXIT, translated(Double.MAX_VALUE, 0, 0)));
        var plan = resolve(host, Map.of(ROOT, SHORT, CHILD, TIP), Map.of(SHORT, parent, TIP, parts().get(TIP)));
        assertEquals(Reason.INVALID_TRANSFORM, outcome(plan, CHILD).reason());
        assertEquals(1, plan.placements().size());
    }

    @Test void childFailureDoesNotRejectReadyParentAndUnusedBrokenPortsAreInert() {
        var ports = new HashMap<>(host().ports());
        ports.put(id("unused"), Port.onComponent(id("unknown"), EXIT, AppearanceTransform.IDENTITY));
        var parts = new HashMap<>(parts());
        parts.put(TIP, new Part(TIP, id("absent"), AppearanceTransform.IDENTITY, Set.of()));
        var plan = resolve(new Host(HOST, ports, host().bindings(), Set.of()), Map.of(ROOT, SHORT, CHILD, TIP), parts);
        assertEquals(List.of(ROOT), plan.placements().stream().map(Placement::slotId).toList());
        assertEquals(Reason.ASSET_NOT_READY, outcome(plan, CHILD).reason());
    }

    @Test void exitDefinitionsAreDefensivelyCopiedAndAmbiguousParentIsRejected() {
        var exits = new HashMap<>(Map.of(EXIT, translated(0, 4, 0)));
        var part = new Part(SHORT, ASSET, AppearanceTransform.IDENTITY, Set.of(), RenderProfile.DEFAULT, exits);
        exits.clear();
        assertEquals(1, part.exits().size());
        assertThrows(UnsupportedOperationException.class, () -> part.exits().clear());
        assertThrows(IllegalArgumentException.class, () -> new Port(Optional.of(ROOT_PORT), AppearanceTransform.IDENTITY,
                Optional.of(new ComponentExit(ROOT, EXIT))));
    }

    @Test void independentlyNamedAuthorsCanShareAnExitWithoutSharingPartOrSlotIds() {
        var grip = ResourceLocation.fromNamespaceAndPath("author_a", "grip");
        var head = ResourceLocation.fromNamespaceAndPath("author_b", "curved_head");
        var sharedExit = ResourceLocation.fromNamespaceAndPath("joint_contract", "forward");
        var host = new Host(HOST, Map.of(ROOT_PORT, new Port(AppearanceTransform.IDENTITY),
                CHILD_PORT, Port.onComponent(ROOT, sharedExit, AppearanceTransform.IDENTITY)), host().bindings(), Set.of());
        var parts = Map.of(grip, new Part(grip, ASSET, AppearanceTransform.IDENTITY, Set.of(), RenderProfile.DEFAULT,
                Map.of(sharedExit, translated(2, 13, 1))), head, new Part(head, ASSET, translated(1, 1, 0), Set.of()));
        var plan = resolve(host, Map.of(ROOT, grip, CHILD, head), parts);
        assertEquals(2, plan.placements().size());
        assertVector(new AppearanceVector(2, 13, 1), placement(plan, CHILD).transform().apply(parts.get(head).mount().position()));
    }

    @Test void cacheSeparatesInstalledPartsSupportAndResourceGeneration() {
        var cache = new AppearancePlanCache(4);
        var equipment = equipment(Map.of(ROOT, SHORT, CHILD, TIP));
        var catalog = catalog(host(), parts(), 1);
        var first = cache.getOrCompute(equipment, support(1), () -> AppearanceResolver.resolve(equipment, catalog, support(1)));
        assertSame(first, cache.getOrCompute(equipment, support(1), () -> { throw new AssertionError("cache miss"); }));
        var reloadedParts = new HashMap<>(parts());
        reloadedParts.put(SHORT, part(SHORT, 12));
        var newCatalog = catalog(host(), reloadedParts, 2);
        var reloaded = cache.getOrCompute(equipment, support(2), () -> AppearanceResolver.resolve(equipment, newCatalog, support(2)));
        assertVector(new AppearanceVector(0, 14, 0), placement(reloaded, CHILD).transform().position());
        var removed = equipment.withComponents(ROOT, List.of());
        var removedPlan = cache.getOrCompute(removed, support(2), () -> AppearanceResolver.resolve(removed, newCatalog, support(2)));
        assertTrue(removedPlan.usesOriginalAppearance());
        var stale = AppearanceResolver.resolve(equipment, newCatalog, support(1));
        assertTrue(stale.usesOriginalAppearance());
        assertTrue(stale.outcomes().stream().allMatch(outcome -> outcome.reason() == Reason.STALE_RESOURCES));
    }

    private static Host host() {
        return new Host(HOST, Map.of(ROOT_PORT, new Port(translated(0, 2, 0)),
                CHILD_PORT, Port.onComponent(ROOT, EXIT, AppearanceTransform.IDENTITY)),
                Map.of(ROOT, binding(ROOT_PORT), CHILD, binding(CHILD_PORT)), Set.of());
    }

    @Test void playerPoseMovesRotatesAndScalesParentExitWithoutScalingChildGeometry() {
        var original = equipment(Map.of(ROOT, SHORT, CHILD, TIP));
        var pose = new AppearancePose(3, 0, 0, new AppearanceRotation(0, 0, 1, 1), 2);
        var changed = original.withComponent(ROOT, AppearancePoseStorage.with(original.component(ROOT).orElseThrow(), pose));
        var plan = AppearanceResolver.resolve(changed, catalog(host(), parts(), 1), support(1));
        assertVector(new AppearanceVector(3, 2, 0), placement(plan, ROOT).transform().position());
        assertVector(new AppearanceVector(-5, 2, 0), placement(plan, CHILD).transform().position());
        assertEquals(2, placement(plan, ROOT).scale());
        assertEquals(1, placement(plan, CHILD).scale());
        assertEquals(original.component(CHILD), changed.component(CHILD));
    }

    @Test void nonzeroMountRemainsAtAnchorWhenScaledAndRotated() {
        var original = equipment(Map.of(ROOT, SHORT));
        var mount = new AppearanceTransform(new AppearanceVector(2, 1, 3), new AppearanceRotation(0, 1, 0, 1));
        var part = new Part(SHORT, ASSET, mount, Set.of());
        var pose = new AppearancePose(3, -1, 2, new AppearanceRotation(0, 0, 1, 1), 1.5);
        var changed = original.withComponent(ROOT, AppearancePoseStorage.with(original.component(ROOT).orElseThrow(), pose));
        var placed = placement(AppearanceResolver.resolve(changed, catalog(host(), Map.of(SHORT, part), 1), support(1)), ROOT);
        var p = mount.position();
        var actualMount = placed.transform().apply(new AppearanceVector(p.x() * placed.scale(), p.y() * placed.scale(), p.z() * placed.scale()));
        assertVector(new AppearanceVector(3, 1, 2), actualMount);
    }

    private static Map<ResourceLocation, Part> parts() {
        return Map.of(SHORT, part(SHORT, 4), LONG, part(LONG, 9), TIP, new Part(TIP, ASSET, AppearanceTransform.IDENTITY, Set.of()));
    }

    private static Part part(ResourceLocation id, double length) {
        return new Part(id, ASSET, AppearanceTransform.IDENTITY, Set.of(), RenderProfile.DEFAULT, Map.of(EXIT, translated(0, length, 0)));
    }

    private static Binding binding(ResourceLocation port, ResourceLocation... elements) {
        return new Binding(port, new Joint(id("owner"), ANCHOR_ONLY, AppearanceTransform.IDENTITY), Set.of(elements));
    }

    private static AppearanceCatalog catalog(Host host, Map<ResourceLocation, Part> parts, long generation) {
        return new AppearanceCatalog(generation, Map.of(HOST, host), parts);
    }

    private static AppearanceSupport support(long generation) { return new AppearanceSupport(generation, Set.of(ASSET), Set.of(), true); }

    private static AppearancePlan resolve(Host host, Map<ResourceLocation, ResourceLocation> installed, Map<ResourceLocation, Part> parts) {
        return AppearanceResolver.resolve(equipment(installed), catalog(host, parts, 1), support(1));
    }

    private static EquipmentStructure equipment(Map<ResourceLocation, ResourceLocation> installed) {
        var slots = new ArrayList<EquipmentSlotDefinition>();
        var parts = new HashMap<ResourceLocation, List<EquipmentComponentInstance>>();
        installed.forEach((slot, part) -> {
            slots.add(EquipmentSlotDefinition.of(slot, TIP));
            parts.put(slot, List.of(new EquipmentComponentInstance(part, TIP)));
        });
        return new EquipmentStructure(HOST, HOST, slots, parts);
    }

    private static Placement placement(AppearancePlan plan, ResourceLocation slot) {
        return plan.placements().stream().filter(placement -> placement.slotId().equals(slot)).findFirst().orElseThrow();
    }

    private static Outcome outcome(AppearancePlan plan, ResourceLocation slot) {
        return plan.outcomes().stream().filter(outcome -> outcome.slotId().equals(slot)).findFirst().orElseThrow();
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("connections", path); }
}
