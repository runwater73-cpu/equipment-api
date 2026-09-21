package dev.equipmentstructure.api.grid;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentStructure;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Pure proposals; callers commit the returned structure only after item and event validation. */
public final class GridTransactions {
    private GridTransactions() {}
    private static final ThreadLocal<dev.equipmentstructure.api.internal.SnapshotCache<EquipmentStructure, GridDefinitions, View>> VIEWS =
            ThreadLocal.withInitial(() -> new dev.equipmentstructure.api.internal.SnapshotCache<>(16));
    public enum Status {
        VALID, DISABLED, MISSING_DEFINITION, STALE_DEFINITIONS, INVALID_LAYOUT,
        NO_SPACE, PLACEMENT_REJECTED, UNKNOWN_SLOT, EMPTY_SLOT
    }
    public record View(Status status, Optional<GridLayout> layout) {
        public boolean allowed() { return status == Status.VALID || status == Status.DISABLED; }
    }
    public record Plan(Status status, EquipmentStructure structure) {
        public boolean allowed() { return status == Status.VALID || status == Status.DISABLED; }
    }

    public static View resolve(EquipmentStructure structure, GridDefinitions definitions) {
        var cache = VIEWS.get();
        var cached = cache.get(structure, definitions);
        return cached != null ? cached : cache.put(structure, definitions, resolve(structure, definitions, false));
    }

    private static View resolve(EquipmentStructure structure, GridDefinitions definitions, boolean refresh) {
        GridBoard board = definitions.hosts().get(structure.hostId());
        if (board == null) return view(structure.grid().isEmpty() ? Status.DISABLED : Status.MISSING_DEFINITION);
        if (structure.components().values().stream().anyMatch(parts -> !definitions.components().containsKey(parts.getFirst().id())))
            return view(Status.MISSING_DEFINITION);
        var state = structure.grid();
        if (state.isEmpty() && !structure.components().isEmpty()) return view(Status.INVALID_LAYOUT);
        if (state.isPresent() && !state.get().placements().keySet().equals(structure.components().keySet()))
            return view(Status.INVALID_LAYOUT);
        if (!refresh && state.isPresent() && !state.get().definitions().equals(definitions.fingerprint(structure)))
            return view(Status.STALE_DEFINITIONS);
        GridLayout layout = GridLayout.empty(board);
        for (ResourceLocation slot : structure.components().keySet().stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList()) {
            var footprint = definitions.components().get(structure.component(slot).orElseThrow().id());
            var spaces = definitions.spaces().getOrDefault(structure.component(slot).orElseThrow().id(), java.util.List.of());
            Optional<GridPlacement> placement = Optional.of(state.orElseThrow().placements().get(slot));
            if (placement.isEmpty()) return view(Status.NO_SPACE);
            if (!layout.checkPlacement(slot, footprint, placement.get(), spaces).allowed()) return view(Status.INVALID_LAYOUT);
            layout = layout.withComponent(slot, footprint, placement.get(), spaces);
        }
        return new View(Status.VALID, Optional.of(layout));
    }

    /** Initializes an empty board or refreshes existing saved coordinates without repacking parts. */
    public static Plan initializeOrRefresh(EquipmentStructure original, GridDefinitions definitions) {
        View view = resolve(original, definitions, true);
        return view.status() == Status.VALID
                ? new Plan(Status.VALID, save(original, original, view.layout().orElseThrow(), definitions))
                : new Plan(view.status(), original);
    }

    /** Interface compatibility and occupancy are checked before reaching this pure geometry operation. */
    public static Plan install(EquipmentStructure original, ResourceLocation slot, EquipmentComponentInstance component,
                               Optional<GridPlacement> requested, GridDefinitions definitions) {
        if (original.slot(slot).isEmpty()) return new Plan(Status.UNKNOWN_SLOT, original);
        long candidateBytes = dev.equipmentstructure.api.grid.space.ComponentSpaceContents.storedBytes(original.withComponent(slot, component));
        if (candidateBytes > dev.equipmentstructure.api.grid.space.ComponentSpaceContents.MAX_BYTES
                && candidateBytes > dev.equipmentstructure.api.grid.space.ComponentSpaceContents.storedBytes(original)) return new Plan(Status.PLACEMENT_REJECTED, original);
        View current = resolve(original, definitions);
        if (!current.allowed()) return new Plan(current.status(), original);
        if (current.status() == Status.DISABLED) return requested.isPresent()
                ? new Plan(Status.PLACEMENT_REJECTED, original)
                : new Plan(Status.DISABLED, original.withComponent(slot, component));
        var footprint = definitions.components().get(component.id());
        var spaces = definitions.spaces().getOrDefault(component.id(), java.util.List.of());
        if (footprint == null) return new Plan(Status.MISSING_DEFINITION, original);
        var layout = current.layout().orElseThrow();
        var previous = layout.parts().get(slot);
        Optional<GridPlacement> placement = requested;
        if (placement.isEmpty() && previous != null) {
            var old = previous.placement();
            var rotation = footprint.rotations().contains(old.rotation()) ? old.rotation()
                    : footprint.rotations().stream().sorted().findFirst().orElseThrow();
            placement = Optional.of(new GridPlacement(old.x(), old.y(), rotation));
        }
        if (placement.isEmpty()) placement = firstFit(layout, slot, footprint, spaces);
        if (placement.isEmpty()) return new Plan(Status.NO_SPACE, original);
        if (!layout.checkPlacement(slot, footprint, placement.get(), spaces).allowed()) return new Plan(Status.PLACEMENT_REJECTED, original);
        var changed = original.withComponent(slot, component);
        return new Plan(Status.VALID, save(original, changed,
                layout.withComponent(slot, footprint, placement.get(), spaces), definitions));
    }

    /** Batch moves allow swapping positions; validate the final layout rather than transient overlap. */
    public static Plan move(EquipmentStructure original, Map<ResourceLocation, GridPlacement> edits, GridDefinitions definitions) {
        View current = resolve(original, definitions);
        if (current.status() != Status.VALID) return new Plan(current.status(), original);
        for (ResourceLocation slot : edits.keySet()) {
            if (original.slot(slot).isEmpty()) return new Plan(Status.UNKNOWN_SLOT, original);
            if (original.component(slot).isEmpty()) return new Plan(Status.EMPTY_SLOT, original);
        }
        var layout = current.layout().orElseThrow();
        for (ResourceLocation slot : edits.keySet()) layout = layout.withoutComponent(slot);
        for (ResourceLocation slot : edits.keySet().stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList()) {
            var footprint = definitions.components().get(original.component(slot).orElseThrow().id());
            var spaces = definitions.spaces().getOrDefault(original.component(slot).orElseThrow().id(), java.util.List.of());
            if (!layout.checkPlacement(slot, footprint, edits.get(slot), spaces).allowed()) return new Plan(Status.PLACEMENT_REJECTED, original);
            layout = layout.withComponent(slot, footprint, edits.get(slot), spaces);
        }
        return new Plan(Status.VALID, save(original, original, layout, definitions));
    }

    /** Always release removed geometry, including recovery of stale or missing definitions. */
    public static EquipmentStructure remove(EquipmentStructure original, ResourceLocation slot, GridDefinitions definitions) {
        var changed = original.withComponents(slot, java.util.List.of());
        if (changed.grid().isEmpty()) return changed;
        View remaining = resolve(changed, definitions, true);
        if (remaining.status() != Status.VALID) return changed;
        return save(original, changed, remaining.layout().orElseThrow(), definitions);
    }

    private static EquipmentStructure save(EquipmentStructure original, EquipmentStructure changed,
                                            GridLayout layout, GridDefinitions definitions) {
        Map<ResourceLocation, GridPlacement> positions = new HashMap<>();
        layout.parts().forEach((slot, part) -> positions.put(slot, part.placement()));
        String fingerprint = definitions.fingerprint(changed);
        if (original.grid().filter(old -> old.definitions().equals(fingerprint) && old.placements().equals(positions)).isPresent())
            return changed.withGrid(original.grid().orElseThrow());
        long revision = original.grid().map(GridState::nextRevision).orElse(0L);
        return changed.withGrid(new GridState(GridState.FORMAT, revision, fingerprint, positions));
    }

    private static View view(Status status) { return new View(status, Optional.empty()); }

    public static Optional<GridPlacement> firstFit(GridLayout layout, ResourceLocation slot, GridFootprint footprint) {
        return firstFit(layout, slot, footprint, layout.parts().containsKey(slot) ? layout.parts().get(slot).spaces() : java.util.List.of());
    }
    public static Optional<GridPlacement> firstFit(GridLayout layout, ResourceLocation slot, GridFootprint footprint,
            java.util.List<dev.equipmentstructure.api.grid.space.ComponentSpaceDefinition> spaces) {
        int width = layout.board().area().width(), height = layout.board().area().height();
        var rotations = footprint.rotations().stream().sorted().toList();
        // Build occupancy and each rotated candidate once, including signed exclusive regions.
        // Child panels do not occupy this board. A source/region overlap invalidates that rotation.
        var blocked = new java.util.HashSet<>(layout.board().blockedBodyCells());
        layout.parts().forEach((id, part) -> { if (!id.equals(slot)) blocked.addAll(part.reservedCells()); });
        var shapes = new java.util.EnumMap<GridRotation, java.util.Set<GridCell>>(GridRotation.class);
        for (var rotation : rotations) {
            var cells = new java.util.HashSet<>(footprint.oriented(rotation).cells());
            boolean valid = true;
            for (var space : spaces) if (space.mode() == dev.equipmentstructure.api.grid.space.ComponentSpaceDefinition.Mode.ATTACHED_REGION) {
                for (var cell : space.boardCells(footprint.shape(), new GridPlacement(0, 0, rotation))) {
                    if (!cells.add(cell)) valid = false;
                }
            }
            if (valid && cells.size() <= layout.board().area().area() - blocked.size()) shapes.put(rotation, cells);
        }
        if (shapes.isEmpty()) return Optional.empty();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) for (GridRotation rotation : rotations) {
            var cells = shapes.get(rotation);
            if (cells == null) continue;
            boolean fits = true;
            for (GridCell cell : cells) {
                var target = new GridCell(x + cell.x(), y + cell.y());
                if (!layout.board().area().contains(target) || blocked.contains(target)) { fits = false; break; }
            }
            if (fits) return Optional.of(new GridPlacement(x, y, rotation));
        }
        return Optional.empty();
    }
}
