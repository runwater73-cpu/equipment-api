package dev.equipmentstructure.api.attribute;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerResources;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Internal data storage. Server resources are committed by Minecraft, client data by a server packet. */
public final class EquipmentAttributeData {
    private static final Map<ReloadableServerResources, Snapshot> PREPARED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile Snapshot client = Snapshot.EMPTY;

    private EquipmentAttributeData() {}

    static void stage(ReloadableServerResources owner, Snapshot snapshot) { PREPARED.put(owner, snapshot); }

    static Snapshot serverSnapshot(MinecraftServer server) {
        return server == null ? Snapshot.EMPTY : PREPARED.getOrDefault(
                server.getServerResources().managers(), Snapshot.EMPTY);
    }

    static Snapshot current() {
        return EffectiveSide.get().isServer() ? serverSnapshot(ServerLifecycleHooks.getCurrentServer()) : client;
    }

    /** Committed definitions on the calling logical game thread; includes explicit empty overrides. */
    public static Map<ResourceLocation, EquipmentAttributeDefinition> definitions() { return current().definitions(); }

    /** Internal network entry. Compiles the entire packet before swapping the client view. */
    public static void receive(Map<ResourceLocation, EquipmentAttributeDefinition> definitions, RegistryAccess registries) {
        client = Snapshot.compile(definitions, registries);
    }

    public static void clearClient() { client = Snapshot.EMPTY; }

    static void clearServer() { PREPARED.clear(); }

    record Snapshot(Map<ResourceLocation, EquipmentAttributeDefinition> definitions,
                    Map<ResourceLocation, List<EquipmentAttributeContribution>> providers) {
        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of());
        Snapshot {
            definitions = Map.copyOf(definitions);
            providers = Map.copyOf(providers);
        }

        static Snapshot compile(Map<ResourceLocation, EquipmentAttributeDefinition> definitions, RegistryAccess registries) {
            Map<ResourceLocation, List<EquipmentAttributeContribution>> providers = new LinkedHashMap<>();
            definitions.forEach((id, definition) -> {
                try { providers.put(id, definition.contributions(registries)); }
                catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid component attributes for " + id + ": " + ex.getMessage(), ex); }
            });
            return new Snapshot(definitions, providers);
        }
    }
}
