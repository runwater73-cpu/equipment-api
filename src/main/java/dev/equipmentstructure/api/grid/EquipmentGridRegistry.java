package dev.equipmentstructure.api.grid;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Code registrations for grid definitions. Hosts opt in by their template ID.
 * Definitions synchronize to clients; registered hosts use grid-aware structure transactions.
 * The assembly screen uses the grid workspace for these hosts. Registration does not create or bind a host template.
 */
public final class EquipmentGridRegistry {
    private static final Map<ResourceLocation, GridBoard> HOSTS = new ConcurrentHashMap<>();
    private static volatile Map<ResourceLocation, GridBoard> templates = Map.of();

    private EquipmentGridRegistry() {}

    public static void registerHost(ResourceLocation hostId, GridBoard board) {
        Objects.requireNonNull(hostId, "hostId");
        Objects.requireNonNull(board, "board");
        GridBoard previous = HOSTS.putIfAbsent(hostId, board);
        if (previous != null && !previous.equals(board)) {
            throw new IllegalStateException("Host grid already registered: " + hostId);
        }
        if (previous == null) GridDefinitions.invalidateRegistered();
    }

    /** Atomically replaces world-owned boards; removed templates cannot leak into another world. */
    public static void replaceTemplates(Map<ResourceLocation, GridBoard> boards) {
        var next = Map.copyOf(boards);
        if (!next.equals(templates)) {
            templates = next;
            GridDefinitions.invalidateRegistered();
        }
    }

    public static Optional<GridBoard> host(ResourceLocation hostId) {
        Objects.requireNonNull(hostId, "hostId");
        return Optional.ofNullable(templates.getOrDefault(hostId, HOSTS.get(hostId)));
    }

    public static Map<ResourceLocation, GridBoard> hosts() {
        var result = new java.util.HashMap<>(HOSTS);
        result.putAll(templates);
        return Map.copyOf(result);
    }

    public static boolean unregisterHost(ResourceLocation hostId) {
        boolean changed = HOSTS.remove(Objects.requireNonNull(hostId, "hostId")) != null;
        if (changed) GridDefinitions.invalidateRegistered();
        return changed;
    }
}
