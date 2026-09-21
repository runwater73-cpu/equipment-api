package dev.equipmentstructure.api.client.appearance;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.equipmentstructure.api.EquipmentStructureApiMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.util.*;

/** Data-driven 2D appearance assets. Each JSON points at one ordinary PNG texture. */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SpriteModelAssetRegistry {
    public static final String DIRECTORY = "equipment_structure_api/appearance/sprite";
    private static volatile Map<ResourceLocation, SpriteModelDefinition> PREPARED = Map.of();
    private static final Map<ResourceLocation, AppearanceModelAssetRenderer> OWNED = new HashMap<>();

    private SpriteModelAssetRegistry() {}

    public static Set<ResourceLocation> preparedAssets() { return Set.copyOf(PREPARED.keySet()); }
    public static Optional<SpriteModelDefinition> prepared(ResourceLocation id) {
        return Optional.ofNullable(PREPARED.get(id));
    }

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new ReloadListener());
    }

    static final class ReloadListener extends SimplePreparableReloadListener<Prepared> {
        @Override protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, SpriteModelDefinition> sprites = new HashMap<>();
            Map<ResourceLocation, SpriteQuadRenderer.Mesh> meshes = new HashMap<>();
            for (var entry : manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation id = logicalId(entry.getKey());
                if (id == null) continue;
                try (var reader = entry.getValue().openAsReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    SpriteModelDefinition sprite = decode(id, json);
                    var pixels = drawablePng(manager, sprite.texture());
                    if (pixels.isEmpty()) {
                        EquipmentStructureApiMod.LOGGER.warn("Sprite appearance {} has missing/invalid/transparent texture {}", id, sprite.texture());
                        continue;
                    }
                    meshes.put(id, SpriteQuadRenderer.mesh(sprite, pixels.get()));
                    sprites.put(id, sprite);
                } catch (Exception error) {
                    EquipmentStructureApiMod.LOGGER.warn("Could not decode sprite appearance {}", id, error);
                }
            }
            return new Prepared(Map.copyOf(sprites), Map.copyOf(meshes));
        }

        @Override protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
            install(prepared.sprites(), prepared.meshes());
        }

        @Override public String getName() { return "Equipment API sprite appearance assets"; }
    }

    /** Pure JSON decoding. Textures resolve to standalone PNG files, not atlas sprite names. */
    public static SpriteModelDefinition decode(ResourceLocation id, JsonObject json) {
        if (json.has("format_version") && !json.get("format_version").toString().equals("1"))
            throw new IllegalArgumentException("Unsupported sprite format_version");
        return new SpriteModelDefinition(id, ResourceLocation.parse(json.get("texture").getAsString()),
                json.has("width") ? json.get("width").getAsFloat() : 8f,
                json.has("height") ? json.get("height").getAsFloat() : 8f,
                !json.has("extrude") || json.get("extrude").getAsBoolean());
    }

    static void install(Map<ResourceLocation, SpriteModelDefinition> sprites) {
        install(sprites, Map.of());
    }

    private static void install(Map<ResourceLocation, SpriteModelDefinition> sprites,
                                Map<ResourceLocation, SpriteQuadRenderer.Mesh> meshes) {
        OWNED.forEach(AppearanceModelAssetRegistry::unregister);
        OWNED.clear();
        Map<ResourceLocation, SpriteModelDefinition> installed = new HashMap<>();
        for (var entry : sprites.entrySet()) {
            ResourceLocation id = entry.getKey();
            if (AppearanceModelAssetRegistry.get(id).isPresent()) {
                EquipmentStructureApiMod.LOGGER.warn("Sprite appearance {} conflicts with a registered renderer; keeping the renderer", id);
                continue;
            }
            var mesh = meshes.getOrDefault(id, new SpriteQuadRenderer.Mesh(0, List.of()));
            AppearanceModelAssetRenderer renderer = context -> SpriteQuadRenderer.render(context, entry.getValue(), mesh);
            AppearanceModelAssetRegistry.register(id, renderer);
            OWNED.put(id, renderer);
            installed.put(id, entry.getValue());
        }
        PREPARED = Map.copyOf(installed);
        AppearanceRuntime.clearCache();
    }

    public static ResourceLocation logicalId(ResourceLocation resource) {
        String prefix = DIRECTORY + "/";
        if (!resource.getPath().startsWith(prefix) || !resource.getPath().endsWith(".json")) return null;
        String path = resource.getPath().substring(prefix.length(), resource.getPath().length() - 5);
        return path.isBlank() ? null : ResourceLocation.fromNamespaceAndPath(resource.getNamespace(), path);
    }

    static boolean hasDrawablePng(ResourceManager manager, ResourceLocation texture) {
        return drawablePng(manager, texture).isPresent();
    }

    private static Optional<BufferedImage> drawablePng(ResourceManager manager, ResourceLocation texture) {
        try {
            Optional<Resource> found = manager.getResource(texture);
            if (found.isEmpty()) return Optional.empty();
            try (var stream = found.get().open()) {
                var readers = ImageIO.getImageReadersBySuffix("png");
                if (!readers.hasNext()) return Optional.empty();
                var reader = readers.next();
                try (var input = new MemoryCacheImageInputStream(stream)) {
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || width > 4096 || height > 4096) return Optional.empty();
                    var image = reader.read(0);
                    for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                        if ((image.getRGB(x, y) >>> 24) >= 26) return Optional.of(image);
                    return Optional.empty();
                } finally { reader.dispose(); }
            }
        } catch (IOException | RuntimeException error) { return Optional.empty(); }
    }

    record Prepared(Map<ResourceLocation, SpriteModelDefinition> sprites,
                    Map<ResourceLocation, SpriteQuadRenderer.Mesh> meshes) {}
}
