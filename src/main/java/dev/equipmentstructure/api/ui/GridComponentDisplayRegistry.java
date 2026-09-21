package dev.equipmentstructure.api.ui;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Resource packs override complete Java display entries; neither source changes server geometry. */
public final class GridComponentDisplayRegistry {
    private static final Map<ResourceLocation, GridComponentDisplay> JAVA = new ConcurrentHashMap<>();
    private static volatile Map<ResourceLocation, GridComponentDisplay> resources = Map.of();
    private GridComponentDisplayRegistry() {}
    public static void register(ResourceLocation componentId, GridComponentDisplay display) {
        Objects.requireNonNull(componentId); Objects.requireNonNull(display);
        var old = JAVA.putIfAbsent(componentId, display);
        if (old != null && !old.equals(display)) throw new IllegalStateException("Grid display already registered: " + componentId);
    }
    public static GridComponentDisplay get(ResourceLocation componentId) {
        Objects.requireNonNull(componentId);
        return resources.getOrDefault(componentId, JAVA.getOrDefault(componentId, GridComponentDisplay.defaults()));
    }
    public static boolean unregister(ResourceLocation componentId) { return JAVA.remove(componentId) != null; }
    public static void replaceResources(Map<ResourceLocation, GridComponentDisplay> entries) { resources = Map.copyOf(entries); }
    public static void clear() { JAVA.clear(); resources = Map.of(); }
}
