package dev.equipmentstructure.api.grid;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.EquipmentStructureRegistries;
import net.minecraft.core.RegistryAccess;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import dev.equipmentstructure.api.network.GridDefinitionsPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Login/reload snapshot, separate from local registrations even in an integrated server. */
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class GridDefinitionSync {
    private static volatile GridDefinitions client;
    private GridDefinitionSync() {}

    public static GridDefinitions current() {
        GridDefinitions received = client;
        return EffectiveSide.get().isServer() || received == null ? GridDefinitions.registered() : received;
    }
    public static void receive(GridDefinitions definitions) { client = java.util.Objects.requireNonNull(definitions); }
    public static void clearClient() { client = null; }

    /** Resolve every loaded template before publishing a login or menu snapshot. */
    public static void loadTemplates(RegistryAccess registries) {
        var boards = new java.util.HashMap<net.minecraft.resources.ResourceLocation, GridBoard>();
        registries.registry(EquipmentStructureRegistries.HOST_DEFINITION).ifPresent(registry ->
                registry.entrySet().forEach(entry -> {
                    var definition = entry.getValue();
                    if (!entry.getKey().location().equals(definition.id())) {
                        throw new IllegalArgumentException("Host template ID does not match registry key: " + entry.getKey());
                    }
                    definition.grid().ifPresent(board -> boards.put(definition.id(), board));
                }));
        EquipmentGridRegistry.replaceTemplates(boards);
    }

    @SubscribeEvent
    public static void starting(ServerAboutToStartEvent event) {
        loadTemplates(event.getServer().registryAccess());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        EquipmentGridRegistry.replaceTemplates(java.util.Map.of());
    }

    @SubscribeEvent
    public static void sync(OnDatapackSyncEvent event) {
        loadTemplates(event.getPlayerList().getServer().registryAccess());
        var payload = new GridDefinitionsPayload(GridDefinitions.registered());
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }
}
