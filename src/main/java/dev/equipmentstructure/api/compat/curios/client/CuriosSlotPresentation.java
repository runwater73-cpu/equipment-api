package dev.equipmentstructure.api.compat.curios.client;

import dev.equipmentstructure.api.compat.curios.*;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.extensions.ICurioSlotExtension;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.network.client.CPacketToggleRender;
import java.util.List;

/** Slot presentation delegates to Curios extensions. Does not own items, effects or render flags. */
public final class CuriosSlotPresentation {
    private CuriosSlotPresentation() {}
    private static ICurioStacksHandler handler(CuriosSlotKey key) {
        return CuriosApi.getCuriosInventory(Minecraft.getInstance().player).flatMap(inv -> inv.getStacksHandler(key.type())).orElse(null);
    }
    public static ItemStack display(ResourceLocation id, ItemStack item) {
        var key = CuriosSlotKey.parse(id).orElse(null);
        if (key == null) return item;
        var extension = ICurioSlotExtension.from(key.type());
        return extension == ICurioSlotExtension.DEFAULT ? item : extension
                .getDisplayStack(CuriosArmorCompat.context(Minecraft.getInstance().player, key), item.copy());
    }
    public static void tooltip(ResourceLocation id, ItemStack item, List<Component> lines) {
        var key = CuriosSlotKey.parse(id).orElse(null);
        if (key == null) return;
        var mc = Minecraft.getInstance(); var h = handler(key);
        var flag = net.neoforged.neoforge.client.ClientTooltipFlag.of(mc.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL);
        var shown = display(id, item);
        if (!shown.isEmpty()) lines.addAll(shown.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, flag));
        else lines.addAll(ICurioSlotExtension.from(key.type()).getSlotTooltip(CuriosArmorCompat.context(mc.player, key), flag));
        if (key.cosmetic()) lines.add(Component.translatable("curios.cosmetic"));
        if (h != null && key.index() < h.getSlots()) {
            if (key.index() >= h.getActiveStates().size() || !h.getActiveStates().get(key.index()))
                lines.add(text("native_inactive"));
            if (!key.cosmetic() && h.canToggleRendering()) lines.add(text(h.getRenders().get(key.index()) ? "render_on" : "render_off"));
        } else lines.add(text("native_unavailable"));
    }
    public static boolean click(EquipmentAssemblyMenu menu, ResourceLocation id, boolean personal, int button) {
        var key = CuriosSlotKey.parse(id).orElse(null);
        if (key == null || !menu.getCarried().isEmpty()) return false;
        if (button == 2 && Minecraft.getInstance().player.hasInfiniteMaterials()) {
            PacketDistributor.sendToServer(new CuriosSlotClonePayload(menu.containerId, menu.getStateId(), id, personal)); return true;
        }
        if (button == 1 && Screen.hasAltDown()) {
            var h = handler(key);
            if (!key.cosmetic() && h != null && h.canToggleRendering() && key.index() < h.getSlots())
                PacketDistributor.sendToServer(new CPacketToggleRender(key.type(), key.index()));
            return true;
        }
        return false;
    }
    private static Component text(String key) { return Component.translatable("gui.equipment_structure_api.curios." + key); }
}
