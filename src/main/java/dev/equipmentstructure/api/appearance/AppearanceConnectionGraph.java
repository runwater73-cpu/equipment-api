package dev.equipmentstructure.api.appearance;

import dev.equipmentstructure.api.EquipmentStructure;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static dev.equipmentstructure.api.appearance.AppearanceDefinitions.*;
import static dev.equipmentstructure.api.appearance.AppearancePlan.Reason;

/**
 * One resolution's memoized frame graph. Ports and installed slots are distinct node kinds even
 * when their IDs match. Each node has at most one frame parent; a parent may have many children.
 * Iterative traversal and reverse failure propagation stay bounded by the visited graph size.
 */
final class AppearanceConnectionGraph {
    sealed interface Result permits Frame, Failure {}
    record Frame(AppearanceTransform transform) implements Result {}
    record Failure(Reason reason, ResourceLocation resource) implements Result, Recipe {}
    private sealed interface Recipe permits Link, Failure {}
    private record Link(Node parent, AppearanceTransform local) implements Recipe {}
    private enum Kind { PORT, SLOT }
    private record Node(Kind kind, ResourceLocation id) {}

    private final EquipmentStructure structure;
    private final Host host;
    private final Map<ResourceLocation, Part> readyParts;
    private final Map<Node, Result> resolved = new HashMap<>();
    private final Map<Node, List<Node>> children = new HashMap<>();

    AppearanceConnectionGraph(EquipmentStructure structure, Host host, Map<ResourceLocation, Part> readyParts) {
        this.structure = structure;
        this.host = host;
        this.readyParts = Map.copyOf(readyParts);
    }

    Result resolveSlot(ResourceLocation slot) {
        Node target = new Node(Kind.SLOT, slot);
        if (resolved.containsKey(target)) return resolved.get(target);
        List<Node> path = new ArrayList<>();
        List<Link> links = new ArrayList<>();
        Map<Node, Integer> visiting = new HashMap<>();
        Node cursor = target;
        Result result;
        while (true) {
            if (resolved.containsKey(cursor)) {
                result = resolved.get(cursor);
                break;
            }
            Integer cycleStart = visiting.get(cursor);
            if (cycleStart != null) {
                var cycle = path.subList(cycleStart, path.size());
                boolean componentCycle = cycle.stream().anyMatch(node -> node.kind == Kind.SLOT);
                ResourceLocation reference = cycle.stream().filter(node -> !componentCycle || node.kind == Kind.SLOT)
                        .map(Node::id).min(Comparator.comparing(ResourceLocation::toString)).orElseThrow();
                result = new Failure(componentCycle ? Reason.CYCLIC_CONNECTION : Reason.CYCLIC_PORT, reference);
                break;
            }
            Recipe recipe;
            try {
                recipe = describe(cursor);
            } catch (IllegalArgumentException overflow) {
                recipe = new Failure(Reason.INVALID_TRANSFORM, cursor.id);
            }
            if (recipe instanceof Failure failure) {
                result = failure;
                resolved.put(cursor, result);
                break;
            }
            Link link = (Link) recipe;
            visiting.put(cursor, path.size());
            path.add(cursor);
            links.add(link);
            if (link.parent == null) {
                result = new Frame(AppearanceTransform.IDENTITY);
                break;
            }
            children.computeIfAbsent(link.parent, ignored -> new ArrayList<>()).add(cursor);
            cursor = link.parent;
        }
        for (int index = path.size() - 1; index >= 0; index--) {
            Node node = path.get(index);
            if (result instanceof Frame frame) {
                try {
                    result = new Frame(frame.transform.compose(links.get(index).local));
                } catch (IllegalArgumentException overflow) {
                    result = new Failure(Reason.INVALID_TRANSFORM, node.id);
                }
            }
            resolved.put(node, result);
        }
        return result;
    }

    private Recipe describe(Node node) {
        if (node.kind == Kind.SLOT) {
            if (!structure.components().containsKey(node.id)) return new Failure(Reason.MISSING_COMPONENT_SLOT, node.id);
            Part part = readyParts.get(node.id);
            if (part == null) return new Failure(Reason.DEPENDENCY_UNAVAILABLE, node.id);
            Binding binding = host.bindings().get(node.id);
            AppearancePose pose = pose(node.id);
            AppearanceTransform inverseMount = part.mount().inverse();
            AppearanceTransform scaledMount = new AppearanceTransform(
                    scaled(inverseMount.position(), pose.scale()), inverseMount.rotation());
            AppearanceTransform local = binding.joint().offset()
                    .compose(pose.transform()).compose(scaledMount);
            // A missing port is an intentional free attachment. The component still
            // receives its own saved pose and mount, but does not require a host frame.
            return binding.port() == null ? new Link(null, local)
                    : new Link(new Node(Kind.PORT, binding.port()), local);
        }
        Port port = host.ports().get(node.id);
        if (port == null) return new Failure(Reason.MISSING_PORT, node.id);
        if (port.componentExit().isPresent()) {
            ComponentExit source = port.componentExit().get();
            if (!structure.components().containsKey(source.slot())) return new Failure(Reason.MISSING_COMPONENT_SLOT, source.slot());
            Part parentPart = readyParts.get(source.slot());
            if (parentPart == null) return new Failure(Reason.DEPENDENCY_UNAVAILABLE, source.slot());
            AppearanceTransform exit = parentPart.exits().get(source.exit());
            if (exit == null) return new Failure(Reason.MISSING_COMPONENT_EXIT, source.exit());
            AppearanceTransform scaledExit = new AppearanceTransform(
                    scaled(exit.position(), pose(source.slot()).scale()), exit.rotation());
            return new Link(new Node(Kind.SLOT, source.slot()), scaledExit.compose(port.localFrame()));
        }
        return new Link(port.parent().map(id -> new Node(Kind.PORT, id)).orElse(null), port.localFrame());
    }

    AppearancePose pose(ResourceLocation slot) {
        Binding binding = host.bindings().get(slot);
        return (binding == null ? new Binding((ResourceLocation) null,
                new Joint(host.id(), ANCHOR_ONLY, AppearanceTransform.IDENTITY), Set.of(),
                PlacementFreedom.FREE) : binding).constrain(AppearancePoseStorage.read(
                structure.component(slot).orElseThrow()).orElse(AppearancePose.IDENTITY));
    }

    private static AppearanceVector scaled(AppearanceVector p, double scale) {
        return new AppearanceVector(p.x() * scale, p.y() * scale, p.z() * scale);
    }

    /** Invalidate all descendants of rejected replacement groups, with no recursive traversal. */
    Map<ResourceLocation, ResourceLocation> dependentSlots(Set<ResourceLocation> rejectedSlots) {
        if (rejectedSlots.isEmpty()) return Map.of();
        Map<ResourceLocation, ResourceLocation> affected = new HashMap<>();
        var queue = new ArrayDeque<Node>();
        Set<Node> visited = new HashSet<>();
        Map<Node, ResourceLocation> causes = new HashMap<>();
        rejectedSlots.stream().sorted(Comparator.comparing(ResourceLocation::toString)).forEach(slot -> {
            Node node = new Node(Kind.SLOT, slot);
            queue.add(node);
            visited.add(node);
            causes.put(node, slot);
        });
        while (!queue.isEmpty()) {
            Node parent = queue.remove();
            for (Node child : children.getOrDefault(parent, List.of())) {
                if (!visited.add(child)) continue;
                ResourceLocation cause = causes.get(parent);
                causes.put(child, cause);
                if (child.kind == Kind.SLOT) affected.put(child.id, cause);
                queue.add(child);
            }
        }
        return affected;
    }
}
