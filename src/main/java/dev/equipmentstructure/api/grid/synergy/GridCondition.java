package dev.equipmentstructure.api.grid.synergy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.equipmentstructure.api.grid.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Bounded, serializable condition tree. Custom leaves name a server callback, never executable network code. */
public record GridCondition(Kind kind, GridSelector target, GridSelector through, int min, int max,
        List<GridCell> offsets, List<Role> roles, Optional<ResourceLocation> key, boolean local,
        int direction, List<GridCondition> children) {
    public enum Kind { NEIGHBORS, DIAGONAL_NEIGHBORS, CONTACT_LENGTH, DISTANCE, DIRECTION, REGION_ANY, REGION_ALL, PATTERN, EMPTY, CONNECTED, ALL, ANY, NOT, CUSTOM }
    public static final Codec<GridCell> OFFSET = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(-64, 64).fieldOf("x").forGetter(GridCell::x), Codec.intRange(-64, 64).fieldOf("y").forGetter(GridCell::y)).apply(i, GridCell::new));
    public record Role(GridCell offset, GridSelector target) {
        public static final Codec<Role> CODEC = RecordCodecBuilder.create(i -> i.group(OFFSET.fieldOf("offset").forGetter(Role::offset),
                GridSelector.CODEC.fieldOf("target").forGetter(Role::target)).apply(i, Role::new));
    }
    public static final Codec<GridCondition> CODEC = Codec.recursive("grid_condition", self -> RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(GridCondition::kind),
            GridSelector.CODEC.optionalFieldOf("target", GridSelector.any()).forGetter(GridCondition::target),
            GridSelector.CODEC.optionalFieldOf("through", GridSelector.any()).forGetter(GridCondition::through),
            Codec.intRange(0, 16384).optionalFieldOf("min", 1).forGetter(GridCondition::min),
            Codec.intRange(0, 16384).optionalFieldOf("max", 16384).forGetter(GridCondition::max),
            OFFSET.listOf(0, 256).optionalFieldOf("offsets", List.of()).forGetter(GridCondition::offsets),
            Role.CODEC.listOf(0, 8).optionalFieldOf("roles", List.of()).forGetter(GridCondition::roles),
            ResourceLocation.CODEC.optionalFieldOf("key").forGetter(GridCondition::key),
            Codec.BOOL.optionalFieldOf("local", false).forGetter(GridCondition::local),
            Codec.intRange(0, 3).optionalFieldOf("direction", 0).forGetter(GridCondition::direction),
            self.listOf(0, 16).optionalFieldOf("children", List.of()).forGetter(GridCondition::children)
    ).apply(i, GridCondition::new)));
    public GridCondition {
        Objects.requireNonNull(kind); Objects.requireNonNull(target); Objects.requireNonNull(through); Objects.requireNonNull(key);
        offsets = List.copyOf(offsets); roles = List.copyOf(roles); children = List.copyOf(children);
        if (min < 0 || max < min || max > 16384 || direction < 0 || direction > 3 || offsets.size() > 256 || roles.size() > 8
                || children.size() > 16 || children.stream().mapToInt(GridCondition::nodes).sum() > 63
                || children.stream().mapToInt(GridCondition::depth).max().orElse(0) >= 16) throw new IllegalArgumentException("Condition exceeds bounds");
        if (kind == Kind.NOT && children.size() != 1 || (kind == Kind.ALL || kind == Kind.ANY) && children.isEmpty()
                || (kind == Kind.CUSTOM || kind == Kind.REGION_ANY || kind == Kind.REGION_ALL) && key.isEmpty()
                || kind == Kind.PATTERN && roles.isEmpty() || kind == Kind.EMPTY && offsets.isEmpty()) throw new IllegalArgumentException("Missing condition arguments");
    }
    private int nodes() { return 1 + children.stream().mapToInt(GridCondition::nodes).sum(); }
    private int depth() { return 1 + children.stream().mapToInt(GridCondition::depth).max().orElse(0); }
    public static GridCondition count(Kind kind, GridSelector target, int min, int max) {
        return new GridCondition(kind, target, GridSelector.any(), min, max, List.of(), List.of(), Optional.empty(), false, 0, List.of());
    }
    public static GridCondition neighbors(GridSelector targets, int min) { return count(Kind.NEIGHBORS, targets, min, 16384); }
    public static GridCondition distance(GridSelector targets, int min, int max) { return count(Kind.DISTANCE, targets, min, max); }
    public static GridCondition direction(GridSelector target, int clockwiseFromNorth, boolean local) {
        return new GridCondition(Kind.DIRECTION, target, GridSelector.any(), 1, 16384, List.of(), List.of(), Optional.empty(), local, clockwiseFromNorth, List.of());
    }
    public static GridCondition region(ResourceLocation id, boolean all) { return new GridCondition(all ? Kind.REGION_ALL : Kind.REGION_ANY, GridSelector.any(), GridSelector.any(), 1, 16384, List.of(), List.of(), Optional.of(id), false, 0, List.of()); }
    public static GridCondition pattern(boolean local, Role... roles) { return new GridCondition(Kind.PATTERN, GridSelector.any(), GridSelector.any(), 1, 16384, List.of(), List.of(roles), Optional.empty(), local, 0, List.of()); }
    public static GridCondition empty(boolean local, int minimum, GridCell... offsets) { return new GridCondition(Kind.EMPTY, GridSelector.any(), GridSelector.any(), minimum, 16384, List.of(offsets), List.of(), Optional.empty(), local, 0, List.of()); }
    public static GridCondition connected(GridSelector targets, GridSelector through) { return new GridCondition(Kind.CONNECTED, targets, through, 1, 16384, List.of(), List.of(), Optional.empty(), false, 0, List.of()); }
    public static GridCondition custom(ResourceLocation id) { return new GridCondition(Kind.CUSTOM, GridSelector.any(), GridSelector.any(), 1, 16384, List.of(), List.of(), Optional.of(id), false, 0, List.of()); }
    public static GridCondition all(GridCondition... conditions) { return compound(Kind.ALL, conditions); }
    public static GridCondition any(GridCondition... conditions) { return compound(Kind.ANY, conditions); }
    public GridCondition not() { return compound(Kind.NOT, this); }
    private static GridCondition compound(Kind kind, GridCondition... conditions) { return new GridCondition(kind, GridSelector.any(), GridSelector.any(), 1, 16384, List.of(), List.of(), Optional.empty(), false, 0, List.of(conditions)); }

    public GridRuleResult evaluate(GridSpatialContext context, ResourceLocation source, Map<ResourceLocation, Set<GridCell>> regions, boolean preview) {
        var origin = context.require(source);
        var matches = new ArrayList<>(context.select(target).stream().filter(n -> !n.id().equals(source)).toList());
        var cells = new HashSet<GridCell>(); double value;
        switch (kind) {
            case CUSTOM -> { return preview ? GridRuleResult.unknown("server_condition") : GridRuleRegistry.custom(key.orElseThrow(), context, source); }
            case ALL, ANY, NOT -> {
                var results = children.stream().map(c -> c.evaluate(context, source, regions, preview)).toList();
                var targets = new HashSet<ResourceLocation>(); var measurements = new LinkedHashMap<String, Double>();
                for (int n = 0; n < results.size(); n++) { var result = results.get(n); targets.addAll(result.targets()); cells.addAll(result.cells()); final int index = n;
                    result.measurements().forEach((k, v) -> measurements.put(index + "." + k, v)); }
                GridRuleResult.Status status;
                if (kind == Kind.NOT) status = results.getFirst().status() == GridRuleResult.Status.UNKNOWN ? GridRuleResult.Status.UNKNOWN
                        : results.getFirst().active() ? GridRuleResult.Status.UNSATISFIED : GridRuleResult.Status.SATISFIED;
                else if (kind == Kind.ALL) status = results.stream().anyMatch(r -> r.status() == GridRuleResult.Status.UNSATISFIED) ? GridRuleResult.Status.UNSATISFIED
                        : results.stream().allMatch(GridRuleResult::active) ? GridRuleResult.Status.SATISFIED : GridRuleResult.Status.UNKNOWN;
                else status = results.stream().anyMatch(GridRuleResult::active) ? GridRuleResult.Status.SATISFIED
                        : results.stream().anyMatch(r -> r.status() == GridRuleResult.Status.UNKNOWN) ? GridRuleResult.Status.UNKNOWN : GridRuleResult.Status.UNSATISFIED;
                return new GridRuleResult(status, kind.name().toLowerCase(Locale.ROOT), measurements, targets, cells);
            }
            case NEIGHBORS, DIAGONAL_NEIGHBORS, CONTACT_LENGTH -> {
                matches = new ArrayList<>(context.neighbors(source, target, kind == Kind.DIAGONAL_NEIGHBORS));
                value = kind == Kind.CONTACT_LENGTH ? matches.stream().mapToInt(n -> GridSpatialContext.contact(origin, n)).sum() : matches.size();
            }
            case DISTANCE -> {
                value = matches.stream().mapToInt(n -> GridSpatialContext.distance(origin, n, false)).min().orElse(-1);
                double nearest = value; matches.removeIf(n -> GridSpatialContext.distance(origin, n, false) != nearest);
            }
            case DIRECTION -> {
                int turn = (direction + (local ? origin.part().placement().rotation().ordinal() : 0)) % 4;
                // Entire target lies strictly on one side of the source's actual bounds.
                int minX = origin.cells().stream().mapToInt(GridCell::x).min().orElseThrow(), maxX = origin.cells().stream().mapToInt(GridCell::x).max().orElseThrow();
                int minY = origin.cells().stream().mapToInt(GridCell::y).min().orElseThrow(), maxY = origin.cells().stream().mapToInt(GridCell::y).max().orElseThrow();
                matches.removeIf(n -> !n.cells().stream().allMatch(c -> switch (turn) { case 0 -> c.y() < minY; case 1 -> c.x() > maxX; case 2 -> c.y() > maxY; default -> c.x() < minX; })); value = matches.size();
            }
            case REGION_ANY, REGION_ALL -> {
                var region = regions.get(key.orElseThrow()); if (region == null) return GridRuleResult.unknown("missing_region");
                cells.addAll(region); matches.clear(); long count = origin.cells().stream().filter(region::contains).count();
                return GridRuleResult.measured(kind == Kind.REGION_ALL ? count == origin.cells().size() : count > 0, "region", count, kind == Kind.REGION_ALL ? origin.cells().size() : 1, origin.cells().size(), matches, cells);
            }
            case EMPTY -> {
                offsets.forEach(offset -> cells.add(context.relative(source, offset, local))); var occupied = context.occupied();
                value = cells.stream().filter(context.layout().board().area()::contains).filter(c -> !occupied.contains(c)).count(); matches.clear();
            }
            case PATTERN -> {
                matches.clear(); var seen = new HashSet<ResourceLocation>(); boolean valid = true;
                for (var role : roles) {
                    var cell = context.relative(source, role.offset(), local); cells.add(cell);
                    var candidate = context.select(role.target()).stream().filter(n -> !n.id().equals(source) && !seen.contains(n.id()) && n.cells().contains(cell)).findFirst();
                    if (candidate.isEmpty()) valid = false; else { matches.add(candidate.get()); seen.add(candidate.get().id()); }
                }
                return GridRuleResult.measured(valid, "pattern", matches.size(), roles.size(), roles.size(), matches, cells);
            }
            case CONNECTED -> {
                var connected = context.connected(source, through);
                matches.removeIf(n -> !connected.contains(n.id()) && connected.stream().noneMatch(id -> !GridSpatialContext.overlaps(context.require(id), n) && GridSpatialContext.contact(context.require(id), n) > 0));
                value = matches.size(); connected.forEach(id -> cells.addAll(context.require(id).cells()));
            }
            default -> throw new IllegalStateException();
        }
        matches.forEach(n -> cells.addAll(n.cells()));
        return GridRuleResult.measured(value >= min && value <= max, kind.name().toLowerCase(Locale.ROOT), value, min, max, matches, cells);
    }
}
