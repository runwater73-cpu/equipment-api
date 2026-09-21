package dev.equipmentstructure.api.internal;

import dev.equipmentstructure.api.EquipmentStructureApiMod;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.function.Supplier;

/** Internal extension boundary: recursion protection and bounded, rate-limited diagnostics. */
public final class ExtensionGuard<K> {
    private static final long LOG_INTERVAL_NANOS = 60_000_000_000L;
    private static final int MAX_DIAGNOSTICS = 1024;
    private final String name;
    private final LinkedHashMap<K, Long> logged = new LinkedHashMap<>();
    private final ThreadLocal<Set<K>> active = ThreadLocal.withInitial(HashSet::new);

    public ExtensionGuard(String name) {
        this.name = name;
    }

    public <T> T call(K key, Supplier<T> operation, T fallback) {
        Set<K> current = active.get();
        if (!current.add(key)) return fallback;
        try {
            return operation.get();
        } catch (RuntimeException exception) {
            if (shouldLog(key)) {
                EquipmentStructureApiMod.LOGGER.warn("{} failed for {}; using fallback", name, key, exception);
            }
            return fallback;
        } finally {
            current.remove(key);
            if (current.isEmpty()) active.remove();
        }
    }

    private synchronized boolean shouldLog(K key) {
        long now = System.nanoTime();
        Long previous = logged.get(key);
        if (previous != null && now - previous < LOG_INTERVAL_NANOS) return false;
        if (previous == null && logged.size() >= MAX_DIAGNOSTICS) {
            logged.remove(logged.keySet().iterator().next());
        }
        logged.put(key, now);
        return true;
    }

    public synchronized void forget(K key) { logged.remove(key); }
    public synchronized void clear() { logged.clear(); }
}
