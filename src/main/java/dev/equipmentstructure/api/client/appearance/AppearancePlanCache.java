package dev.equipmentstructure.api.client.appearance;

import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.appearance.AppearancePlan;
import dev.equipmentstructure.api.appearance.AppearanceSupport;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Small bounded cache for immutable appearance plans.
 *
 * <p>The key contains only immutable snapshots. It never retains an ItemStack,
 * player, level or render buffer, so a plan cannot accidentally keep a world
 * alive. Access is synchronized because GUI and world render paths can ask for
 * a plan from different client threads during a resource reload.</p>
 */
public final class AppearancePlanCache {
    public static final int DEFAULT_MAX_ENTRIES = 256;

    private final int maxEntries;
    private final LinkedHashMap<Key, AppearancePlan> entries = new LinkedHashMap<>(16, 0.75f, true);

    public AppearancePlanCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public AppearancePlanCache(int maxEntries) {
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be positive");
        this.maxEntries = maxEntries;
    }

    public AppearancePlan getOrCompute(EquipmentStructure structure,
                                       AppearanceSupport support,
                                       Supplier<AppearancePlan> factory) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(support, "support");
        Objects.requireNonNull(factory, "factory");
        Key key = new Key(structure, support);
        synchronized (this) {
            AppearancePlan cached = entries.get(key);
            if (cached != null) return cached;
        }
        AppearancePlan computed = Objects.requireNonNull(factory.get(), "factory result");
        // A provider may return a plan for a different generation. Do not let
        // that result enter a cache keyed by the current support snapshot.
        if (computed.generation() != support.generation()) return computed;
        synchronized (this) {
            // Another render path may have completed the same plan while this
            // thread was resolving outside the lock. Reuse that instance and
            // avoid replacing a more recent equal result.
            AppearancePlan existing = entries.get(key);
            if (existing != null) return existing;
            entries.put(key, computed);
            while (entries.size() > maxEntries) entries.remove(entries.keySet().iterator().next());
            return computed;
        }
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized void invalidateGeneration(long generation) {
        entries.keySet().removeIf(key -> key.support.generation() != generation);
    }

    public synchronized int size() {
        return entries.size();
    }

    private record Key(EquipmentStructure structure, AppearanceSupport support) {
        private Key {
            Objects.requireNonNull(structure, "structure");
            Objects.requireNonNull(support, "support");
        }
    }
}
