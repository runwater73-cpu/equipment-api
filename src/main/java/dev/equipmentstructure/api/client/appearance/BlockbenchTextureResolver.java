package dev.equipmentstructure.api.client.appearance;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves and caches validated Blockbench texture references for one resource generation. */
@OnlyIn(Dist.CLIENT)
public final class BlockbenchTextureResolver {
    private static final Map<Key, Optional<ResourceLocation>> CACHE = new ConcurrentHashMap<>();

    private BlockbenchTextureResolver() {}

    public static Optional<ResourceLocation> resolve(ResourceManager manager, ResourceLocation model,
                                                     BlockbenchModelDefinition.Texture texture,
                                                     long generation) {
        Objects.requireNonNull(manager, "manager");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(texture, "texture");
        Key key = new Key(model, texture, generation);
        return CACHE.computeIfAbsent(key, ignored -> BlockbenchModelLoader.findTexture(manager, model, texture));
    }

    public static void invalidateGeneration(long generation) {
        CACHE.keySet().removeIf(key -> key.generation() != generation);
    }

    public static void clear() {
        CACHE.clear();
    }

    private record Key(ResourceLocation model, BlockbenchModelDefinition.Texture texture, long generation) {}
}
