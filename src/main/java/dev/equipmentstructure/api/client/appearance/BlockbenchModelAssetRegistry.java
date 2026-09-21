package dev.equipmentstructure.api.client.appearance;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Convenience bridge for content authors. A registration connects one stable appearance asset ID
 * to one Blockbench resource and renderer; resource reloads rebuild the prepared set atomically.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BlockbenchModelAssetRegistry {
    private static final Map<ResourceLocation, Registration> REGISTRATIONS = new ConcurrentHashMap<>();
    private static volatile Map<ResourceLocation, BlockbenchModelDefinition> PREPARED = Map.of();

    private BlockbenchModelAssetRegistry() {}

    /**
     * Register during RegisterClientReloadListenersEvent, before the first resource preparation.
     * Later registrations take effect on the next reload. No resource IO runs in this method.
     */
    public static void register(ResourceLocation asset, ResourceLocation modelResource,
                                BlockbenchModelAssetRenderer renderer) {
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(modelResource, "modelResource");
        Objects.requireNonNull(renderer, "renderer");
        var registration = new Registration(modelResource, renderer);
        var previous = REGISTRATIONS.putIfAbsent(asset, registration);
        if (previous != null && !previous.equals(registration)) {
            throw new IllegalStateException("Blockbench asset already registered: " + asset);
        }
        // An identical registration may already belong to the current prepared snapshot.
        if (PREPARED.containsKey(asset)) install(asset, registration, PREPARED.get(asset));
    }

    public static boolean unregister(ResourceLocation asset) {
        Objects.requireNonNull(asset, "asset");
        boolean removed = REGISTRATIONS.remove(asset) != null;
        PREPARED = without(PREPARED, asset);
        AppearanceModelAssetRegistry.unregister(asset);
        return removed;
    }

    /** Returns assets with a successfully decoded model in the active resource generation. */
    public static Set<ResourceLocation> preparedAssets() {
        return Set.copyOf(PREPARED.keySet());
    }

    public static Optional<BlockbenchModelDefinition> prepared(ResourceLocation asset) {
        Objects.requireNonNull(asset, "asset");
        return Optional.ofNullable(PREPARED.get(asset));
    }

    public static void clear() {
        for (ResourceLocation asset : REGISTRATIONS.keySet()) AppearanceModelAssetRegistry.unregister(asset);
        REGISTRATIONS.clear();
        PREPARED = Map.of();
    }

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new ReloadListener());
    }

    private static void install(ResourceLocation asset, Registration registration, BlockbenchModelDefinition model) {
        AppearanceModelAssetRegistry.unregister(asset);
        AppearanceModelAssetRegistry.register(asset, context -> registration.renderer().render(context, model));
    }

    private static Map<ResourceLocation, BlockbenchModelDefinition> without(
            Map<ResourceLocation, BlockbenchModelDefinition> source, ResourceLocation removed) {
        if (!source.containsKey(removed)) return source;
        var copy = new java.util.HashMap<>(source);
        copy.remove(removed);
        return Map.copyOf(copy);
    }

    private record Registration(ResourceLocation modelResource, BlockbenchModelAssetRenderer renderer) {}

    private static final class ReloadListener extends SimplePreparableReloadListener<Prepared> {
        @Override
        protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
            var models = new java.util.HashMap<ResourceLocation, BlockbenchModelDefinition>();
            for (var entry : REGISTRATIONS.entrySet()) {
                var decoded = BlockbenchModelLoader.load(manager, entry.getValue().modelResource());
                if (decoded.isEmpty()) {
                    EquipmentStructureApiMod.LOGGER.warn("Blockbench appearance asset {} could not decode model {}",
                            entry.getKey(), entry.getValue().modelResource());
                    continue;
                }
                var model = decoded.get();
                if (!BlockbenchModelLoader.hasDrawableFace(manager, model)) {
                    EquipmentStructureApiMod.LOGGER.warn("Blockbench appearance asset {} has no drawable face or present texture; keeping original appearance",
                            entry.getKey());
                    continue;
                }
                models.put(entry.getKey(), model);
            }
            return new Prepared(Map.copyOf(models));
        }

        @Override
        protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
            var old = PREPARED;
            PREPARED = prepared.models();
            BlockbenchTextureResolver.invalidateGeneration(AppearanceResourceReloadListener.generation());
            for (ResourceLocation asset : old.keySet()) {
                if (!PREPARED.containsKey(asset)) AppearanceModelAssetRegistry.unregister(asset);
            }
            for (var entry : REGISTRATIONS.entrySet()) {
                var model = PREPARED.get(entry.getKey());
                if (model == null) {
                    AppearanceModelAssetRegistry.unregister(entry.getKey());
                } else {
                    install(entry.getKey(), entry.getValue(), model);
                }
            }
            EquipmentStructureApiMod.LOGGER.debug("Prepared {} Blockbench appearance assets", PREPARED.size());
        }

        @Override
        public String getName() {
            return "Equipment Structure API Blockbench appearance assets";
        }
    }

    private record Prepared(Map<ResourceLocation, BlockbenchModelDefinition> models) {
        private Prepared {
            models = Map.copyOf(models);
        }
    }
}
