package dev.equipmentstructure.api.client.appearance;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.mojang.serialization.DataResult;
import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.EquipmentComponentRegistry;
import dev.equipmentstructure.api.appearance.AppearanceCatalog;
import dev.equipmentstructure.api.appearance.AppearanceCatalogValidation;
import dev.equipmentstructure.api.appearance.AppearanceResourceDecoder;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Client-only resource bridge for the A1 appearance definitions. Preparation reads JSON off the
 * render thread; apply swaps one immutable catalog. A failed generation never replaces the last
 * successful catalog, so an invalid resource cannot hide the original equipment appearance.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AppearanceResourceReloadListener extends SimplePreparableReloadListener<AppearanceResourceReloadListener.Prepared> {
    public static final String HOST_DIRECTORY = "equipment_structure_api/appearance/host";
    public static final String COMPONENT_DIRECTORY = "equipment_structure_api/appearance/component";
    private static final Gson GSON = new Gson();
    private static final AtomicLong NEXT_GENERATION = new AtomicLong();
    private static final AtomicReference<AppearanceCatalog> CURRENT = new AtomicReference<>(
            new AppearanceCatalog(0, Map.of(), Map.of()));
    private static volatile List<String> LAST_ERRORS = List.of();

    @SubscribeEvent
    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new AppearanceResourceReloadListener());
    }

    public static AppearanceCatalog catalog() {
        return CURRENT.get();
    }

    public static long generation() {
        return CURRENT.get().generation();
    }

    /** Last attempted reload, separate from the still-active successful resource generation. */
    public static List<String> lastErrors() { return LAST_ERRORS; }

    @Override
    protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
        long generation = NEXT_GENERATION.incrementAndGet();
        List<String> errors = new ArrayList<>();
        Map<ResourceLocation, JsonElement> hosts = readDirectory(manager, HOST_DIRECTORY, errors);
        Map<ResourceLocation, JsonElement> components = readDirectory(manager, COMPONENT_DIRECTORY, errors);
        if (!errors.isEmpty()) return new Prepared(generation, DataResult.error(() -> String.join("; ", errors)));
        return new Prepared(generation, AppearanceResourceDecoder.decodeCatalog(generation, hosts, components));
    }

    @Override
    protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
        LAST_ERRORS = prepared.catalog().error().map(error -> List.of(
                error.message().substring(0, Math.min(error.message().length(), 1000)))).orElse(List.of());
        prepared.catalog().resultOrPartial(error -> EquipmentStructureApiMod.LOGGER.warn(
                        "Appearance resources rejected for generation {}: {}", prepared.generation(), error))
                .ifPresent(catalog -> {
                    CURRENT.set(catalog);
                    var unknown = AppearanceCatalogValidation.unknownComponentIds(
                            catalog, EquipmentComponentRegistry::isRegistered);
                    if (!unknown.isEmpty()) {
                        EquipmentStructureApiMod.LOGGER.warn(
                                "Appearance definitions without matching component registrations: {}. "
                                        + "Register the component definition before using this appearance.",
                                unknown);
                    }
                });
    }

    @Override
    public String getName() {
        return "Equipment Structure API appearance definitions";
    }

    private static Map<ResourceLocation, JsonElement> readDirectory(ResourceManager manager, String directory,
                                                                      List<String> errors) {
        Predicate<ResourceLocation> json = id -> id.getPath().endsWith(".json");
        Map<ResourceLocation, Resource> resources = manager.listResources(directory, json);
        Map<ResourceLocation, JsonElement> decoded = new LinkedHashMap<>();
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation resourceId = logicalId(directory, entry.getKey());
                    if (resourceId == null) {
                        errors.add("resource path is not a JSON definition: " + entry.getKey());
                        return;
                    }
                    try (var reader = entry.getValue().openAsReader()) {
                        decoded.put(resourceId, JsonParser.parseReader(reader));
                    } catch (IOException | JsonParseException error) {
                        errors.add(resourceId + ": " + error.getMessage());
                    }
                });
        return decoded;
    }

    /** Converts namespace:path/appearance.json to namespace:path/appearance for the payload ID check. */
    public static ResourceLocation logicalId(String directory, ResourceLocation resource) {
        String prefix = directory + "/";
        if (!resource.getPath().startsWith(prefix) || !resource.getPath().endsWith(".json")) return null;
        String path = resource.getPath().substring(prefix.length(), resource.getPath().length() - ".json".length());
        if (path.isBlank() || path.endsWith("/") || path.contains("//")) return null;
        return ResourceLocation.fromNamespaceAndPath(resource.getNamespace(), path);
    }

    record Prepared(long generation, DataResult<AppearanceCatalog> catalog) {}
}
