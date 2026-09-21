package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.equipmentstructure.api.network.OpenAssemblyPayload;

/** Sends the generic API menu request to the server when the key is pressed. */
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID, value = Dist.CLIENT)
public final class EquipmentAssemblyKeyEvents {

    private EquipmentAssemblyKeyEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean requested = false;
        while (EquipmentAssemblyKeyMappings.OPEN_ASSEMBLY.consumeClick()) {
            requested = true;
        }
        // Screen key input is handled by the screen itself. A queued world key
        // must never open a second menu over chat, controls or another container.
        if (requested && minecraft.screen == null && minecraft.player != null && canOpenOnServer()) {
            PacketDistributor.sendToServer(new OpenAssemblyPayload());
        }
    }

    public static boolean canOpenOnServer() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(
                connection, OpenAssemblyPayload.TYPE.id());
    }

    static void clearQueuedClicks() {
        while (EquipmentAssemblyKeyMappings.OPEN_ASSEMBLY.consumeClick()) { }
    }
}
