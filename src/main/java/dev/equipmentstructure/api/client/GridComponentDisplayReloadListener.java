package dev.equipmentstructure.api.client;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.ui.GridComponentDisplay;
import dev.equipmentstructure.api.ui.GridComponentDisplayRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** F3+T reload of optional grid artwork; a malformed generation keeps the previous metadata. */
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GridComponentDisplayReloadListener extends SimplePreparableReloadListener<GridComponentDisplayReloadListener.Prepared> {
    public static final String DIRECTORY = "equipment_structure_api/grid_display";
    private static volatile List<String> lastErrors = List.of();
    private static volatile long generation;
    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new GridComponentDisplayReloadListener());
    }
    public static List<String> lastErrors() { return lastErrors; }
    public static long generation() { return generation; }

    @Override protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
        var entries = new LinkedHashMap<ResourceLocation, GridComponentDisplay>();
        var errors = new ArrayList<String>();
        manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    try (var reader = entry.getValue().openAsReader()) {
                        var path = entry.getKey().getPath().substring(DIRECTORY.length() + 1);
                        path = path.substring(0, path.length() - 5);
                        if (path.isBlank() || path.endsWith("/") || path.contains("//")) throw new IllegalArgumentException("Invalid component resource path");
                        var id = ResourceLocation.fromNamespaceAndPath(entry.getKey().getNamespace(), path);
                        var display = GridComponentDisplay.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
                        if (display.texture().isPresent()) validateTexture(manager, display.texture().get());
                        entries.put(id, display);
                    } catch (Exception failure) { errors.add(entry.getKey() + ": " + failure.getMessage()); }
                });
        return new Prepared(Map.copyOf(entries), List.copyOf(errors));
    }
    private static void validateTexture(ResourceManager manager, GridComponentDisplay.Texture texture) throws java.io.IOException {
        var resource = manager.getResource(texture.resource()).orElseThrow(() ->
                new IllegalArgumentException("Missing grid texture: " + texture.resource()));
        try (var stream = resource.open(); var input = javax.imageio.ImageIO.createImageInputStream(stream)) {
            if (input == null) throw new IllegalArgumentException("Invalid PNG: " + texture.resource());
            var readers = javax.imageio.ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Invalid PNG: " + texture.resource());
            var reader = readers.next();
            try {
                reader.setInput(input);
                if (!reader.getFormatName().equalsIgnoreCase("png") || reader.getWidth(0) != texture.width()
                        || reader.getHeight(0) != texture.height())
                    throw new IllegalArgumentException("Grid texture dimensions/format do not match definition: " + texture.resource());
            } finally { reader.dispose(); }
        }
    }
    @Override protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
        generation++;
        lastErrors = prepared.errors();
        if (lastErrors.isEmpty()) GridComponentDisplayRegistry.replaceResources(prepared.entries());
        else EquipmentStructureApiMod.LOGGER.warn("Grid display resources rejected; previous metadata retained: {}", lastErrors);
    }
    @Override public String getName() { return "Equipment API grid component displays"; }
    record Prepared(Map<ResourceLocation, GridComponentDisplay> entries, List<String> errors) {}
}
