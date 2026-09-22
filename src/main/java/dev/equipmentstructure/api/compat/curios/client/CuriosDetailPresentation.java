package dev.equipmentstructure.api.compat.curios.client;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.client.EquipmentAssemblyDisplaySnapshot.*;
import dev.equipmentstructure.api.compat.curios.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;
import net.neoforged.neoforge.common.util.AttributeUtil;
import net.neoforged.neoforge.client.event.GatherSkippedAttributeTooltipsEvent;
import top.theillusivec4.curios.api.CuriosApi;
import java.util.*;

/** Read-only presentation, captured with the screen's once-per-tick snapshot. Never applies effects. */
public final class CuriosDetailPresentation {
    private CuriosDetailPresentation() {}
    public static Component typeName(CuriosSlotKey key) {
        return Component.translatableWithFallback("curios.identifier." + key.type(), key.type());
    }
    public static void appendComponent(ItemStack equipment, EquipmentSlotDefinition definition, ItemStack item, List<InfoLine> lines) {
        var mc = Minecraft.getInstance();
        var key = CuriosSlotKey.parse(definition.id()).orElseThrow();
        lines.add(new InfoLine(text("slot_type", typeName(key)), 0xFFAAAAAA));
        lines.add(new InfoLine(text(key.cosmetic() ? "cosmetic_detail" : "functional_detail"), 0xFFAAAAAA));
        if (mc.player != null) lines.add(new InfoLine(text(EquipmentSlotItemAdapters.canRemove(equipment, definition.id(), mc.player)
                ? "native_removable" : "native_locked"), 0xFFAAAAAA));
        lines.add(new InfoLine(text("original_description"), 0xFFD3A65C));
        var tooltip = new ArrayList<Component>();
        CuriosSlotPresentation.tooltip(definition.id(), item, tooltip);
        // The item name already heads the panel; retain the addon's colors, descriptions and attributes.
        for (int i = item.isEmpty() ? 0 : 1; i < tooltip.size(); i++)
            if (!tooltip.get(i).getString().isBlank()) lines.add(new InfoLine(tooltip.get(i), 0xFFAAAAAA));
    }
    public static void appendAttributes(Map<net.minecraft.resources.ResourceLocation, ComponentInfoSnapshot> components,
                                        List<EquipmentSlotDefinition> definitions, List<EquipmentStatRow> rows) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        boolean heading = false;
        for (var definition : definitions) {
            var key = CuriosSlotKey.parse(definition.id()).orElse(null);
            var component = components.get(definition.id());
            if (key == null || key.cosmetic() || component == null || component.installed().isEmpty()) continue;
            if (!heading) {
                rows.add(full(text("accessory_attributes"), 0xFFD3A65C));
                rows.add(full(text("attribute_context"), 0xFF999999));
                heading = true;
            }
            rows.add(full(component.itemName().copy().append(" · ").append(typeName(key)).append(" #" + (key.index() + 1)), 0xFFD3A65C));
            var h = CuriosApi.getCuriosInventory(mc.player).flatMap(inv -> inv.getStacksHandler(key.type())).orElse(null);
            if (h == null || key.index() >= h.getSlots()) rows.add(full(text("native_unavailable"), 0xFFAAAAAA));
            else if (key.index() >= h.getActiveStates().size() || !h.getActiveStates().get(key.index()))
                rows.add(full(text("native_inactive"), 0xFFAAAAAA));
            var item = component.itemStack();
            var context = Item.TooltipContext.of(mc.level);
            var flag = net.neoforged.neoforge.client.ClientTooltipFlag.of(TooltipFlag.NORMAL);
            var attributeContext = AttributeTooltipContext.of(mc.player, context, flag);
            var skipped = NeoForge.EVENT_BUS.post(new GatherSkippedAttributeTooltipsEvent(item, attributeContext));
            List<Component> text = new ArrayList<>();
            if (!skipped.isSkippingAll()) {
                // Use the installed type and index, not the tooltip's list of every possible slot.
                var slot = CuriosArmorCompat.context(mc.player, key);
                var modifiers = com.google.common.collect.LinkedHashMultimap.create(
                        CuriosApi.getAttributeModifiers(slot, CuriosApi.getSlotId(slot), item));
                modifiers.values().removeIf(modifier -> skipped.isSkipped(modifier.id()));
                AttributeUtil.applyTextFor(item, text::add, modifiers, attributeContext);
                var curio = CuriosApi.getCurio(item).orElse(null);
                if (curio != null) text = curio.getAttributesTooltip(text, context);
            }
            if (text.isEmpty()) rows.add(full(text("no_numeric_attributes"), 0xFF999999));
            else text.forEach(line -> rows.add(full(line, 0xFFAAAAAA)));
        }
    }
    private static EquipmentStatRow full(Component line, int color) { return new EquipmentStatRow(line, Component.empty(), color); }
    private static MutableComponent text(String key, Object... args) { return Component.translatable("gui.equipment_structure_api.curios." + key, args); }
}
