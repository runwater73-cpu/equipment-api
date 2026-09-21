package dev.equipmentstructure.api.grid.synergy;

import dev.equipmentstructure.api.grid.GridCell;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Explainable output for authors, logs and future UI. UNKNOWN never activates gameplay. */
public record GridRuleResult(Status status, String reason, Map<String, Double> measurements,
                             Set<ResourceLocation> targets, Set<GridCell> cells) {
    public enum Status { SATISFIED, UNSATISFIED, UNKNOWN }
    public GridRuleResult { Objects.requireNonNull(status); Objects.requireNonNull(reason); measurements = Map.copyOf(measurements); targets = Set.copyOf(targets); cells = Set.copyOf(cells); }
    public boolean active() { return status == Status.SATISFIED; }
    public static GridRuleResult unknown(String reason) { return new GridRuleResult(Status.UNKNOWN, reason, Map.of(), Set.of(), Set.of()); }
    public static GridRuleResult measured(boolean yes, String reason, double value, double min, double max,
            Collection<GridSpatialContext.Node> targets, Collection<GridCell> cells) {
        return new GridRuleResult(yes ? Status.SATISFIED : Status.UNSATISFIED, reason, Map.of("value", value, "min", min, "max", max),
                targets.stream().map(GridSpatialContext.Node::id).collect(java.util.stream.Collectors.toSet()), Set.copyOf(cells));
    }
}
