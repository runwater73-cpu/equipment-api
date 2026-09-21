package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.ui.EquipmentTooltips;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Optional;

@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID, value = Dist.CLIENT)
public final class EquipmentTooltipEvents {
    private EquipmentTooltipEvents() {}

    @SubscribeEvent
    public static void onAssemblyTooltipColor(RenderTooltipEvent.Color event) {
        if (!(Minecraft.getInstance().screen instanceof EquipmentAssemblyScreen)) return;
        // Dense detail-panel text must not show through a tooltip laid over it.
        // Keep the supplied colors and border while making the background opaque.
        event.setBackgroundStart(event.getBackgroundStart() | 0xFF000000);
        event.setBackgroundEnd(event.getBackgroundEnd() | 0xFF000000);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!EquipmentTooltipConfig.enabled()) return;
        Optional<Component> key = Optional.empty();
        // Tooltips also run during search-tree construction with no player.
        if (event.getEntity() != null && EquipmentAssemblyKeyEvents.canOpenOnServer()
                && EquipmentTooltipConfig.showOpenKey() && !EquipmentAssemblyKeyMappings.OPEN_ASSEMBLY.isUnbound()) {
            key = Optional.of(EquipmentAssemblyKeyMappings.OPEN_ASSEMBLY.getTranslatedKeyMessage());
        }
        event.getToolTip().addAll(EquipmentTooltips.lines(event.getItemStack(),
                event.getEntity() != null && Screen.hasShiftDown(), key));
    }
}
