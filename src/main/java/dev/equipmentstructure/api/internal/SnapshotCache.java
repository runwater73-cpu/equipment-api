package dev.equipmentstructure.api.internal;

import java.util.ArrayDeque;
import java.util.Objects;

/** Small thread-confined cache for immutable snapshots. Never hashes potentially large item NBT. */
@org.jetbrains.annotations.ApiStatus.Internal
public final class SnapshotCache<A, B, V> {
    private final int limit;
    private final ArrayDeque<Entry<A, B, V>> entries = new ArrayDeque<>();

    public SnapshotCache(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Positive cache limit required");
        this.limit = limit;
    }

    public V get(A first, B second) {
        for (var entry : entries) if (entry.first == first && entry.second == second) return entry.value;
        return null;
    }

    public V put(A first, B second, V value) {
        Objects.requireNonNull(value);
        entries.removeIf(entry -> entry.first == first && entry.second == second);
        if (entries.size() == limit) entries.removeLast();
        entries.addFirst(new Entry<>(first, second, value));
        return value;
    }

    private record Entry<A, B, V>(A first, B second, V value) {}
}
