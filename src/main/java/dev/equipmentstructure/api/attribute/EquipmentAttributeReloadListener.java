package dev.equipmentstructure.api.attribute;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.network.ComponentAttributesPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.LinkedHashMap;

/** Rejects invalid reloads; prepared data becomes visible only when Minecraft adopts those resources. */
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class EquipmentAttributeReloadListener extends SimplePreparableReloadListener<EquipmentAttributeData.Snapshot> {
    public static final String DIRECTORY = "equipment_structure_api/component_attributes";
    private final ReloadableServerResources owner;
    private final RegistryAccess registries;
    private final ICondition.IContext conditions;

    private EquipmentAttributeReloadListener(ReloadableServerResources owner, RegistryAccess registries,
                                            ICondition.IContext conditions) {
        this.owner = owner;
        this.registries = registries;
        this.conditions = conditions;
    }

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new EquipmentAttributeReloadListener(event.getServerResources(), event.getRegistryAccess(),
                event.getConditionContext()));
    }

    @Override
    protected EquipmentAttributeData.Snapshot prepare(ResourceManager manager, ProfilerFiller profiler) {
        var definitions = new LinkedHashMap<ResourceLocation, EquipmentAttributeDefinition>();
        var ops = new ConditionalOps<>(RegistryOps.create(JsonOps.INSTANCE, registries), conditions);
        manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    var resource = entry.getKey();
                    var component = ResourceLocation.fromNamespaceAndPath(resource.getNamespace(),
                            resource.getPath().substring(DIRECTORY.length() + 1, resource.getPath().length() - 5));
                    try (var reader = entry.getValue().openAsReader()) {
                        ICondition.getConditionally(EquipmentAttributeDefinition.CODEC, ops, JsonParser.parseReader(reader))
                                .ifPresent(definition -> definitions.put(component, definition));
                    } catch (Exception ex) {
                        throw new IllegalArgumentException("Invalid component attribute resource " + resource
                                + " from pack " + entry.getValue().sourcePackId() + ": " + ex.getMessage(), ex);
                    }
                });
        var snapshot = EquipmentAttributeData.Snapshot.compile(definitions, registries);
        // Reject oversize data during reload instead of disconnecting every joining player.
        var buffer = Unpooled.buffer();
        try {
            ComponentAttributesPayload.STREAM_CODEC.encode(buffer, new ComponentAttributesPayload(definitions));
            if (buffer.readableBytes() > 900_000) throw new IllegalArgumentException("Component attribute snapshot exceeds 900000 encoded bytes");
            // NBT heap accounting is stricter than byte length for many tiny records.
            // Check the real receiver's allocation budget here as well.
            ComponentAttributesPayload.STREAM_CODEC.decode(buffer);
        } finally { buffer.release(); }
        return snapshot;
    }

    @Override
    protected void apply(EquipmentAttributeData.Snapshot prepared, ResourceManager manager, ProfilerFiller profiler) {
        EquipmentAttributeData.stage(owner, prepared);
    }

    @SubscribeEvent
    public static void sync(OnDatapackSyncEvent event) {
        var server = event.getPlayerList().getServer();
        if (event.getPlayer() == null) EquipmentAttributeRefresh.refresh(server);
        var payload = new ComponentAttributesPayload(EquipmentAttributeData.serverSnapshot(server).definitions());
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { EquipmentAttributeData.clearServer(); }
}
