package dev.equipmentstructure.api.client;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.ui.EquipmentSlotDisplay;
import dev.equipmentstructure.api.ui.EquipmentStructureUiRegistry;
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

/** Resource-pack slot metadata, isolated from saved structure and server slot policy. */
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EquipmentSlotDisplayReloadListener extends SimplePreparableReloadListener<EquipmentSlotDisplayReloadListener.Prepared> {
    public static final String DIRECTORY = "equipment_structure_api/slot_display";
    public static final String INTERFACE_DIRECTORY = "equipment_structure_api/interface_display";
    public static final Codec<Map<ResourceLocation, EquipmentSlotDisplay>> CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, EquipmentSlotDisplay.CODEC).fieldOf("slots").codec();
    private static volatile List<String> lastErrors = List.of();

    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new EquipmentSlotDisplayReloadListener());
    }

    public static List<String> lastErrors() { return lastErrors; }

    @Override protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
        var definitions = new LinkedHashMap<ResourceLocation, Map<ResourceLocation, EquipmentSlotDisplay>>();
        var interfaces = new LinkedHashMap<ResourceLocation, EquipmentSlotDisplay>();
        var errors = new ArrayList<String>();
        manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    try (var reader = entry.getValue().openAsReader()) {
                        var path = entry.getKey().getPath().substring(DIRECTORY.length() + 1);
                        path = path.substring(0, path.length() - 5);
                        if (path.isBlank() || path.endsWith("/") || path.contains("//")) {
                            throw new IllegalArgumentException("Invalid host resource path");
                        }
                        var host = ResourceLocation.fromNamespaceAndPath(entry.getKey().getNamespace(), path);
                        var slots = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
                        definitions.put(host, Map.copyOf(slots));
                    } catch (Exception failure) {
                        errors.add(entry.getKey() + ": " + failure.getMessage());
                    }
                });
        manager.listResources(INTERFACE_DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    try (var reader = entry.getValue().openAsReader()) {
                        String path = entry.getKey().getPath().substring(INTERFACE_DIRECTORY.length() + 1);
                        path = path.substring(0, path.length() - 5);
                        if (path.isBlank() || path.endsWith("/") || path.contains("//")) {
                            throw new IllegalArgumentException("Invalid interface resource path");
                        }
                        var id = ResourceLocation.fromNamespaceAndPath(entry.getKey().getNamespace(), path);
                        interfaces.put(id, EquipmentSlotDisplay.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow());
                    } catch (Exception failure) {
                        errors.add(entry.getKey() + ": " + failure.getMessage());
                    }
                });
        return new Prepared(Map.copyOf(definitions), Map.copyOf(interfaces), List.copyOf(errors));
    }

    @Override protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
        lastErrors = prepared.errors();
        if (lastErrors.isEmpty()) EquipmentStructureUiRegistry.replaceResourceDisplays(prepared.definitions(), prepared.interfaces());
        else EquipmentStructureApiMod.LOGGER.warn("Slot display resources rejected; previous definitions retained: {}", lastErrors);
    }

    @Override public String getName() { return "Equipment API slot display definitions"; }

    record Prepared(Map<ResourceLocation, Map<ResourceLocation, EquipmentSlotDisplay>> definitions,
                    Map<ResourceLocation, EquipmentSlotDisplay> interfaces, List<String> errors) {}
}
