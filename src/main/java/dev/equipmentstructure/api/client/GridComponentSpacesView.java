package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.grid.*;
import dev.equipmentstructure.api.grid.space.*;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import dev.equipmentstructure.api.ui.GridComponentDisplayRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Consumer;

/** Attached regions and a modal child panel in the existing assembly screen. No predicted item writes. */
final class GridComponentSpacesView {
    record Intent(ResourceLocation source, ResourceLocation space, String token, ComponentSpaceTransactions.Action action,
                  int entry, GridPlacement target, boolean single) {}
    private record Surface(ResourceLocation owner, ComponentSpaceDefinition definition, ComponentSpaceContents.Space stored,
                           String token, GridLayout.Part source) {}
    private record Hit(Surface surface, GridCell local, int entry) {}
    private final EquipmentAssemblyMenu menu;
    private final Consumer<Intent> send;
    private final GridViewport boardCamera;
    private final GridViewport panelCamera = new GridViewport(115, 45, 154, 77);
    private EquipmentStructure structure;
    private GridDefinitions definitions;
    private GridLayout layout;
    private ResourceLocation openOwner;
    private ResourceLocation openComponent;
    private String openToken;
    private int page, recoveryScroll;
    private GridRotation carriedRotation = GridRotation.NONE;
    private List<Surface> surfaces = List.of();
    private double mouseX, mouseY;
    GridComponentSpacesView(EquipmentAssemblyMenu menu, GridViewport camera, Consumer<Intent> send) {
        this.menu = menu; this.boardCamera = camera; this.send = send;
    }
    void close() { openOwner = null; openComponent = null; openToken = null; page = 0; recoveryScroll = 0; carriedRotation = GridRotation.NONE; }
    boolean open() { return openOwner != null; }
    void refresh(EquipmentStructure next, GridDefinitions definitions, GridLayout layout) {
        if (structure == null || next == null || !structure.hostId().equals(next.hostId())) close();
        structure = next; this.definitions = definitions; this.layout = layout;
        var result = new ArrayList<Surface>();
        if (next != null) for (var slot : next.components().keySet()) {
            var part = next.component(slot).orElseThrow();
            if (slot.equals(openOwner) && !part.id().equals(openComponent)) close();
            try {
                var saved = ComponentSpaceContents.read(part);
                var ids = new LinkedHashMap<ResourceLocation, ComponentSpaceDefinition>();
                definitions.spaces().getOrDefault(part.id(), List.of()).forEach(s -> ids.put(s.id(), s));
                saved.spaces().forEach((id, space) -> ids.putIfAbsent(id, ComponentSpaceDefinition.panel(id, space.shape())));
                for (var definition : ids.values()) result.add(new Surface(slot, definition,
                        saved.spaces().getOrDefault(definition.id(), new ComponentSpaceContents.Space(definition.shape(), List.of())),
                        saved.token(), layout == null ? null : layout.parts().get(slot)));
                if (slot.equals(openOwner) && openToken != null && !openToken.isEmpty() && !openToken.equals(saved.token())) close();
                if (slot.equals(openOwner)) openToken = saved.token();
            } catch (RuntimeException invalid) { /* Unknown raw storage remains untouched on the server. */ }
        }
        surfaces = List.copyOf(result);
        if (openOwner != null && panels().isEmpty()) close();
        if (openOwner != null) page = Math.clamp(page, 0, panels().size() - 1);
    }
    private List<Surface> panels() { return surfaces.stream().filter(s -> s.owner().equals(openOwner)).toList(); }
    private Surface panel() { return openOwner == null ? null : panels().get(page); }
    private void fit() { var p = panel(); if (p != null) panelCamera.fit(p.stored().shape()); }
    private int color(Surface s) {
        var id = structure.component(s.owner()).orElseThrow().id();
        return s.definition().color().orElse(GridComponentDisplayRegistry.get(id).resolvedColor(id));
    }
    void renderAttached(GuiGraphics g, Font font) {
        for (var s : surfaces) if (s.source() != null && s.definition().mode() == ComponentSpaceDefinition.Mode.ATTACHED_REGION) {
            for (var cell : s.definition().shape().cells()) drawCell(g, boardCamera,
                    s.definition().toBoard(cell, s.source().footprint().shape(), s.source().placement()), color(s), false);
            for (var entry : s.stored().entries()) drawEntry(g, font, s, entry, boardCamera, true);
        }
    }
    void renderPanel(GuiGraphics g, Font font, int left, int top, double mx, double my) {
        mouseX = mx; mouseY = my;
        var s = panel(); if (s == null) return;
        g.pose().pushPose(); g.pose().translate(left, top, 200);
        g.fill(108, 25, 276, 141, 0xFF171D22); g.renderOutline(108, 25, 168, 116, 0xFF879594);
        tiny(g, font, 112, "<"); tiny(g, font, 128, ">"); tiny(g, font, 259, "x");
        var title = name(s).getString(); g.drawString(font, font.plainSubstrByWidth(title, 108), 146, 30, 0xFFE7EFED, false);
        g.enableScissor(left + 114, top + 44, left + 270, top + 123);
        for (var cell : s.stored().shape().cells()) drawCell(g, panelCamera, cell, color(s), false);
        for (var entry : s.stored().entries()) drawEntry(g, font, s, entry, panelCamera, false);
        g.disableScissor();
        // Direct recovery strip remains usable when a reload moves entries outside the current shape.
        int count = needsRecoveryStrip(s) ? s.stored().entries().size() : 0; recoveryScroll = Math.clamp(recoveryScroll, 0, Math.max(0, count - 11));
        for (int n = 0; n < 11 && n + recoveryScroll < count; n++) {
            int x = 115 + n * 14; g.renderOutline(x, 125, 13, 13, 0xFF647471);
            var item = s.stored().entries().get(n + recoveryScroll).stack(Minecraft.getInstance().level.registryAccess());
            g.pose().pushPose(); g.pose().translate(x + 1, 126, 0); g.pose().scale(.6875F, .6875F, 1); g.renderItem(item, 0, 0); g.pose().popPose();
        }
        g.pose().popPose();
    }
    private boolean needsRecoveryStrip(Surface s) {
        var used = new HashSet<GridCell>();
        for (var entry : s.stored().entries()) for (var cell : entry.cells())
            if (!s.stored().shape().contains(cell) || !used.add(cell)) return true;
        return false;
    }
    private static void tiny(GuiGraphics g, Font font, int x, String label) {
        EquipmentStructureButton.blitNineSlice(g, x, 28, 13, 13, 2); g.drawString(font, label, x + 4, 31, 0xFFE7EFED, false);
    }
    private static void drawCell(GuiGraphics g, GridViewport camera, GridCell cell, int rgb, boolean filled) {
        int x = (int) Math.floor(camera.screenX(cell.x())), y = (int) Math.floor(camera.screenY(cell.y()));
        int r = (int) Math.floor(camera.screenX(cell.x() + 1)), b = (int) Math.floor(camera.screenY(cell.y() + 1));
        var colors = GridPartColors.of(rgb, false, false);
        g.fill(x, y, r, b, filled ? colors.fill() : 0xFF20292D);
        if (filled) return;
        for (int px = x; px < r; px += 4) { g.fill(px, y, Math.min(px + 2, r), y + 1, colors.outline()); g.fill(px, b - 1, Math.min(px + 2, r), b, colors.outline()); }
        for (int py = y; py < b; py += 4) { g.fill(x, py, x + 1, Math.min(py + 2, b), colors.outline()); g.fill(r - 1, py, r, Math.min(py + 2, b), colors.outline()); }
    }
    private void drawEntry(GuiGraphics g, Font font, Surface s, ComponentSpaceContents.Entry entry, GridViewport camera, boolean attached) {
        var actual = entry.cells().stream().map(local -> attached ? s.definition().toBoard(local, s.source().footprint().shape(), s.source().placement()) : local).toList();
        var item = entry.stack(Minecraft.getInstance().level.registryAccess());
        var itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem());
        int rgb = GridComponentDisplayRegistry.get(itemId).resolvedColor(itemId);
        for (var cell : actual) drawCell(g, camera, cell, rgb, true);
        var shape = GridShape.of(actual); var geometry = GridPartGeometry.of(shape);
        int minX = actual.stream().mapToInt(GridCell::x).min().orElseThrow(), minY = actual.stream().mapToInt(GridCell::y).min().orElseThrow();
        int outline = GridPartColors.of(rgb, false, false).outline();
        for (var edge : geometry.edges()) {
            int x = (int) Math.floor(camera.screenX(minX + edge.x())), y = (int) Math.floor(camera.screenY(minY + edge.y()));
            int r = (int) Math.floor(camera.screenX(minX + edge.x() + 1)), b = (int) Math.floor(camera.screenY(minY + edge.y() + 1));
            switch (edge.side()) {
                case TOP -> g.fill(x, y, r, y + 1, outline); case RIGHT -> g.fill(r - 1, y, r, b, outline);
                case BOTTOM -> g.fill(x, b - 1, r, b, outline); case LEFT -> g.fill(x, y, x + 1, b, outline);
            }
        }
        if (item.isEmpty() || camera.scale() < 5) return;
        var area = GridPartGeometry.artwork(shape, 1);
        double size = Math.min(area.width(), area.height()) * camera.scale() - 2;
        double x = camera.screenX(minX + area.x()) + area.width() * camera.scale() / 2 - size / 2;
        double y = camera.screenY(minY + area.y()) + area.height() * camera.scale() / 2 - size / 2;
        float scale = (float) (size / 16);
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        g.renderItem(item, 0, 0); g.renderItemDecorations(font, item, 0, 0); g.pose().popPose();
    }
    private Hit at(double x, double y) {
        var p = panel();
        if (p != null) {
            if (needsRecoveryStrip(p) && x >= 115 && x < 269 && y >= 125 && y < 138) {
                int index = recoveryScroll + (int) (x - 115) / 14;
                if (index < p.stored().entries().size()) return new Hit(p, p.stored().entries().get(index).placement().translate(new GridCell(0, 0)), index);
            }
            if (panelCamera.contains(x, y)) { var cell = panelCamera.cell(x, y);
                if (p.stored().shape().contains(cell)) return new Hit(p, cell, -1); }
            return null;
        }
        if (!boardCamera.contains(x, y)) return null;
        var cell = boardCamera.cell(x, y);
        for (var s : surfaces) if (s.source() != null && s.definition().mode() == ComponentSpaceDefinition.Mode.ATTACHED_REGION)
            for (var local : s.definition().shape().cells()) if (s.definition().toBoard(local, s.source().footprint().shape(), s.source().placement()).equals(cell)) return new Hit(s, local, -1);
        return null;
    }
    boolean click(double x, double y, int button) {
        mouseX = x; mouseY = y;
        if (openOwner != null && y >= 28 && y < 41) {
            if (x >= 259 && x < 272) { close(); return true; }
            if (x >= 112 && x < 141) { page = Math.floorMod(page + (x < 126 ? -1 : 1), panels().size()); recoveryScroll = 0; fit(); return true; }
        }
        var hit = at(x, y);
        if (hit != null && (button == 0 || button == 1)) {
            if (hit.entry() >= 0 && !menu.getCarried().isEmpty()) return true;
            send.accept(new Intent(hit.surface().owner(), hit.surface().definition().id(), hit.surface().token(), ComponentSpaceTransactions.Action.CLICK,
                    hit.entry(), new GridPlacement(hit.local().x(), hit.local().y(), rotation(hit.surface())), button == 1));
            return true;
        }
        if (openOwner != null && x >= 104 && x < 392 && y < 164) { if (x < 108 || x >= 276 || y < 25 || y >= 141) close(); return true; }
        if (button == 1 && layout != null && boardCamera.contains(x, y)) {
            var source = layout.componentAt(boardCamera.cell(x, y)).orElse(null);
            if (source != null && surfaces.stream().anyMatch(s -> s.owner().equals(source))) {
                openOwner = source; openComponent = structure.component(source).orElseThrow().id(); openToken = surfaces.stream().filter(s -> s.owner().equals(source)).findFirst().orElseThrow().token();
                page = 0; var options = panels();
                for (int i = 0; i < options.size(); i++) if (options.get(i).definition().mode() == ComponentSpaceDefinition.Mode.CHILD_PANEL) { page = i; break; }
                fit(); return true;
            }
        }
        return false;
    }
    boolean openSource(ResourceLocation source) {
        if (surfaces.stream().noneMatch(s -> s.owner().equals(source))) return false;
        openOwner = source; openComponent = structure.component(source).orElseThrow().id(); openToken = panels().getFirst().token(); page = 0; fit(); return true;
    }
    private GridRotation rotation(Surface s) {
        if (menu.getCarried().isEmpty()) return GridRotation.NONE;
        var footprint = ComponentSpaceTransactions.footprint(s.definition(), menu.getCarried());
        return footprint.rotations().contains(carriedRotation) ? carriedRotation : footprint.rotations().stream().sorted().findFirst().orElseThrow();
    }
    boolean rotate() {
        var hit = at(mouseX, mouseY); if (hit == null) return openOwner != null;
        if (!menu.getCarried().isEmpty()) {
            var footprint = ComponentSpaceTransactions.footprint(hit.surface().definition(), menu.getCarried());
            do { carriedRotation = carriedRotation.clockwise(); } while (!footprint.rotations().contains(carriedRotation)); return true;
        }
        int index = hit.entry() >= 0 ? hit.entry() : hit.surface().stored().at(hit.local());
        if (index < 0) return true;
        var entry = hit.surface().stored().entries().get(index); var rotation = entry.placement().rotation();
        do { rotation = rotation.clockwise(); } while (!entry.footprint().rotations().contains(rotation));
        send.accept(new Intent(hit.surface().owner(), hit.surface().definition().id(), hit.surface().token(), ComponentSpaceTransactions.Action.MOVE,
                index, new GridPlacement(entry.placement().x(), entry.placement().y(), rotation), false)); return true;
    }
    boolean scroll(double x, double y, double amount) {
        if (openOwner == null || x < 108 || x >= 276 || y < 25 || y >= 141) return false;
        if (y >= 123) recoveryScroll += amount < 0 ? 1 : -1;
        else if (panelCamera.contains(x, y)) panelCamera.zoom(x, y, amount);
        return true;
    }
    boolean tooltip(GuiGraphics g, Font font, int left, int top, int mx, int my) {
        var hit = at(mx - left, my - top); if (hit == null) return openOwner != null && mx - left >= 108 && mx - left < 276 && my - top >= 25 && my - top < 141;
        var lines = new ArrayList<Component>(); lines.add(name(hit.surface()));
        int index = hit.entry() >= 0 ? hit.entry() : hit.surface().stored().at(hit.local());
        if (index >= 0) { var item = hit.surface().stored().entries().get(index).stack(Minecraft.getInstance().level.registryAccess()); if (!item.isEmpty()) lines.add(item.getHoverName()); }
        g.pose().pushPose(); g.pose().translate(0, 0, 300); g.renderComponentTooltip(font, lines, mx, my); g.pose().popPose(); return true;
    }
    private static Component name(Surface s) { var id = s.definition().id(); return Component.translatableWithFallback("space." + id.getNamespace() + "." + id.getPath().replace('/', '.'), id.getPath()); }
}
