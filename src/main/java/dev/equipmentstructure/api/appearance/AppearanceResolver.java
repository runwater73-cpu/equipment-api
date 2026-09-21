package dev.equipmentstructure.api.appearance;

import dev.equipmentstructure.api.EquipmentStructure;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static dev.equipmentstructure.api.appearance.AppearanceDefinitions.*;
import static dev.equipmentstructure.api.appearance.AppearancePlan.*;

/**
 * Pure, deterministic resolver for static ports and installed-part exit connections. No registry mutation,
 * resource lookup, logging, caching between stacks, rendering, or gameplay operation occurs here.
 */
public final class AppearanceResolver {
    private static final Comparator<ResourceLocation> IDS = Comparator.comparing(ResourceLocation::toString);

    private AppearanceResolver() {}

    public static AppearancePlan resolve(EquipmentStructure structure, AppearanceCatalog catalog, AppearanceSupport support) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(support, "support");
        Host host = catalog.hosts().get(structure.hostId());
        List<Candidate> candidates = new ArrayList<>();
        var slots = structure.components().keySet().stream().sorted(IDS).toList();
        Map<ResourceLocation, Candidate> rejected = new HashMap<>();
        Map<ResourceLocation, Part> readyParts = new HashMap<>();
        for (ResourceLocation slot : slots) {
            ResourceLocation component = structure.component(slot).orElseThrow().id();
            Part part = catalog.components().get(component);
            Candidate failure = validateSlot(slot, component, host, part, catalog.generation(), support);
            if (failure != null) rejected.put(slot, failure);
            else readyParts.put(slot, part);
        }
        var graph = new AppearanceConnectionGraph(structure, host, readyParts);
        for (ResourceLocation slot : slots) {
            Candidate failure = rejected.get(slot);
            if (failure != null) {
                candidates.add(failure);
                continue;
            }
            ResourceLocation component = structure.component(slot).orElseThrow().id();
            Part part = readyParts.get(slot);
            var frame = graph.resolveSlot(slot);
            if (frame instanceof AppearanceConnectionGraph.Failure invalid) {
                candidates.add(failed(slot, component, part, Status.FALLBACK, invalid.reason(), invalid.resource()));
            } else {
                Binding binding = host.bindings().get(slot);
                AppearanceTransform transform = ((AppearanceConnectionGraph.Frame) frame).transform();
                double scale = graph.pose(slot).scale();
                var motion = part.motion().map(value -> {
                    var mount = part.mount().inverse();
                    var scaledMount = new AppearanceTransform(new AppearanceVector(mount.position().x() * scale,
                            mount.position().y() * scale, mount.position().z() * scale), mount.rotation());
                    var anchor = transform.compose(scaledMount.inverse()).compose(graph.pose(slot).transform().inverse());
                    // Fixed author placement ignores saved player motion overrides just like saved poses.
                    // Keep the data so changing the resource back to adjustable restores the player's edit.
                    var settings = binding.placementFreedom() == PlacementFreedom.FIXED ? AppearanceMotionSettings.DEFAULT
                            : AppearanceMotionSettings.read(structure.component(slot).orElseThrow());
                    return value.resolve(anchor, settings);
                });
                var placement = new Placement(slot, component, part.id(), part.asset(), binding.joint().owner(),
                        transform, binding.replaces(), part.renderProfile(), scale,
                        AppearanceVisibilityStorage.componentVisible(structure.component(slot).orElseThrow()),
                        binding.followsHostAnimation() && part.followsHostAnimation(), binding.bounds(),
                        motion);
                candidates.add(new Candidate(new Outcome(slot, component, Optional.of(part.id()), Status.READY,
                        Reason.NONE, Optional.empty()), placement));
            }
        }

        // Treat each accepted part as an indivisible replacement group. Shared exclusive targets
        // invalidate every involved group, independent of input map order, without hiding anything.
        Map<ResourceLocation, List<Candidate>> claims = new HashMap<>();
        for (Candidate candidate : candidates) {
            if (candidate.placement != null && candidate.placement.visible()) {
                for (ResourceLocation element : candidate.placement.replacedElements()) {
                    claims.computeIfAbsent(element, ignored -> new ArrayList<>()).add(candidate);
                }
            }
        }
        Map<ResourceLocation, ResourceLocation> conflicts = new HashMap<>();
        claims.entrySet().stream().sorted(Map.Entry.comparingByKey(IDS)).filter(entry -> entry.getValue().size() > 1)
                .forEach(entry -> entry.getValue().forEach(candidate -> conflicts.putIfAbsent(candidate.outcome.slotId(), entry.getKey())));
        // A child must not float at an exit of a parent rejected during replacement arbitration.
        Map<ResourceLocation, ResourceLocation> unavailableParents = graph.dependentSlots(conflicts.keySet());

        List<Placement> placements = new ArrayList<>();
        List<Outcome> outcomes = new ArrayList<>();
        for (Candidate candidate : candidates) {
            ResourceLocation conflict = conflicts.get(candidate.outcome.slotId());
            if (conflict != null) {
                Outcome previous = candidate.outcome;
                outcomes.add(new Outcome(previous.slotId(), previous.componentId(), previous.appearanceId(),
                        Status.FALLBACK, Reason.REPLACEMENT_CONFLICT, Optional.of(conflict)));
            } else if (candidate.placement != null && unavailableParents.containsKey(candidate.outcome.slotId())) {
                Outcome previous = candidate.outcome;
                outcomes.add(new Outcome(previous.slotId(), previous.componentId(), previous.appearanceId(),
                        Status.FALLBACK, Reason.DEPENDENCY_UNAVAILABLE, Optional.of(unavailableParents.get(previous.slotId()))));
            } else {
                outcomes.add(candidate.outcome);
                if (candidate.placement != null) placements.add(candidate.placement);
            }
        }
        return new AppearancePlan(catalog.generation(), structure.hostId(), Optional.ofNullable(host).map(Host::id), placements, outcomes);
    }

    /** Null means ready for frame resolution; failures do not make replacement claims. */
    private static Candidate validateSlot(ResourceLocation slot, ResourceLocation component, Host host, Part part,
                                          long generation, AppearanceSupport support) {
        if (host == null) return failed(slot, component, part, Status.UNCONFIGURED, Reason.HOST_UNCONFIGURED, null);
        Binding binding = host.bindings().get(slot);
        if (binding == null) return failed(slot, component, part, Status.UNCONFIGURED, Reason.BINDING_UNCONFIGURED, null);
        // Missing appearance is normal. Do not inspect its binding's replacement requests or infer an icon.
        if (part == null) return failed(slot, component, null, Status.UNCONFIGURED, Reason.COMPONENT_UNCONFIGURED, null);
        if (generation != support.generation()) return failed(slot, component, part, Status.FALLBACK, Reason.STALE_RESOURCES, null);
        if (!support.canReplaceElements() && !binding.replaces().isEmpty()) {
            return failed(slot, component, part, Status.FALLBACK, Reason.UNSUPPORTED_ADAPTER, null);
        }
        if (!support.preparedAssets().contains(part.asset())) return failed(slot, component, part, Status.FALLBACK, Reason.ASSET_NOT_READY, part.asset());
        var missingCapability = part.requiredCapabilities().stream().filter(id -> !support.capabilities().contains(id)).sorted(IDS).findFirst();
        if (missingCapability.isPresent()) return failed(slot, component, part, Status.FALLBACK, Reason.UNSUPPORTED_CAPABILITY, missingCapability.get());
        if (!binding.joint().strategy().equals(ANCHOR_ONLY)) {
            return failed(slot, component, part, Status.FALLBACK, Reason.UNSUPPORTED_JOINT, binding.joint().strategy());
        }
        var undeclared = binding.replaces().stream().filter(id -> !host.replaceableElements().contains(id)).sorted(IDS).findFirst();
        if (undeclared.isPresent()) return failed(slot, component, part, Status.FALLBACK, Reason.UNDECLARED_REPLACEMENT, undeclared.get());
        return null;
    }

    private static Candidate failed(ResourceLocation slot, ResourceLocation component, Part part,
                                     Status status, Reason reason, ResourceLocation resource) {
        return new Candidate(new Outcome(slot, component, Optional.ofNullable(part).map(Part::id),
                status, reason, Optional.ofNullable(resource)), null);
    }

    private record Candidate(Outcome outcome, Placement placement) {}
}
