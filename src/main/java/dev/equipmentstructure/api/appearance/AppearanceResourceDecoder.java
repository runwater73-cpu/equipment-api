package dev.equipmentstructure.api.appearance;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure JSON-to-definition conversion used by a future resource-manager bridge. */
public final class AppearanceResourceDecoder {
    private static final Comparator<ResourceLocation> IDS = Comparator.comparing(ResourceLocation::toString);

    private AppearanceResourceDecoder() {}

    /** Resource path ID must match the payload ID; a renamed file cannot silently redefine another host. */
    public static DataResult<AppearanceDefinitions.Host> decodeHost(ResourceLocation resourceId, JsonElement json) {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(json, "json");
        return AppearanceResourceCodec.HOST.parse(JsonOps.INSTANCE, json).flatMap(value -> value.id().equals(resourceId)
                ? DataResult.success(value.toDefinition())
                : DataResult.error(() -> "Host resource ID " + resourceId + " does not match payload ID " + value.id()));
    }

    public static DataResult<AppearanceDefinitions.Part> decodeComponent(ResourceLocation resourceId, JsonElement json) {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(json, "json");
        return AppearanceResourceCodec.COMPONENT.parse(JsonOps.INSTANCE, json).flatMap(value -> value.id().equals(resourceId)
                ? DataResult.success(value.toDefinition())
                : DataResult.error(() -> "Component resource ID " + resourceId + " does not match payload ID " + value.id()));
    }

    /**
     * Decode a complete immutable generation. No partial catalog is returned: one bad resource rejects
     * the generation so an adapter can retain the previous generation/original appearance atomically.
     */
    public static DataResult<AppearanceCatalog> decodeCatalog(long generation,
                                                               Map<ResourceLocation, JsonElement> hostResources,
                                                               Map<ResourceLocation, JsonElement> componentResources) {
        if (generation < 0) return DataResult.error(() -> "Negative appearance generation");
        Objects.requireNonNull(hostResources, "hostResources");
        Objects.requireNonNull(componentResources, "componentResources");
        var hosts = new LinkedHashMap<ResourceLocation, AppearanceDefinitions.Host>();
        var components = new LinkedHashMap<ResourceLocation, AppearanceDefinitions.Part>();
        List<String> errors = new ArrayList<>();
        hostResources.keySet().stream().sorted(IDS).forEach(id -> decodeHost(id, hostResources.get(id))
                .resultOrPartial(message -> errors.add("host " + id + ": " + message)).ifPresent(value -> hosts.put(id, value)));
        componentResources.keySet().stream().sorted(IDS).forEach(id -> decodeComponent(id, componentResources.get(id))
                .resultOrPartial(message -> errors.add("component " + id + ": " + message)).ifPresent(value -> components.put(id, value)));
        if (!errors.isEmpty()) return DataResult.error(() -> String.join("; ", errors));
        return DataResult.success(new AppearanceCatalog(generation, hosts, components));
    }
}
