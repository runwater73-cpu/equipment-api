package dev.equipmentstructure.api.grid;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import dev.equipmentstructure.api.grid.space.ComponentSpaceDefinition;
import java.util.List;

/**
 * Immutable geometry-only layout, keyed by the existing logical slot identity.
 * Does not transfer items or authorize installation: callers must also enforce interface policy.
 */
public final class GridLayout {
    public enum Failure { NONE, ROTATION_NOT_ALLOWED, OUTSIDE_BOARD, BODY_COLLISION, COMPONENT_COLLISION, SPACE_COLLISION }

    public record Check(Failure failure, Optional<GridCell> cell, Optional<ResourceLocation> conflictingSlot) {
        public Check {
            Objects.requireNonNull(failure, "failure");
            Objects.requireNonNull(cell, "cell");
            Objects.requireNonNull(conflictingSlot, "conflictingSlot");
        }

        public boolean allowed() { return failure == Failure.NONE; }
    }

    public record Part(GridFootprint footprint, GridPlacement placement, List<ComponentSpaceDefinition> spaces) {
        public Part {
            Objects.requireNonNull(footprint, "footprint");
            Objects.requireNonNull(placement, "placement");
            footprint.oriented(placement.rotation());
            spaces = List.copyOf(spaces);
        }
        public Part(GridFootprint footprint, GridPlacement placement) { this(footprint, placement, List.of()); }

        public Set<GridCell> cells() {
            // Keep hot geometry queries out of Stream's collector/lambda path (client crash report).
            var result = new HashSet<GridCell>();
            for (GridCell cell : footprint.oriented(placement.rotation()).cells()) {
                result.add(placement.translate(cell));
            }
            return Set.copyOf(result);
        }
        public Set<GridCell> reservedCells() {
            var result = new HashSet<GridCell>(cells());
            for (var space : spaces) {
                if (space.mode() == ComponentSpaceDefinition.Mode.ATTACHED_REGION) {
                    result.addAll(space.boardCells(footprint.shape(), placement));
                }
            }
            return Set.copyOf(result);
        }
    }

    private final GridBoard board;
    private final Map<ResourceLocation, Part> parts;
    private final Set<GridCell> bodyCells;

    private GridLayout(GridBoard board, Map<ResourceLocation, Part> parts) {
        this.board = Objects.requireNonNull(board, "board");
        TreeMap<ResourceLocation, Part> ordered = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        ordered.putAll(parts);
        this.parts = Collections.unmodifiableMap(ordered);
        this.bodyCells = board.blockedBodyCells();
    }

    public static GridLayout empty(GridBoard board) { return new GridLayout(board, Map.of()); }

    public GridBoard board() { return board; }

    /** Contains only installed placements. Empty logical interfaces never reserve cells. */
    public Map<ResourceLocation, Part> parts() { return parts; }

    public int freeArea() {
        return board.usableArea() - parts.values().stream().mapToInt(part -> part.reservedCells().size()).sum();
    }

    public Optional<ResourceLocation> componentAt(GridCell cell) {
        Objects.requireNonNull(cell, "cell");
        return parts.entrySet().stream().filter(entry -> entry.getValue().cells().contains(cell))
                .map(Map.Entry::getKey).findFirst();
    }

    /** Validates a new, moved or replaced part, excluding only that slot's old occupied cells. */
    public Check checkPlacement(ResourceLocation slotId, GridFootprint footprint, GridPlacement placement) {
        return checkPlacement(slotId, footprint, placement, parts.containsKey(slotId) ? parts.get(slotId).spaces() : List.of());
    }
    public Check checkPlacement(ResourceLocation slotId, GridFootprint footprint, GridPlacement placement, List<ComponentSpaceDefinition> spaces) {
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(footprint, "footprint");
        Objects.requireNonNull(placement, "placement");
        if (!footprint.rotations().contains(placement.rotation())) {
            return new Check(Failure.ROTATION_NOT_ALLOWED, Optional.empty(), Optional.empty());
        }
        Map<GridCell, ResourceLocation> occupied = new java.util.HashMap<>();
        parts.forEach((id, part) -> {
            if (!id.equals(slotId)) part.reservedCells().forEach(cell -> occupied.put(cell, id));
        });
        int width = board.area().width();
        int height = board.area().height();
        var candidates = new java.util.HashSet<GridCell>();
        try {
            footprint.oriented(placement.rotation()).cells().forEach(cell -> candidates.add(placement.translate(cell)));
            for (var space : spaces) if (space.mode() == ComponentSpaceDefinition.Mode.ATTACHED_REGION)
                for (var cell : space.boardCells(footprint.shape(), placement)) if (!candidates.add(cell))
                    return failed(Failure.SPACE_COLLISION, cell, slotId);
        } catch (ArithmeticException overflow) { return new Check(Failure.OUTSIDE_BOARD, Optional.empty(), Optional.empty()); }
        for (GridCell candidate : candidates) {
            long x = candidate.x();
            long y = candidate.y();
            if (x < 0 || y < 0 || x >= width || y >= height) {
                return new Check(Failure.OUTSIDE_BOARD, Optional.empty(), Optional.empty());
            }
            GridCell cell = new GridCell((int) x, (int) y);
            if (!board.area().contains(cell)) return failed(Failure.OUTSIDE_BOARD, cell, null);
            if (bodyCells.contains(cell)) return failed(Failure.BODY_COLLISION, cell, null);
            if (occupied.containsKey(cell)) return failed(Failure.COMPONENT_COLLISION, cell, occupied.get(cell));
        }
        return new Check(Failure.NONE, Optional.empty(), Optional.empty());
    }

    /** Returns a new validated layout; a failed change leaves this layout intact. */
    public GridLayout withComponent(ResourceLocation slotId, GridFootprint footprint, GridPlacement placement) {
        return withComponent(slotId, footprint, placement, parts.containsKey(slotId) ? parts.get(slotId).spaces() : List.of());
    }
    public GridLayout withComponent(ResourceLocation slotId, GridFootprint footprint, GridPlacement placement, List<ComponentSpaceDefinition> spaces) {
        Check check = checkPlacement(slotId, footprint, placement, spaces);
        if (!check.allowed()) throw new IllegalArgumentException("Invalid component placement: " + check);
        Map<ResourceLocation, Part> changed = new java.util.HashMap<>(parts);
        changed.put(slotId, new Part(footprint, placement, spaces));
        return new GridLayout(board, changed);
    }

    public GridLayout moved(ResourceLocation slotId, GridPlacement placement) {
        Part part = parts.get(Objects.requireNonNull(slotId, "slotId"));
        if (part == null) throw new IllegalArgumentException("No placed component for slot " + slotId);
        return withComponent(slotId, part.footprint(), placement);
    }

    public GridLayout withoutComponent(ResourceLocation slotId) {
        Objects.requireNonNull(slotId, "slotId");
        if (!parts.containsKey(slotId)) return this;
        Map<ResourceLocation, Part> changed = new java.util.HashMap<>(parts);
        changed.remove(slotId);
        return new GridLayout(board, changed);
    }

    private static Check failed(Failure failure, GridCell cell, ResourceLocation slot) {
        return new Check(failure, Optional.of(cell), Optional.ofNullable(slot));
    }
}
