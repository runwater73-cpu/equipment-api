package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.attribute.EquipmentAttributeData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** A previous server's definitions must not be visible on the next connection. */
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID, value = Dist.CLIENT)
public final class EquipmentAttributeClientEvents {
    private EquipmentAttributeClientEvents() {}
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        EquipmentAttributeData.clearClient();
        dev.equipmentstructure.api.grid.GridDefinitionSync.clearClient();
    }
}
