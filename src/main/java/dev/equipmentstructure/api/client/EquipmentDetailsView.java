package dev.equipmentstructure.api.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.equipmentstructure.api.client.EquipmentDetailsLayout.*;

/** Read-only detail pages inside the assembly screen; owns no menu, ItemStack or network action. */
final class EquipmentDetailsView {
    enum Page { HOME, ATTRIBUTES, COMPONENTS }
    private Page page = Page.HOME;
    private ResourceLocation componentId;
    private final EquipmentDetailsScroll textScroll = new EquipmentDetailsScroll();
    private final EquipmentDetailsScroll listScroll = new EquipmentDetailsScroll();
    private List<EquipmentAssemblyDisplaySnapshot.ComponentInfoSnapshot> installed = List.of();
    private List<?> documentKey;
    private Language documentLanguage;
    private int documentWidth;
    private int documentHeight;
    private List<TextLine> document = List.of();

    Page page() { return page; }
    boolean active() { return page != Page.HOME; }

    void open(Page next, ResourceLocation selected) {
        if (page != next) { textScroll.reset(); listScroll.reset(); documentKey = null; }
        page = next;
        if (next == Page.COMPONENTS && selected != null && !selected.equals(componentId)) {
            componentId = selected;
            textScroll.reset();
        }
    }

    void render(GuiGraphics graphics, Font font, int left, int top, int mouseX, int mouseY,
                EquipmentAssemblyDisplaySnapshot snapshot) {
        refresh(snapshot);
        EquipmentAssemblyPanelRenderer.render(graphics, left, top, PANEL.panel());
        tab(graphics, font, left, top, mouseX, mouseY, HOME, "back", false, snapshot);
        tab(graphics, font, left, top, mouseX, mouseY, ATTRIBUTES, "attributes", page == Page.ATTRIBUTES, snapshot);
        tab(graphics, font, left, top, mouseX, mouseY, COMPONENTS, "components", page == Page.COMPONENTS, snapshot);
        graphics.renderItem(snapshot.equipment(), left + 18, top + 47);
        drawShort(graphics, font, snapshot.equipmentName(), left + 44, top + 45, 334, 0xFFFFFFFF);
        drawShort(graphics, font, text(page == Page.ATTRIBUTES ? "attributes_subtitle" : "components_subtitle"),
                left + 44, top + 59, 334, 0xFF999999);
        if (page == Page.ATTRIBUTES) {
            drawShort(graphics, font, Component.translatable(snapshot.uiDefinition().texts().stats()),
                    left + 16, top + 70, 364, snapshot.uiDefinition().accentColor());
            prepareDocument(font, snapshot.equipmentStats(), ATTRIBUTE_TEXT.width(), true);
            renderDocument(graphics, font, left, top, ATTRIBUTE_TEXT);
            if (snapshot.equipmentStats().isEmpty()) {
                drawShort(graphics, font, text("no_attributes"), left + 16, top + 88, 360, 0xFF999999);
            }
        } else {
            renderComponents(graphics, font, left, top, mouseX, mouseY, snapshot);
        }
        drawShort(graphics, font, text("footer"), left + 16, top + 239, 364, 0xFF888888);
    }

    /** Definition order is stable; selections follow IDs when other mods update an open equipment. */
    private void refresh(EquipmentAssemblyDisplaySnapshot snapshot) {
        installed = snapshot.definitions().stream().map(value -> snapshot.componentInfo().get(value.id()))
                .filter(Objects::nonNull).filter(value -> value.installed().isPresent()).toList();
        if (installed.stream().noneMatch(value -> value.definition().id().equals(componentId))) {
            ResourceLocation next = installed.isEmpty() ? null : installed.getFirst().definition().id();
            if (!Objects.equals(componentId, next)) {
                componentId = next;
                // Component selection must not reset an unrelated attributes document.
                if (page == Page.COMPONENTS) textScroll.reset();
            }
        }
        listScroll.measure(installed.size() * COMPONENT_ROW_HEIGHT, COMPONENT_LIST.height());
    }

    private void renderComponents(GuiGraphics graphics, Font font, int left, int top, int mouseX, int mouseY,
                                  EquipmentAssemblyDisplaySnapshot snapshot) {
        drawShort(graphics, font, text("installed_list"), left + 14, top + 70, 112, 0xFFB8B8B8);
        if (installed.isEmpty()) {
            drawShort(graphics, font, text("no_components"), left + 144, top + 88, 236, 0xFF999999);
            return;
        }
        clip(graphics, left, top, COMPONENT_LIST);
        for (int index = 0; index < installed.size(); index++) {
            var value = installed.get(index);
            int y = COMPONENT_LIST.y() + index * COMPONENT_ROW_HEIGHT - listScroll.offset();
            if (y + COMPONENT_ROW_HEIGHT <= COMPONENT_LIST.y() || y >= COMPONENT_LIST.bottom()) continue;
            boolean selected = value.definition().id().equals(componentId);
            boolean hovered = COMPONENT_LIST.contains(mouseX - left, mouseY - top)
                    && mouseY - top >= y && mouseY - top < y + COMPONENT_ROW_HEIGHT;
            if (selected || hovered) graphics.fill(left + COMPONENT_LIST.x(), top + y,
                    left + COMPONENT_LIST.right() - 4, top + y + COMPONENT_ROW_HEIGHT - 2,
                    selected ? 0xFF272727 : 0xFF1C1C1C);
            if (selected) graphics.fill(left + COMPONENT_LIST.x(), top + y,
                    left + COMPONENT_LIST.x() + 1, top + y + COMPONENT_ROW_HEIGHT - 2, snapshot.uiDefinition().accentColor());
            graphics.renderItem(value.itemStack(), left + 18, top + y + 5);
            drawShort(graphics, font, name(value, snapshot), left + 38, top + y + 4, 80, 0xFFCCCCCC);
            drawShort(graphics, font, snapshot.interfaceName(value.definition().id()), left + 38, top + y + 16, 80, 0xFF888888);
        }
        graphics.disableScissor();
        scrollBar(graphics, left, top, COMPONENT_LIST, listScroll);
        var selected = installed.stream().filter(value -> value.definition().id().equals(componentId)).findFirst().orElseThrow();
        graphics.renderItem(selected.itemStack(), left + 145, top + 85);
        drawShort(graphics, font, name(selected, snapshot), left + 169, top + 83, 211, 0xFFFFFFFF);
        drawShort(graphics, font, snapshot.interfaceName(componentId), left + 169, top + 98, 211, 0xFF999999);
        prepareDocument(font, selected.lines(), COMPONENT_TEXT.width(), false);
        renderDocument(graphics, font, left, top, COMPONENT_TEXT);
        // Full translated names remain available even when compact headers need truncation.
        if (new Rect(144, 80, 236, 27).contains(mouseX - left, mouseY - top)) {
            graphics.renderTooltip(font, name(selected, snapshot), mouseX, mouseY);
        }
    }

    private static Component name(EquipmentAssemblyDisplaySnapshot.ComponentInfoSnapshot value,
                                  EquipmentAssemblyDisplaySnapshot snapshot) {
        return value.itemName().getString().isEmpty()
                ? Component.translatable(snapshot.uiDefinition().texts().unknownComponent()) : value.itemName();
    }

    /** Wrap only when text/locale/width changes; scrolling reuses the measured document. */
    private void prepareDocument(Font font, List<?> rows, int width, boolean attributes) {
        if (rows.equals(documentKey) && width == documentWidth && documentLanguage == Language.getInstance()) return;
        documentKey = List.copyOf(rows);
        documentWidth = width;
        documentLanguage = Language.getInstance();
        List<TextLine> lines = new ArrayList<>();
        int y = 0;
        for (Object value : rows) {
            if (attributes) {
                var row = (EquipmentAssemblyDisplaySnapshot.EquipmentStatRow) value;
                var labels = font.split(row.label(), 136);
                var values = font.split(row.value(), width - 152);
                for (int index = 0; index < labels.size(); index++) lines.add(new TextLine(0, y + index * 12, labels.get(index), 0xFFAAAAAA));
                for (int index = 0; index < values.size(); index++) lines.add(new TextLine(148, y + index * 12, values.get(index), row.color()));
                y += Math.max(1, Math.max(labels.size(), values.size())) * 12 + 8;
            } else {
                var row = (EquipmentAssemblyDisplaySnapshot.InfoLine) value;
                for (var line : font.split(row.text(), width - 6)) {
                    lines.add(new TextLine(0, y, line, row.color()));
                    y += 12;
                }
                y += 6;
            }
        }
        document = List.copyOf(lines);
        documentHeight = y;
    }

    private void renderDocument(GuiGraphics graphics, Font font, int left, int top, Rect viewport) {
        textScroll.measure(documentHeight, viewport.height());
        clip(graphics, left, top, viewport);
        for (TextLine line : document) {
            int y = viewport.y() + line.y() - textScroll.offset();
            if (y + font.lineHeight > viewport.y() && y < viewport.bottom())
                graphics.drawString(font, line.text(), left + viewport.x() + line.x(), top + y, line.color(), false);
        }
        graphics.disableScissor();
        scrollBar(graphics, left, top, viewport, textScroll);
    }

    private static void clip(GuiGraphics graphics, int left, int top, Rect rect) {
        graphics.enableScissor(left + rect.x(), top + rect.y(), left + rect.right(), top + rect.bottom());
    }

    private static void scrollBar(GuiGraphics graphics, int left, int top, Rect rect, EquipmentDetailsScroll scroll) {
        if (scroll.maximum() == 0) return;
        int thumb = Math.max(12, rect.height() * rect.height() / (rect.height() + scroll.maximum()));
        int y = top + rect.y() + (int) ((long) scroll.offset() * (rect.height() - thumb) / scroll.maximum());
        graphics.fill(left + rect.right() - 2, top + rect.y(), left + rect.right(), top + rect.bottom(), 0xFF333333);
        graphics.fill(left + rect.right() - 2, y, left + rect.right(), y + thumb, 0xFFAAAAAA);
    }

    private void tab(GuiGraphics graphics, Font font, int left, int top, int mouseX, int mouseY,
                     Rect rect, String key, boolean selected, EquipmentAssemblyDisplaySnapshot snapshot) {
        EquipmentAssemblyPanelRenderer.render(graphics, left, top, rect.panel());
        int color = selected || rect.contains(mouseX - left, mouseY - top) ? snapshot.uiDefinition().accentColor() : 0xFF888888;
        drawShort(graphics, font, text(key), left + rect.x() + 9, top + rect.y() + 7, rect.width() - 18, color);
    }

    boolean mouseClicked(double localX, double localY, int button) {
        if (button != 0) return true;
        if (HOME.contains(localX, localY)) open(Page.HOME, null);
        else if (ATTRIBUTES.contains(localX, localY)) open(Page.ATTRIBUTES, null);
        else if (COMPONENTS.contains(localX, localY)) open(Page.COMPONENTS, null);
        else if (page == Page.COMPONENTS && COMPONENT_LIST.contains(localX, localY)) {
            int index = (int) (localY - COMPONENT_LIST.y() + listScroll.offset()) / COMPONENT_ROW_HEIGHT;
            if (index < installed.size()) {
                var next = installed.get(index).definition().id();
                if (!next.equals(componentId)) { componentId = next; textScroll.reset(); }
            }
        }
        return true;
    }

    boolean mouseScrolled(double localX, double localY, double delta) {
        if (page == Page.ATTRIBUTES && ATTRIBUTE_TEXT.contains(localX, localY)) textScroll.move(-delta * 24);
        if (page == Page.COMPONENTS) {
            if (COMPONENT_LIST.contains(localX, localY)) listScroll.move(-delta * COMPONENT_ROW_HEIGHT);
            if (COMPONENT_TEXT.contains(localX, localY)) textScroll.move(-delta * 24);
        }
        return true;
    }

    boolean keyPressed(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> open(Page.HOME, null);
            case GLFW.GLFW_KEY_UP -> textScroll.move(-24);
            case GLFW.GLFW_KEY_DOWN -> textScroll.move(24);
            case GLFW.GLFW_KEY_PAGE_UP -> textScroll.move(-100);
            case GLFW.GLFW_KEY_PAGE_DOWN -> textScroll.move(100);
            case GLFW.GLFW_KEY_HOME -> textScroll.reset();
            case GLFW.GLFW_KEY_END -> textScroll.end();
            default -> { }
        }
        return true; // Read-only page must not pass hotbar, clone or drop keys to hidden inventory slots.
    }

    static Component text(String suffix) { return Component.translatable("gui.equipment_structure_api.details." + suffix); }
    private static void drawShort(GuiGraphics graphics, Font font, Component value, int x, int y, int width, int color) {
        graphics.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(value, width)), x, y, color, false);
    }
    private record TextLine(int x, int y, FormattedCharSequence text, int color) {}
}
