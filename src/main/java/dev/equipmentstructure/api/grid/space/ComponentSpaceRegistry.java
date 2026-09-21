package dev.equipmentstructure.api.grid.space;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.internal.ExtensionGuard;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

public final class ComponentSpaceRegistry {
    private static final Map<ResourceLocation, List<ComponentSpaceDefinition>> DEFINITIONS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, BiPredicate<EquipmentComponentInstance, ItemStack>> FILTERS = new ConcurrentHashMap<>();
    private static final ExtensionGuard<ResourceLocation> GUARD = new ExtensionGuard<>("Component space filter");
    private static final java.util.concurrent.atomic.AtomicLong GENERATION = new java.util.concurrent.atomic.AtomicLong();
    private ComponentSpaceRegistry() {}
    public static void register(ResourceLocation component, ComponentSpaceDefinition... spaces) {
        var values = List.of(spaces);
        if (values.size() > 16 || values.stream().map(ComponentSpaceDefinition::id).distinct().count() != values.size())
            throw new IllegalArgumentException("Duplicate or too many spaces");
        var old = DEFINITIONS.putIfAbsent(Objects.requireNonNull(component), values);
        if (old != null && !old.equals(values)) throw new IllegalStateException("Spaces already registered: " + component);
        if (old == null) { GENERATION.incrementAndGet(); dev.equipmentstructure.api.grid.GridDefinitions.invalidateRegistered(); }
    }
    public static void registerFilter(ResourceLocation id, BiPredicate<EquipmentComponentInstance, ItemStack> filter) {
        if (FILTERS.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(filter)) != null) throw new IllegalStateException("Duplicate space filter: " + id);
        GENERATION.incrementAndGet();
    }
    public static Map<ResourceLocation, List<ComponentSpaceDefinition>> definitions() { return Map.copyOf(DEFINITIONS); }
    public static long generation() { return GENERATION.get(); }
    public static void clear() { DEFINITIONS.clear(); FILTERS.clear(); GUARD.clear(); GENERATION.incrementAndGet(); dev.equipmentstructure.api.grid.GridDefinitions.invalidateRegistered(); }
    public static boolean unregister(ResourceLocation component) { boolean changed = DEFINITIONS.remove(component) != null; if (changed) { GENERATION.incrementAndGet(); dev.equipmentstructure.api.grid.GridDefinitions.invalidateRegistered(); } return changed; }
    public static boolean accepts(ComponentSpaceDefinition definition, EquipmentComponentInstance owner, ItemStack item) {
        if (item.isEmpty()) return false;
        boolean classified = definition.items().isEmpty() && definition.tags().isEmpty()
                || definition.items().contains(BuiltInRegistries.ITEM.getKey(item.getItem()))
                || definition.tags().stream().anyMatch(tag -> item.is(TagKey.create(Registries.ITEM, tag)));
        if (!classified) return false;
        if (definition.predicate().isEmpty()) return true;
        var key = definition.predicate().get(); var filter = FILTERS.get(key);
        return filter != null && GUARD.call(key, () -> filter.test(owner, item.copy()), false);
    }
}
