package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** NeoForge's mod-list configuration entry; server settings retain its permission boundaries. */
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class EquipmentClientSetup {
    private EquipmentClientSetup() {}
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        ModList.get().getModContainerById(EquipmentStructureApiMod.MOD_ID).orElseThrow()
                .registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
