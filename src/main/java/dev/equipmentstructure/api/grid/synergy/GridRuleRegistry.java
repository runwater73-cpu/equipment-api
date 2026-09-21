package dev.equipmentstructure.api.grid.synergy;

import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.grid.*;
import dev.equipmentstructure.api.internal.ExtensionGuard;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/** Rules have stable names and derive results without modifying equipment or giving preview rewards. */
public final class GridRuleRegistry {
    private static final Map<ResourceLocation, GridRuleDefinition> RULES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Map<ResourceLocation, GridRegion>> REGIONS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, BiFunction<GridSpatialContext, ResourceLocation, GridRuleResult>> CUSTOM = new ConcurrentHashMap<>();
    private static final ExtensionGuard<ResourceLocation> GUARD = new ExtensionGuard<>("Grid spatial condition");
    private static final ThreadLocal<Set<ResourceLocation>> RUNNING = ThreadLocal.withInitial(HashSet::new);
    private static final ThreadLocal<dev.equipmentstructure.api.internal.SnapshotCache<EquipmentStructure, GridDefinitions, Evaluation>> EVALUATIONS =
            ThreadLocal.withInitial(() -> new dev.equipmentstructure.api.internal.SnapshotCache<>(8));
    private GridRuleRegistry() {}
    public record Key(ResourceLocation rule, ResourceLocation source) {}
    public static void register(GridRuleDefinition rule) {
        var old = RULES.putIfAbsent(rule.id(), rule); if (old != null && !old.equals(rule)) throw new IllegalStateException("Duplicate grid rule " + rule.id());
        if (old == null) GridDefinitions.invalidateRegistered();
    }
    public static void registerRegion(ResourceLocation host, ResourceLocation id, GridRegion region) {
        REGIONS.compute(host, (key, previous) -> {
            var changed = new HashMap<>(previous == null ? Map.<ResourceLocation, GridRegion>of() : previous);
            var old = changed.putIfAbsent(id, region); if (old != null && !old.equals(region)) throw new IllegalStateException("Duplicate region " + id);
            return Map.copyOf(changed);
        });
        GridDefinitions.invalidateRegistered();
    }
    public static void registerCustom(ResourceLocation id, BiFunction<GridSpatialContext, ResourceLocation, GridRuleResult> callback) {
        if (CUSTOM.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(callback)) != null) throw new IllegalStateException("Duplicate custom rule " + id);
    }
    public static Map<ResourceLocation, GridRuleDefinition> definitions() { return Map.copyOf(RULES); }
    public static Map<ResourceLocation, Map<ResourceLocation, GridRegion>> regions() { return Map.copyOf(REGIONS); }
    public static void clear() { RULES.clear(); REGIONS.clear(); CUSTOM.clear(); GUARD.clear(); GridDefinitions.invalidateRegistered(); }
    public static boolean unregister(ResourceLocation id) { boolean changed = RULES.remove(id) != null; if (changed) GridDefinitions.invalidateRegistered(); return changed; }
    static GridRuleResult custom(ResourceLocation id, GridSpatialContext context, ResourceLocation source) {
        var callback = CUSTOM.get(id);
        if (callback == null) return GridRuleResult.unknown("missing_custom_rule");
        if (!RUNNING.get().add(id)) return GridRuleResult.unknown("recursive_custom_rule");
        try { return GUARD.call(id, () -> Objects.requireNonNull(callback.apply(context, source)), GridRuleResult.unknown("custom_rule_failed")); }
        finally { RUNNING.get().remove(id); }
    }
    public static Map<Key, GridRuleResult> evaluate(EquipmentStructure structure, GridDefinitions definitions, boolean preview) {
        if (definitions.rules().isEmpty()) return Map.of();
        var evaluation = evaluation(structure, definitions);
        var result = new LinkedHashMap<Key, GridRuleResult>();
        for (var rule : definitions.rules().values()) if (rule.host().isEmpty() || rule.host().get().equals(structure.hostId())) {
            for (var node : evaluation.nodes.values()) if (rule.sources().matches(node)) {
                result.put(new Key(rule.id(), node.id()), evaluation.result(rule, node.id(), preview));
            }
        }
        return Collections.unmodifiableMap(result);
    }
    public static GridRuleResult result(EquipmentStructure structure, ResourceLocation rule, ResourceLocation source) {
        var definitions = GridDefinitionSync.current();
        var definition = definitions.rules().get(rule);
        if (definition == null || definition.host().filter(host -> !host.equals(structure.hostId())).isPresent())
            return GridRuleResult.unknown("missing_rule_or_source");
        var evaluation = evaluation(structure, definitions);
        var node = evaluation.nodes.get(source);
        return node == null || !definition.sources().matches(node) ? GridRuleResult.unknown("missing_rule_or_source")
                : evaluation.result(definition, source, net.neoforged.fml.util.thread.EffectiveSide.get().isClient());
    }

    private static Evaluation evaluation(EquipmentStructure structure, GridDefinitions definitions) {
        var cache = EVALUATIONS.get();
        var cached = cache.get(structure, definitions);
        return cached != null ? cached : cache.put(structure, definitions, new Evaluation(structure, definitions));
    }

    private static boolean layoutOnly(GridCondition condition) {
        if (condition.kind() == GridCondition.Kind.CUSTOM) return false;
        for (var child : condition.children()) if (!layoutOnly(child)) return false;
        return true;
    }

    private static final class Evaluation {
        private final GridSpatialContext context;
        private final Map<ResourceLocation, GridSpatialContext.Node> nodes;
        private final Map<ResourceLocation, Set<GridCell>> regions = new HashMap<>();
        private final Map<Key, GridRuleResult> pureResults = new HashMap<>();
        private final String unavailable;

        private Evaluation(EquipmentStructure structure, GridDefinitions definitions) {
            var view = GridTransactions.resolve(structure, definitions);
            unavailable = view.status().name().toLowerCase(Locale.ROOT);
            context = view.layout().map(layout -> new GridSpatialContext(structure, layout)).orElse(null);
            if (context != null) {
                nodes = context.nodes();
                definitions.regions().getOrDefault(structure.hostId(), Map.of()).forEach((id, region) -> regions.put(id, region.cells()));
            } else {
                nodes = new LinkedHashMap<>();
                nodes.put(GridSpatialContext.BODY, new GridSpatialContext.Node(GridSpatialContext.BODY, true, Optional.empty(), Optional.empty(), null));
                for (var slot : structure.slots()) structure.component(slot.id()).ifPresent(part ->
                        nodes.put(slot.id(), new GridSpatialContext.Node(slot.id(), false, Optional.of(part), Optional.of(slot), null)));
            }
        }

        private GridRuleResult result(GridRuleDefinition rule, ResourceLocation source, boolean preview) {
            if (context == null) return GridRuleResult.unknown(source.equals(GridSpatialContext.BODY) ? "invalid_layout" : unavailable);
            var key = new Key(rule.id(), source);
            boolean pure = layoutOnly(rule.condition());
            if (pure && pureResults.containsKey(key)) return pureResults.get(key);
            var result = GUARD.call(rule.id(), () -> rule.condition().evaluate(context, source, regions, preview), GridRuleResult.unknown("evaluation_failed"));
            // Custom callbacks may depend on world state. Never retain their outcome across queries.
            if (pure) {
                if (pureResults.size() >= 1024) pureResults.clear();
                pureResults.put(key, result);
            }
            return result;
        }
    }
}
