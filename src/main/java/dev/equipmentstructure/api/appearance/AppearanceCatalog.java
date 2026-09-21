package dev.equipmentstructure.api.appearance;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Immutable definition generation. Keys are logical host/component definition IDs, not item icons. */
public record AppearanceCatalog(long generation, Map<ResourceLocation, AppearanceDefinitions.Host> hosts,
                                Map<ResourceLocation, AppearanceDefinitions.Part> components) {
    public AppearanceCatalog {
        if (generation < 0) throw new IllegalArgumentException("Negative appearance generation");
        hosts = Map.copyOf(hosts);
        components = Map.copyOf(components);
    }
}
