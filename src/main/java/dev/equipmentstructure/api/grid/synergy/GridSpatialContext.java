package dev.equipmentstructure.api.grid.synergy;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.grid.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.stream.Collectors;

/** Immutable spatial snapshot. Query actual occupied cells, never icon bounds or 3D poses. */
public final class GridSpatialContext {
    public static final ResourceLocation BODY = ResourceLocation.parse("equipment_structure_api:body");
    public record Node(ResourceLocation id, boolean body, Optional<EquipmentComponentInstance> component,
            Optional<EquipmentSlotDefinition> slot, GridLayout.Part part, Set<GridCell> cells) {
        public Node { cells = Set.copyOf(cells); }
        public Node(ResourceLocation id, boolean body, Optional<EquipmentComponentInstance> component,
                Optional<EquipmentSlotDefinition> slot, GridLayout.Part part) {
            this(id, body, component, slot, part, part == null ? Set.of() : part.cells());
        }
    }
    private final EquipmentStructure structure;
    private final GridLayout layout;
    private final Map<ResourceLocation, Node> nodes;
    public GridSpatialContext(EquipmentStructure structure, GridLayout layout) {
        this.structure = structure; this.layout = layout;
        var result = new LinkedHashMap<ResourceLocation, Node>();
        result.put(BODY, new Node(BODY, true, Optional.empty(), Optional.empty(),
                new GridLayout.Part(GridFootprint.freelyRotating(layout.board().body()), layout.board().bodyPlacement())));
        layout.parts().forEach((id, part) -> {
            if (id.equals(BODY)) throw new IllegalArgumentException("Reserved spatial body identity used as a slot");
            result.put(id, new Node(id, false, structure.component(id), structure.slot(id), part));
        });
        nodes = Collections.unmodifiableMap(result);
    }
    public EquipmentStructure structure() { return structure; }
    public GridLayout layout() { return layout; }
    public Map<ResourceLocation, Node> nodes() { return nodes; }
    public Node require(ResourceLocation id) { return Objects.requireNonNull(nodes.get(id), "Missing spatial node " + id); }
    public List<Node> select(GridSelector selector) { return nodes.values().stream().filter(selector::matches).toList(); }
    public static int distance(Node a, Node b, boolean diagonal) {
        int result = Integer.MAX_VALUE;
        for (var x : a.cells()) for (var y : b.cells()) {
            int dx = Math.abs(x.x() - y.x()), dy = Math.abs(x.y() - y.y());
            result = Math.min(result, diagonal ? Math.max(dx, dy) : dx + dy);
        }
        return result;
    }
    public static int contact(Node a, Node b) {
        int result = 0;
        for (var cell : a.cells()) {
            if (b.cells().contains(new GridCell(cell.x() - 1, cell.y()))) result++;
            if (b.cells().contains(new GridCell(cell.x() + 1, cell.y()))) result++;
            if (b.cells().contains(new GridCell(cell.x(), cell.y() - 1))) result++;
            if (b.cells().contains(new GridCell(cell.x(), cell.y() + 1))) result++;
        }
        return result;
    }
    public static boolean overlaps(Node a, Node b) { return !Collections.disjoint(a.cells(), b.cells()); }
    public List<Node> neighbors(ResourceLocation source, GridSelector selector, boolean diagonal) {
        var origin = require(source);
        return select(selector).stream().filter(n -> !n.id().equals(source) && !overlaps(origin, n)
                && (diagonal ? distance(origin, n, true) == 1 : contact(origin, n) > 0)).toList();
    }
    public Set<GridCell> occupied() {
        var result = new HashSet<>(layout.board().blockedBodyCells());
        layout.parts().values().forEach(p -> result.addAll(p.reservedCells())); return Set.copyOf(result);
    }
    /** Signed authored offset from the source's unrotated top-left, optionally in its local frame. */
    public GridCell relative(ResourceLocation source, GridCell offset, boolean local) {
        var part = require(source).part();
        int x = offset.x(), y = offset.y(), w = part.footprint().shape().width(), h = part.footprint().shape().height();
        if (local) for (int n = 0; n < part.placement().rotation().ordinal(); n++) {
            int next = h - 1 - y; y = x; x = next; int oldW = w; w = h; h = oldW;
        }
        return part.placement().translate(new GridCell(x, y));
    }
    public Set<ResourceLocation> connected(ResourceLocation source, GridSelector through) {
        var candidates = new ArrayList<>(select(through));
        var seen = new LinkedHashSet<ResourceLocation>(); seen.add(source);
        var queue = new ArrayDeque<Node>(); queue.add(require(source));
        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            for (var next : candidates) if (!seen.contains(next.id()) && !overlaps(current, next) && contact(current, next) > 0) {
                seen.add(next.id()); queue.addLast(next);
            }
        }
        return Set.copyOf(seen);
    }
}
