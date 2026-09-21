package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.EquipmentComponentRegistry;
import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.grid.*;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import dev.equipmentstructure.api.network.GridActionPayload;
import dev.equipmentstructure.api.ui.GridComponentDisplayRegistry;
import net.minecraft.client.Minecraft;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** One workspace: sidebar transfers items, board gestures move only geometry. No client item prediction. */
final class EquipmentAssemblyGridView {
    static final EquipmentAssemblyUiLayout.Panel DETAILS = new EquipmentAssemblyUiLayout.Panel(288, 170, 104, 86);
    private static final EquipmentAssemblyUiLayout.Panel SIDEBAR = new EquipmentAssemblyUiLayout.Panel(288, 2, 104, 162);
    private static final int VX = 110, VY = 26, VW = 164, VH = 114;
    private static final int SX = GridSidebarLayout.X, SY = GridSidebarLayout.Y, PITCH = GridSidebarLayout.PITCH,
            COLS = GridSidebarLayout.COLUMNS, ROWS = GridSidebarLayout.ROWS;
    private static final AtomicInteger REQUESTS = new AtomicInteger();
    private final EquipmentAssemblyMenu menu;
    private final GridViewport camera = new GridViewport(VX, VY, VW, VH);
    private final GridComponentSpacesView spaces;
    private EquipmentAssemblyDisplaySnapshot snapshot;
    private GridDefinitions definitions;
    private EquipmentStructure structure;
    private GridBoard board;
    private GridLayout layout;
    private GridTransactions.Status layoutStatus;
    private ItemStack observedEquipment = ItemStack.EMPTY, observedCarried = ItemStack.EMPTY;
    private ResourceLocation selected, dragging;
    private GridDragGeometry grip;
    private boolean panning;
    private int scrollRow;
    private double mouseX, mouseY;
    private Integer pending;
    private long pendingSince;
    private final Map<GridShape, GridPartGeometry> partGeometry = new java.util.HashMap<>();
    private final Map<GridArtworkLayout.Key, GridArtworkLayout.Frame> artworkLayouts = new java.util.HashMap<>();
    private final Map<ResourceLocation, Boolean> textureAvailability = new java.util.HashMap<>();
    private long displayGeneration = -1;
    private EquipmentStructure ruleStructure;
    private GridDefinitions ruleDefinitions;
    private Map<dev.equipmentstructure.api.grid.synergy.GridRuleRegistry.Key, dev.equipmentstructure.api.grid.synergy.GridRuleResult> ruleResults = Map.of(), previewRuleResults = Map.of();
    private GridPlacement previewRulePosition;
    private ResourceLocation previewRuleSource;

    EquipmentAssemblyGridView(EquipmentAssemblyMenu menu) { this.menu = menu; this.spaces = new GridComponentSpacesView(menu, camera, this::sendSpace); }
    void closeSpacePanel() { spaces.close(); }

    static boolean hasSprite(ResourceLocation id) {
        return !Minecraft.getInstance().getGuiSprites().getSprite(id).contents().name()
                .equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation());
    }

    ResourceLocation selected() { return selected; }
    boolean busy() { return dragging != null || pending != null || panning; }
    boolean pending() { return pending != null; }
    void cancelGesture() { dragging = null; grip = null; panning = false; }
    void select(ResourceLocation id) { selected = id; }

    void refresh(EquipmentAssemblyDisplaySnapshot nextSnapshot) {
        snapshot = nextSnapshot;
        var next = EquipmentStructureApi.structure(menu.equipmentStack()).orElse(null);
        var catalog = GridDefinitionSync.current();
        boolean hostChanged = structure == null || next == null || !structure.hostId().equals(next.hostId())
                || observedEquipment.getItem() != menu.equipmentStack().getItem();
        boolean changed = !ItemStack.matches(observedEquipment, menu.equipmentStack()) || !Objects.equals(catalog, definitions);
        if (changed || !ItemStack.matches(observedCarried, menu.getCarried())) {
            if (dragging != null) feedback("changed");
            cancelGesture();
            observedCarried = menu.getCarried().copy();
        }
        if (!menu.getCarried().isEmpty()) selected = null;
        if (changed) {
            if (!Objects.equals(catalog, definitions) || hostChanged) { partGeometry.clear(); artworkLayouts.clear(); }
            observedEquipment = menu.equipmentStack().copy();
            definitions = catalog;
            structure = next;
            var nextBoard = next == null ? null : catalog.hosts().get(next.hostId());
            boolean refit = hostChanged || !Objects.equals(nextBoard, board);
            board = nextBoard;
            var view = next == null ? null : GridTransactions.resolve(next, catalog);
            layout = view == null ? null : view.layout().orElse(null);
            layoutStatus = view == null ? GridTransactions.Status.DISABLED : view.status();
            spaces.refresh(next, catalog, layout);
            if (refit && board != null) camera.fit(board.area());
            if (hostChanged) { selected = null; scrollRow = 0; }
            if (selected != null && (next == null || next.component(selected).isEmpty())) selected = null;
        }
        scrollRow = Math.clamp(scrollRow, 0, maxScrollRow());
        if (pending != null) {
            var result = menu.lastGridResult().filter(value -> value.requestId() == pending).orElse(null);
            if (result != null) {
                // The server queues the vanilla slot/cursor sync before the acknowledgement, including no-ops.
                feedback(result.applied() ? "applied" : "rejected");
                pending = null;
            } else if (Util.getMillis() - pendingSince > 10000) {
                feedback("timeout"); // Never resend an item transaction automatically.
                pending = null;
            }
        }
    }

    void render(GuiGraphics g, Font font, int left, int top, double mx, double my) {
        mouseX = mx; mouseY = my;
        if (displayGeneration != GridComponentDisplayReloadListener.generation()) {
            displayGeneration = GridComponentDisplayReloadListener.generation();
            artworkLayouts.clear(); textureAvailability.clear();
        }
        EquipmentAssemblyPanelRenderer.render(g, left, top, SIDEBAR);
        g.pose().pushPose();
        g.pose().translate(left, top, 0);
        label(g, font, text("interfaces"), 298, 11, 84, 0xFFCCCCCC);
        for (int n = 0; n < COLS * ROWS; n++) {
            int index = scrollRow * COLS + n;
            if (index >= snapshot.definitions().size()) break;
            var slot = snapshot.definitions().get(index);
            int x = SX + n % COLS * PITCH, y = SY + n / COLS * PITCH;
            boolean compatible = menu.getCarried().isEmpty() || (structure != null
                    && EquipmentComponentRegistry.fromItemStack(menu.getCarried())
                    .filter(component -> slot.accepts(structure.equipmentType(), component)).isPresent());
            g.fill(x, y, x + 19, y + 19, 0xDD15191D);
            g.renderOutline(x, y, 19, 19, slot.id().equals(selected) ? 0xFFB4C9D9 : compatible ? 0xFF656B70 : 0xFF995D5D);
            var info = snapshot.componentInfo().get(slot.id());
            if (info != null && info.installed().isPresent()) {
                g.renderItem(info.itemStack(), x + 1, y + 1);
                int color = GridComponentDisplayRegistry.get(info.installed().get().id()).resolvedColor(info.installed().get().id());
                g.fill(x + 2, y + 17, x + 17, y + 18, 0xFF000000 | color);
            } else {
                snapshot.uiDefinition().slot(slot.id()).emptyIcon()
                        .filter(EquipmentAssemblyGridView::hasSprite).ifPresent(icon -> {
                            g.setColor(.65F, .65F, .65F, .55F);
                            g.blitSprite(icon, x + 1, y + 1, 16, 16);
                            g.setColor(1, 1, 1, 1);
                        });
            }
        }
        if (maxScrollRow() > 0) {
            g.fill(385, SY, 387, SY + ROWS * PITCH - 2, 0xFF30363B);
            int handle = SY + scrollRow * (ROWS * PITCH - 14) / maxScrollRow();
            g.fill(385, handle, 387, handle + 12, 0xFFA1A8AE);
        }
        g.fill(VX, VY, VX + VW, VY + VH, 0xDD10151A);
        if (board == null) label(g, font, text("status.missing_definition"), VX + 6, VY + 8, VW - 12, 0xFFFFAAAA);
        g.enableScissor(left + VX, top + VY, left + VX + VW, top + VY + VH);
        if (board != null) {
            for (var cell : board.area().cells()) cell(g, cell, 0xFF242D34, 0xFF3F4B54);
            panel(g, board.body().rotated(board.bodyPlacement().rotation()), board.bodyPlacement(),
                    board.bodyOccupiesCells() ? 0xFF515459 : 0xFF283B45,
                    board.bodyOccupiesCells() ? 0xFFA0A0A0 : 0xFF658997);
            spaces.renderAttached(g, font);
            if (layout != null) for (var entry : layout.parts().entrySet()) {
                boolean active = entry.getKey().equals(selected);
                boolean drag = entry.getKey().equals(dragging);
                var part = entry.getValue();
                var shape = part.footprint().oriented(part.placement().rotation());
                var info = snapshot.componentInfo().get(entry.getKey());
                var id = info != null && info.installed().isPresent() ? info.installed().get().id() : entry.getKey();
                var colors = GridPartColors.of(GridComponentDisplayRegistry.get(id).resolvedColor(id), active, drag);
                panel(g, shape, part.placement(), colors.fill(), colors.outline());
                if (info != null && info.installed().isPresent())
                    item(g, info.itemStack(), info.installed().get().id(), part.footprint().shape(), part.placement());
            }
            if (dragging != null && camera.contains(mx, my)) {
                var position = grip.at(camera.cell(mx, my));
                boolean allowed = layout.checkPlacement(dragging, grip.footprint(), position).allowed();
                var info = snapshot.componentInfo().get(dragging);
                var id = info != null && info.installed().isPresent() ? info.installed().get().id() : dragging;
                var colors = GridPartColors.of(GridComponentDisplayRegistry.get(id).resolvedColor(id), true, false);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                panel(g, grip.footprint().oriented(grip.rotation()), position,
                        allowed ? colors.fill() : 0xFF733C3C, allowed ? 0xFF9CE5B3 : 0xFFFF9595);
                if (info != null && info.installed().isPresent())
                    item(g, info.itemStack(), info.installed().get().id(), grip.footprint().shape(), position);
                g.pose().popPose();
            }
        }
        renderRuleHighlights(g);
        g.disableScissor();
        g.pose().popPose();
        if (selected != null && menu.getCarried().isEmpty()) {
            var info = snapshot.componentInfo().get(selected);
            if (info != null && info.installed().isPresent()) {
                EquipmentAssemblyPanelRenderer.render(g, left, top, DETAILS);
                g.renderItem(info.itemStack(), left + 331, top + 181);
                label(g, font, info.itemName(), left + 298, top + 202, 84, 0xFFEEEEEE);
                label(g, font, snapshot.interfaceName(selected), left + 298, top + 216, 84, 0xFFAAAAAA);
            }
        }
        spaces.renderPanel(g, font, left, top, mx, my);
    }

    private void renderRuleHighlights(GuiGraphics g) {
        if (selected == null || dragging == null || structure == null || layout == null
                || definitions.rules().isEmpty() || spaces.open() || !camera.contains(mouseX, mouseY)) return;
        if (ruleStructure != structure || ruleDefinitions != definitions) {
            ruleStructure = structure; ruleDefinitions = definitions;
            ruleResults = dev.equipmentstructure.api.grid.synergy.GridRuleRegistry.evaluate(structure, definitions, true);
            previewRulePosition = null; previewRuleSource = null;
        }
        var before = ruleResults;
        var position = grip.at(camera.cell(mouseX, mouseY));
        if (!position.equals(previewRulePosition) || !dragging.equals(previewRuleSource)) {
            previewRulePosition = position; previewRuleSource = dragging;
            var plan = GridTransactions.move(structure, Map.of(dragging, position), definitions);
            previewRuleResults = plan.allowed() ? dev.equipmentstructure.api.grid.synergy.GridRuleRegistry.evaluate(plan.structure(), definitions, true) : before;
        }
        g.pose().pushPose(); g.pose().translate(0, 0, 180);
        for (var entry : previewRuleResults.entrySet()) if (entry.getKey().source().equals(selected)) {
            var previous = before.get(entry.getKey()); var result = entry.getValue();
            if (Objects.equals(previous, result)) continue;
            int color = result.active() ? 0xFF88E5AA : 0xFFE9A566;
            var cells = new java.util.HashSet<>(result.cells());
            if (previous != null && previous.active() && !result.active()) cells.addAll(previous.cells());
            for (var c : cells) {
                int x = (int) camera.screenX(c.x()), y = (int) camera.screenY(c.y());
                int size = Math.max(2, (int) camera.scale()); g.renderOutline(x, y, size, size, color);
            }
        }
        g.pose().popPose();
    }

    private void cell(GuiGraphics g, GridCell c, int fill, int outline) {
        int x = (int) Math.floor(camera.screenX(c.x())), y = (int) Math.floor(camera.screenY(c.y()));
        int right = (int) Math.floor(camera.screenX(c.x() + 1)), bottom = (int) Math.floor(camera.screenY(c.y() + 1));
        if (right <= VX || bottom <= VY || x >= VX + VW || y >= VY + VH) return;
        g.fill(x, y, right, bottom, fill);
        if (camera.scale() >= 4) g.renderOutline(x, y, right - x, bottom - y, outline);
    }

    private void panel(GuiGraphics g, GridShape shape, GridPlacement position, int fill, int outline) {
        var geometry = partGeometry.computeIfAbsent(shape, GridPartGeometry::of);
        for (var run : geometry.runs()) {
            int x = (int) Math.floor(camera.screenX(position.x() + run.x()));
            int y = (int) Math.floor(camera.screenY(position.y() + run.y()));
            int right = (int) Math.floor(camera.screenX(position.x() + run.x() + run.width()));
            int bottom = (int) Math.floor(camera.screenY(position.y() + run.y() + 1));
            g.fill(x, y, right, bottom, fill);
        }
        if (camera.scale() >= 4) for (var edge : geometry.edges()) {
            int x = (int) Math.floor(camera.screenX(position.x() + edge.x()));
            int y = (int) Math.floor(camera.screenY(position.y() + edge.y()));
            int right = (int) Math.floor(camera.screenX(position.x() + edge.x() + 1));
            int bottom = (int) Math.floor(camera.screenY(position.y() + edge.y() + 1));
            switch (edge.side()) {
                case TOP -> g.fill(x, y, right, y + 1, outline);
                case RIGHT -> g.fill(right - 1, y, right, bottom, outline);
                case BOTTOM -> g.fill(x, bottom - 1, right, bottom, outline);
                case LEFT -> g.fill(x, y, x + 1, bottom, outline);
            }
        }
    }

    private void item(GuiGraphics g, ItemStack stack, ResourceLocation componentId, GridShape shape, GridPlacement position) {
        if (camera.scale() < 5) return;
        var display = GridComponentDisplayRegistry.get(componentId);
        var texture = display.texture().filter(value -> textureAvailability.computeIfAbsent(value.resource(),
                id -> Minecraft.getInstance().getResourceManager().getResource(id).isPresent()));
        if (texture.isEmpty() && stack.isEmpty()) return;
        var key = new GridArtworkLayout.Key(shape, position.rotation(), display, texture.isPresent());
        var frame = artworkLayouts.computeIfAbsent(key, value -> {
            var resolved = GridArtworkLayout.resolve(value);
            if (resolved.rejectedBox()) EquipmentStructureApiMod.LOGGER.warn(
                    "Grid artwork box for {} crosses its footprint or a hole; using automatic placement", componentId);
            return resolved;
        });
        double width = frame.width() * camera.scale(), height = frame.height() * camera.scale();
        g.pose().pushPose();
        g.pose().translate(camera.screenX(position.x()) + frame.centerX() * camera.scale(),
                camera.screenY(position.y()) + frame.centerY() * camera.scale(), 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(frame.angle()));
        g.pose().translate(-width / 2, -height / 2, 0);
        if (texture.isPresent()) {
            var source = texture.get();
            g.pose().scale((float) (width / source.width()), (float) (height / source.height()), 1);
            g.blit(source.resource(), 0, 0, 0, 0, source.width(), source.height(), source.width(), source.height());
        } else {
            g.pose().scale((float) (width / 16), (float) (height / 16), 1);
            g.renderItem(stack, 0, 0);
        }
        g.pose().popPose();
    }

    boolean tooltip(GuiGraphics g, Font font, int left, int top, int mx, int my) {
        if (spaces.tooltip(g, font, left, top, mx, my)) return true;
        double x = mx - left, y = my - top;
        ResourceLocation id = slotAt(x, y);
        if (id == null && layout != null && camera.contains(x, y)) id = layout.componentAt(camera.cell(x, y)).orElse(null);
        var lines = new ArrayList<Component>();
        if (id != null) {
            var info = snapshot.componentInfo().get(id);
            if (info != null && info.installed().isPresent()) lines.add(info.itemName());
            lines.addAll(snapshot.interfaceTooltip(id));
        } else if (camera.contains(x, y)) {
            if (board != null && board.bodyCells().contains(camera.cell(x, y)))
                lines.add(text(board.bodyOccupiesCells() ? "body_solid" : "body_reference"));
        }
        if (lines.isEmpty()) return false;
        g.renderComponentTooltip(font, lines, mx, my);
        return true;
    }

    boolean click(double x, double y, int button, boolean quickMove) {
        mouseX = x; mouseY = y;
        if (busy()) return true;
        if (spaces.click(x, y, button)) return true;
        var slot = slotAt(x, y);
        if (slot != null) {
            if (button == 1 && menu.getCarried().isEmpty() && spaces.openSource(slot)) return true;
            if (button == 0 || button == 1) transfer(slot, quickMove);
            return true;
        }
        if (camera.contains(x, y)) {
            if (button == 2) { panning = true; return true; }
            if (button != 0 || layout == null) return true;
            if (!menu.getCarried().isEmpty()) { feedback("use_sidebar"); return true; }
            var cell = camera.cell(x, y);
            selected = layout.componentAt(cell).orElse(null);
            if (selected != null) {
                dragging = selected;
                var part = layout.parts().get(selected);
                grip = new GridDragGeometry(part.footprint(), part.placement().rotation(),
                        new GridCell(cell.x() - part.placement().x(), cell.y() - part.placement().y()));
            }
            return true;
        }
        return inside(x, y, 104, 2, 176, 162) || inside(x, y, 288, 2, 104, 162);
    }

    private void transfer(ResourceLocation slot, boolean quickMove) {
        if (structure == null) return;
        if (menu.getCarried().isEmpty()) {
            if (structure.component(slot).isPresent()) {
                selected = null;
                send(quickMove ? GridActionPayload.Action.QUICK_REMOVE : GridActionPayload.Action.REMOVE, slot, Map.of());
            }
            return; // Empty interfaces never activate a staging mirror.
        }
        var component = EquipmentComponentRegistry.fromItemStack(menu.getCarried()).orElse(null);
        var definition = structure.slot(slot).orElse(null);
        if (component == null || definition == null
                || !definition.accepts(structure.equipmentType(), component)) { feedback("incompatible"); return; }
        if (layout == null) { feedback("status." + layoutStatus.name().toLowerCase(java.util.Locale.ROOT)); return; }
        boolean replacing = structure.component(slot).isPresent();
        if (replacing && menu.getCarried().getCount() != 1) { feedback("replace_one"); return; }
        var footprint = definitions.components().get(component.id());
        if (footprint == null) { feedback("status.missing_definition"); return; }
        GridPlacement position = GridSidebarPlacement.find(layout, slot, footprint, definitions.spaces().getOrDefault(component.id(), java.util.List.of())).orElse(null);
        if (position == null) { feedback("status.no_space"); return; }
        send(replacing ? GridActionPayload.Action.REPLACE : GridActionPayload.Action.INSTALL, slot, Map.of(slot, position));
    }

    boolean drag(double x, double y, int button, double dx, double dy) {
        mouseX = x; mouseY = y;
        if (panning && button == 2) { camera.pan(dx, dy); return true; }
        return dragging != null || pending();
    }

    boolean release(double x, double y, int button) {
        mouseX = x; mouseY = y;
        if (panning && button == 2) { panning = false; return true; }
        if (dragging == null || button != 0) return pending();
        var slot = dragging;
        var position = grip.at(camera.cell(x, y));
        var footprint = grip.footprint();
        cancelGesture();
        if (!camera.contains(x, y)) { feedback("cancelled"); return true; }
        move(slot, footprint, position);
        return true;
    }

    private void move(ResourceLocation slot, GridFootprint footprint, GridPlacement position) {
        var check = layout.checkPlacement(slot, footprint, position);
        if (!check.allowed()) { feedback("failure." + check.failure().name().toLowerCase(java.util.Locale.ROOT)); return; }
        if (!position.equals(layout.parts().get(slot).placement())) send(GridActionPayload.Action.MOVE, slot, Map.of(slot, position));
    }

    boolean scroll(double x, double y, double amount) {
        if (spaces.scroll(x, y, amount)) return true;
        if (inside(x, y, 288, 2, 104, 162)) {
            scrollRow = Math.clamp(scrollRow + (amount < 0 ? 1 : amount > 0 ? -1 : 0), 0, maxScrollRow());
            return true;
        }
        if (camera.contains(x, y)) { camera.zoom(x, y, amount); return true; }
        return busy();
    }

    boolean key(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (spaces.open()) { spaces.close(); return true; }
            if (dragging != null || panning) { cancelGesture(); feedback("cancelled"); return true; }
            return false;
        }
        if (pending()) return true;
        if (key == GLFW.GLFW_KEY_HOME) { if (board != null) camera.fit(board.area()); return true; }
        if (key == GLFW.GLFW_KEY_R) { if (!spaces.rotate()) rotate(); return true; }
        if (spaces.open()) return false;
        if (dragging != null || panning) return true;
        if (selected != null && layout != null && menu.getCarried().isEmpty()) {
            int dx = key == GLFW.GLFW_KEY_LEFT ? -1 : key == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
            int dy = key == GLFW.GLFW_KEY_UP ? -1 : key == GLFW.GLFW_KEY_DOWN ? 1 : 0;
            if (dx != 0 || dy != 0) {
                var part = layout.parts().get(selected);
                if (part != null) move(selected, part.footprint(), new GridPlacement(part.placement().x() + dx,
                        part.placement().y() + dy, part.placement().rotation()));
                return true;
            }
        }
        return false;
    }

    private void rotate() {
        if (pending() || !menu.getCarried().isEmpty()) return;
        if (dragging != null) { grip = grip.clockwise(); return; }
        if (selected == null || layout == null || !layout.parts().containsKey(selected)) return;
        var part = layout.parts().get(selected);
        var rotation = part.placement().rotation();
        do { rotation = rotation.clockwise(); } while (!part.footprint().rotations().contains(rotation));
        move(selected, part.footprint(), new GridPlacement(part.placement().x(), part.placement().y(), rotation));
    }

    private void send(GridActionPayload.Action action, ResourceLocation slot, Map<ResourceLocation, GridPlacement> positions) {
        if (pending() || structure == null) return;
        cancelGesture();
        pending = REQUESTS.incrementAndGet(); pendingSince = Util.getMillis();
        EquipmentStructureApiMod.LOGGER.info("[Grid assembly] request={} action={} host={} slot={}", pending, action, structure.hostId(), slot);
        PacketDistributor.sendToServer(new GridActionPayload(menu.containerId, menu.getStateId(), pending,
                menu.equipmentStack(), menu.getCarried(), definitions.fingerprint(), action, slot, positions));
    }
    private void sendSpace(GridComponentSpacesView.Intent intent) {
        if (pending() || structure == null) return;
        cancelGesture(); pending = REQUESTS.incrementAndGet(); pendingSince = Util.getMillis();
        PacketDistributor.sendToServer(new dev.equipmentstructure.api.network.SpaceActionPayload(menu.containerId, menu.getStateId(), pending,
                menu.equipmentStack(), menu.getCarried(), definitions.fingerprint(), intent.source(), intent.space(), intent.token(),
                intent.action(), intent.entry(), intent.target(), intent.single()));
        EquipmentStructureApiMod.LOGGER.info("[Component space] request={} source={} space={} action={}", pending, intent.source(), intent.space(), intent.action());
    }

    private void feedback(String code) {
        EquipmentStructureApiMod.LOGGER.info("[Grid assembly] result={} request={} host={} selected={}", code, pending,
                structure == null ? "none" : structure.hostId(), selected);
    }

    private int maxScrollRow() { return snapshot == null ? 0 : GridSidebarLayout.maxScrollRow(snapshot.definitions().size()); }
    private ResourceLocation slotAt(double x, double y) {
        int index = GridSidebarLayout.indexAt(x, y, scrollRow, snapshot.definitions().size());
        return index < 0 ? null : snapshot.definitions().get(index).id();
    }
    private static boolean inside(double x, double y, int bx, int by, int w, int h) { return x >= bx && y >= by && x < bx + w && y < by + h; }
    private static Component text(String key) { return Component.translatable("gui.equipment_structure_api.grid." + key); }
    private static void label(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        g.drawString(font, font.plainSubstrByWidth(text.getString(), width), x, y, color, false);
    }
}
