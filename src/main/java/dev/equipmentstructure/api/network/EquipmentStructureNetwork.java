package dev.equipmentstructure.api.network;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.command.EquipmentStructureCommands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;

/** Registration and server handling for Equipment Structure API payloads. */
public final class EquipmentStructureNetwork {

    private EquipmentStructureNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(EquipmentStructureApiMod.NETWORK_VERSION);
        registrar.playToServer(SpaceActionPayload.TYPE, SpaceActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    boolean applied = false;
                    if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof EquipmentAssemblyMenu menu
                            && menu.containerId == payload.containerId()) {
                        applied = menu.applySpaceAction(payload, player);
                        // Cursor-only vanilla packets do not advance the client menu revision.
                        // Send one complete snapshot before acknowledging either outcome.
                        menu.broadcastFullState();
                        if (!payload.definitions().equals(dev.equipmentstructure.api.grid.GridDefinitions.registered().fingerprint()))
                            context.reply(new GridDefinitionsPayload(dev.equipmentstructure.api.grid.GridDefinitions.registered()));
                    }
                    context.reply(new GridActionResultPayload(payload.containerId(), payload.requestId(), applied));
                }));
        registrar.playToClient(GridDefinitionsPayload.TYPE, GridDefinitionsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        dev.equipmentstructure.api.grid.GridDefinitionSync.receive(payload.definitions())));
        registrar.playToServer(GridActionPayload.TYPE, GridActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    boolean applied = false;
                    if (context.player() instanceof ServerPlayer player
                            && player.containerMenu instanceof EquipmentAssemblyMenu menu
                            && menu.containerId == payload.containerId()) {
                        applied = menu.applyGridAction(payload, player);
                        // Cursor-only vanilla packets do not advance the client menu revision.
                        // Send one complete snapshot before acknowledging either outcome.
                        menu.broadcastFullState();
                        if (!payload.definitions().equals(dev.equipmentstructure.api.grid.GridDefinitions.registered().fingerprint()))
                            context.reply(new GridDefinitionsPayload(dev.equipmentstructure.api.grid.GridDefinitions.registered()));
                    }
                    context.reply(new GridActionResultPayload(payload.containerId(), payload.requestId(), applied));
                }));
        registrar.playToClient(GridActionResultPayload.TYPE, GridActionResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof EquipmentAssemblyMenu menu
                            && menu.containerId == payload.containerId()) menu.receiveGridResult(payload);
                }));
        registrar.playToClient(ComponentAttributesPayload.TYPE, ComponentAttributesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        dev.equipmentstructure.api.attribute.EquipmentAttributeData.receive(
                                payload.definitions(), context.player().registryAccess())));
        registrar.playToClient(AssemblySelectionPayload.TYPE, AssemblySelectionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        dev.equipmentstructure.api.client.EquipmentAssemblyScreen.receiveSelection(payload)));
        registrar.playToServer(ClickInterfacePayload.TYPE, ClickInterfacePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && player.containerMenu instanceof EquipmentAssemblyMenu menu
                            && menu.containerId == payload.containerId()) {
                        menu.clickInterface(payload.interfaceId(), payload.button(), payload.action(), player);
                        menu.broadcastChanges();
                    }
                }));
        registrar.playToServer(OpenAssemblyPayload.TYPE, OpenAssemblyPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        if (context.player() instanceof ServerPlayer player) {
                            EquipmentStructureCommands.openForPlayer(player);
                        }
                    });
                });
        registrar.playToServer(SelectInterfacePayload.TYPE, SelectInterfacePayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        if (context.player() instanceof ServerPlayer player
                                && player.containerMenu instanceof EquipmentAssemblyMenu menu
                                && menu.containerId == payload.containerId()) {
                            menu.selectInterface(payload.interfaceId());
                        }
                    });
                });
        registrar.playToServer(ClearInterfacePayload.TYPE, ClearInterfacePayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        if (context.player() instanceof ServerPlayer player
                                && player.containerMenu instanceof EquipmentAssemblyMenu menu
                                && menu.containerId == payload.containerId()) {
                            menu.selectInterface((net.minecraft.resources.ResourceLocation) null);
                        }
                    });
                });
        registrar.playToServer(SetAppearancePosePayload.TYPE, SetAppearancePosePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && player.containerMenu instanceof EquipmentAssemblyMenu menu
                            && menu.containerId == payload.containerId()) {
                        boolean saved = menu.applyAppearancePresentation(payload.slotId(), payload.expectedEquipment(),
                                payload.pose(), payload.visibility(), player);
                        menu.broadcastChanges();
                        context.reply(new AppearancePlacementStatusPayload(menu.containerId, payload.slotId(),
                                saved ? AppearancePlacementStatusPayload.SAVED : AppearancePlacementStatusPayload.REJECTED));
                    }
                }));
        registrar.playToClient(AppearancePlacementStatusPayload.TYPE, AppearancePlacementStatusPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        dev.equipmentstructure.api.client.EquipmentAppearancePlacementScreen.receive(payload)));
        registrar.playToServer(SetAppearanceLayoutPayload.TYPE, SetAppearanceLayoutPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && player.containerMenu instanceof EquipmentAssemblyMenu menu
                            && menu.containerId == payload.containerId()) {
                        boolean saved = menu.applyAppearanceLayout(payload.expectedEquipment(), payload.edits(),
                                payload.originalVisible(), player);
                        menu.broadcastChanges();
                        context.reply(new AppearancePlacementStatusPayload(menu.containerId, payload.editorSlot(),
                                saved ? AppearancePlacementStatusPayload.SAVED : AppearancePlacementStatusPayload.REJECTED));
                    }
                }));
    }
}
