package dev.equipmentstructure.api.compat.curios.client;

import dev.equipmentstructure.api.compat.curios.*;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;
import java.util.List;

/** A paged view of existing native slots, inside the assembly menu. Never owns an inventory. */
public final class CuriosBindingPanel {
    private static final int COLUMNS = 8;
    private List<CuriosSlotKey> keys = List.of();
    private int page;
    private int slotTop = 65, rows = 4, pageSize = 32;
    public void tick() {
        var mc = Minecraft.getInstance(); var player = mc.player;
        slotTop = Math.max(65, 27 + mc.font.split(text("binding_description"), 156).size() * mc.font.lineHeight + 8);
        rows = Math.clamp((137 - slotTop) / 18, 1, 4);
        pageSize = COLUMNS * rows;
        keys = player == null ? List.of() : CuriosBindingActions.keys(player);
        page = Math.min(page, pages() - 1);
    }
    private int pages() { return Math.max(1, (keys.size() + pageSize - 1) / pageSize); }
    private ItemStack shown(CuriosSlotKey key) {
        var player = Minecraft.getInstance().player;
        return CuriosApi.getCuriosInventory(player).flatMap(inv -> inv.getStacksHandler(key.type()))
                .filter(h -> key.index() < h.getSlots()).map(h -> h.getStacks().getStackInSlot(key.index())).orElse(ItemStack.EMPTY);
    }
    public void render(GuiGraphics g, int left, int top) {
        var mc = Minecraft.getInstance(); var font = mc.font;
        g.drawString(font, text("binding_title"), left + 114, top + 12, 0xFFD3A65C, false);
        g.drawWordWrap(font, text("binding_description"), left + 114, top + 27, 156, 0xFFAAAAAA);
        for (int n = 0; n < pageSize && n + page * pageSize < keys.size(); n++) {
            var key = keys.get(n + page * pageSize); var item = shown(key);
            int x = left + 120 + n % COLUMNS * 18, y = top + slotTop + n / COLUMNS * 18;
            g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF101316);
            g.renderOutline(x - 1, y - 1, 18, 18, PlayerBoundCurios.bound(item, mc.player) ? 0xFFD3A65C : 0xFF5D6368);
            var display = CuriosSlotPresentation.display(key.slotId(), item);
            if (!display.isEmpty()) g.renderItem(display, x, y);
            else CuriosApi.getSlot(key.type(), mc.level).ifPresent(type -> g.blit(x, y, 0, 16, 16,
                    mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(type.getIcon())));
        }
        if (keys.isEmpty()) g.drawWordWrap(font, text("no_bindings"), left + 120, top + slotTop, 144, 0xFFBBBBBB);
        if (pages() > 1) {
            g.drawString(font, "<", left + 120, top + 145, 0xFFFFFFFF, false);
            g.drawCenteredString(font, (page + 1) + " / " + pages(), left + 192, top + 145, 0xFFBBBBBB);
            g.drawString(font, ">", left + 257, top + 145, 0xFFFFFFFF, false);
        }
    }
    public boolean tooltip(GuiGraphics g, int left, int top, int mx, int my) {
        var key = keyAt(mx - left, my - top);
        if (key == null) return false;
        var mc = Minecraft.getInstance(); var item = shown(key);
        var lines = new java.util.ArrayList<Component>();
        lines.add(Component.translatable("curios.identifier." + key.type()).append(" #" + (key.index() + 1)));
        CuriosSlotPresentation.tooltip(key.slotId(), item, lines);
        lines.add(text(item.isEmpty() ? "binding_hint" : "player_bound"));
        g.renderComponentTooltip(mc.font, lines, mx, my); return true;
    }
    private CuriosSlotKey keyAt(double mx, double my) {
        if (mx < 119 || mx >= 263 || my < slotTop - 1 || my >= slotTop - 1 + rows * 18) return null;
        int index = page * pageSize + ((int) my - slotTop + 1) / 18 * COLUMNS + ((int) mx - 119) / 18;
        return index < keys.size() ? keys.get(index) : null;
    }
    public boolean click(EquipmentAssemblyMenu menu, double x, double y, int button, boolean quick) {
        if (x < 104 || x >= 280 || y < 2 || y >= 164) return false;
        var key = keyAt(x, y);
        if (key != null && CuriosSlotPresentation.click(menu, key.slotId(), true, button)) return true;
        if (button != 0 && button != 1) return true;
        if (key != null) PacketDistributor.sendToServer(new CuriosBindingActionPayload(menu.containerId, menu.getStateId(), key, quick, menu.getCarried()));
        else if (y >= 140 && y < 160 && pages() > 1) {
            if (x < 140) page = Math.max(0, page - 1);
            else if (x >= 240) page = Math.min(pages() - 1, page + 1);
        }
        return true;
    }
    public boolean scroll(double x, double y, double delta) {
        if (x < 104 || x >= 280 || y < 2 || y >= 164) return false;
        page = net.minecraft.util.Mth.clamp(page - (int) Math.signum(delta), 0, pages() - 1); return true;
    }
    private static Component text(String key) { return Component.translatable("gui.equipment_structure_api.curios." + key); }
}
