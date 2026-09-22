package dev.equipmentstructure.api.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Lighting;
import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.appearance.*;
import dev.equipmentstructure.api.client.appearance.*;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import dev.equipmentstructure.api.network.AppearancePlacementStatusPayload;
import dev.equipmentstructure.api.network.SetAppearanceLayoutPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** One equipment draft; camera and selection never mutate the server item. */
public final class EquipmentAppearancePlacementScreen extends Screen implements MenuAccess<EquipmentAssemblyMenu> {
    private final EquipmentAssemblyMenu menu;
    private final ResourceLocation editorSlot;
    private ResourceLocation selected;
    private final ItemStack original;
    private final EquipmentStructure structure;
    private final List<ResourceLocation> slots;
    private final Map<ResourceLocation, Component> names = new HashMap<>();
    private final Map<ResourceLocation, AppearancePartPresentation> initial = new LinkedHashMap<>(), parts = new LinkedHashMap<>();
    private boolean originalVisible;
    private record Draft(Map<ResourceLocation, AppearancePartPresentation> parts, boolean originalVisible, ResourceLocation selected) {
        private Draft { parts = Map.copyOf(parts); }
    }
    private final ArrayDeque<Draft> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private final AppearancePlacementGizmo gizmo = new AppearancePlacementGizmo(), authorAnchor = new AppearancePlacementGizmo();
    private AppearancePreviewSelection selectionGeometry;
    private EquipmentAppearancePreviewRenderer previewRenderer;
    private Matrix4f capturedFrame;
    private AppearanceTransform selectedAnchor;
    private AppearanceOrbitGizmo orbitGizmo;
    private AppearancePlan capturedPlan;
    private final Map<ResourceLocation, Matrix4f> nativeFrames = new HashMap<>();
    private ItemStack preview;
    private int mode, axis = -1, dragButton = -1, listOffset;
    private boolean dragPart, dragOrbit, dragCheckpoint;
    private double dragDistance;
    private double orbitDragAngle;
    private boolean tiltOrbit;
    private float yaw = -25, pitch = 15, zoom = 130, panX, panY;
    private final EditBox[] fields = new EditBox[3];
    private final Button[] modes = new Button[3];
    private final List<Button> rows = new ArrayList<>();
    private Button confirm, undoButton, redoButton, resetButton, wholeButton;
    private Button snapButton;
    private Button orbitCenterButton, orbitToolButton;
    private EquipmentVisibilityButton originalVisibility, componentVisibility;
    private boolean filling, waiting, stale;
    /** Client-only editor preference; the saved pose is always the resulting position. */
    private boolean snapEnabled = true;
    private int waitingTicks;
    private String status = "";

    public EquipmentAppearancePlacementScreen(EquipmentAssemblyScreen parent, ResourceLocation slotId) {
        super(label("title"));
        menu = parent.getMenu(); original = menu.equipmentStack().copy();
        structure = EquipmentStructureApi.structure(original).orElseThrow();
        slots = structure.slots().stream().map(EquipmentSlotDefinition::id).filter(id -> structure.component(id).isPresent()).toList();
        editorSlot = slots.contains(slotId) ? slotId : slots.getFirst();
        selected = editorSlot;
        for (var id : slots) {
            var part = structure.component(id).orElseThrow();
            initial.put(id, AppearancePartPresentation.read(part));
            names.put(id, EquipmentComponentRegistry.createItemStack(part).map(ItemStack::getHoverName).orElse(Component.literal(part.id().getPath())));
        }
        parts.putAll(initial); originalVisible = AppearanceVisibilityStorage.originalVisible(original); rebuildPreview();
    }
    @Override public EquipmentAssemblyMenu getMenu() { return menu; }
    @Override public boolean isPauseScreen() { return false; }
    public static void receive(AppearancePlacementStatusPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu.containerId != payload.containerId()) return;
        if (payload.status() == AppearancePlacementStatusPayload.OPEN && mc.screen instanceof EquipmentAssemblyScreen assembly) {
            assembly.requestPlacement(payload.slotId());
        } else if (mc.screen instanceof EquipmentAppearancePlacementScreen screen && screen.editorSlot.equals(payload.slotId()) && screen.waiting) {
            screen.waiting = false;
            if (payload.status() == AppearancePlacementStatusPayload.SAVED) screen.onClose();
            else { screen.stale = true; screen.status = "rejected"; }
        }
    }
    private int right() { return width - 144; }
    private int previewRight() { return width - 156; }
    private int previewTop() { return 79; }
    private boolean compact() { return height < 340; }
    private int previewBottom() { return height - (compact() ? 57 : 72); }
    private int listTop() { return compact() ? 162 : 220; }
    private boolean fixedAnchor() {
        var host = AppearanceRuntime.catalog().hosts().get(structure.hostId());
        return selected != null && host != null && host.bindings().get(selected) != null
                && host.bindings().get(selected).placementFreedom() == AppearanceDefinitions.PlacementFreedom.FIXED;
    }
    private AppearancePose pose() { return selected == null ? AppearancePose.IDENTITY : parts.get(selected).pose(); }
    private AppearancePose constrained(AppearancePose next) {
        if (selected == null) return next;
        var host = AppearanceRuntime.catalog().hosts().get(structure.hostId());
        var binding = host == null ? null : host.bindings().get(selected);
        return binding == null ? next : binding.bounds().map(bounds -> bounds.clamp(next)).orElse(next);
    }
    private boolean canEdit() { return EquipmentFeatureConfig.rules().positionEditing()
            && selected != null && !fixedAnchor() && gizmo.valid() && !waiting && !stale; }

    @Override protected void init() {
        rows.clear();
        originalVisibility = addRenderableWidget(new EquipmentVisibilityButton(right(), 27, "original",
                () -> originalVisible, ignored -> toggleVisibility(true)));
        componentVisibility = addRenderableWidget(new EquipmentVisibilityButton(right() + 53, 27, "component",
                () -> selected == null || parts.get(selected).visible(), ignored -> toggleVisibility(false)));
        String[] keys = {"move", "rotate", "scale"};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            modes[i] = addRenderableWidget(EquipmentStructureButton.builder(label(keys[i]), ignored -> setMode(index))
                    .bounds(12 + i * 44, 27, 42, 20).tooltip(Tooltip.create(label("help_part"))).build(EquipmentStructureButton::new));
            fields[i] = new EditBox(font, compact() ? right() + i * 44 + 2 : right() + 16,
                    compact() ? 78 : 78 + i * 26, compact() ? 40 : 112, 20, Component.literal("XYZ".substring(i, i + 1)));
            fields[i].setMaxLength(12); fields[i].setResponder(text -> editNumber(index, text)); addRenderableWidget(fields[i]);
        }
        undoButton = tool("<", "undo", right(), () -> restore(undo, redo));
        redoButton = tool(">", "redo", right() + 44, () -> restore(redo, undo));
        resetButton = tool("0", "reset", right() + 88, this::resetPart);
        resetButton.setTooltip(Tooltip.create(Component.translatable("gui.equipment_structure_api.placement.reset_key",
                EquipmentAssemblyKeyMappings.RESET_PART.getTranslatedKeyMessage())));
        wholeButton = addRenderableWidget(EquipmentStructureButton.builder(label("whole"), ignored -> select(null))
                .bounds(right(), compact() ? 126 : 186, 132, 20).tooltip(Tooltip.create(label("help_camera"))).build(EquipmentStructureButton::new));
        int count = Math.max(1, (previewBottom() - listTop()) / 23);
        for (int i = 0; i < count; i++) {
            final int row = i;
            rows.add(addRenderableWidget(EquipmentStructureButton.builder(Component.empty(), ignored -> {
                int index = listOffset + row; if (index < slots.size()) select(slots.get(index));
            }).bounds(right(), listTop() + i * 23, 132, 21).build(EquipmentStructureButton::new)));
        }
        confirm = addRenderableWidget(EquipmentStructureButton.builder(label("confirm"), ignored -> confirmPlacement())
                .bounds(width - 136, height - 30, 124, 20).tooltip(Tooltip.create(Component.translatable(
                        "gui.equipment_structure_api.placement.help_shortcuts", EquipmentAssemblyKeyMappings.RESET_PART.getTranslatedKeyMessage(),
                        EquipmentAssemblyKeyMappings.FOCUS_PART.getTranslatedKeyMessage()))).build(EquipmentStructureButton::new));
        addRenderableWidget(EquipmentStructureButton.builder(label("cancel"), ignored -> onClose())
                .bounds(12, height - 30, width < 380 ? 46 : 70, 20).build(EquipmentStructureButton::new));
        addRenderableWidget(EquipmentStructureButton.builder(label("reset_view"), ignored -> resetView())
                .bounds(width < 380 ? 62 : 88, height - 30, width < 380 ? 52 : 68, 20).tooltip(Tooltip.create(viewHelp())).build(EquipmentStructureButton::new));
        snapButton = addRenderableWidget(EquipmentStructureButton.builder(label("snap_on"), ignored -> toggleSnap())
                .bounds(width < 380 ? 118 : 160, height - 30, width < 380 ? 56 : 72, 20).tooltip(Tooltip.create(label("snap_help"))).build(EquipmentStructureButton::new));
        if (width >= 380) addRenderableWidget(EquipmentStructureButton.builder(label("focus"), ignored -> focusSelection())
                .bounds(150, 27, 62, 20).tooltip(Tooltip.create(viewHelp())).build(EquipmentStructureButton::new));
        int orbitWidth = Math.min(108, (previewRight() - 16) / 2);
        orbitCenterButton = addRenderableWidget(EquipmentStructureButton.builder(label("orbit_center_author"), ignored -> cycleOrbitCenter())
                .bounds(12, 53, orbitWidth, 20).tooltip(Tooltip.create(label("orbit_center_help"))).build(EquipmentStructureButton::new));
        orbitToolButton = addRenderableWidget(EquipmentStructureButton.builder(label("orbit_radius"), ignored -> {
            tiltOrbit = !tiltOrbit; updateWidgets();
        }).bounds(16 + orbitWidth, 53, orbitWidth, 20).tooltip(Tooltip.create(label("orbit_drag_help"))).build(EquipmentStructureButton::new));
        ensureSelectedRow(); fillFields(); updateWidgets();
    }
    private Button tool(String symbol, String tooltip, int x, Runnable action) {
        return addRenderableWidget(EquipmentStructureButton.builder(Component.literal(symbol), ignored -> {
            if (!waiting && !stale) action.run();
        }).bounds(x, compact() ? 104 : 160, 40, 20).tooltip(Tooltip.create(label(tooltip))).build(EquipmentStructureButton::new));
    }
    private void updateWidgets() {
        if (confirm == null) return;
        confirm.active = !waiting && !stale && validFields();
        var rules = EquipmentFeatureConfig.rules();
        originalVisibility.active = !waiting && !stale && (rules.hideOriginal() || !originalVisible);
        componentVisibility.active = selected != null && !waiting && !stale
                && (rules.hideAttachments() || !parts.get(selected).visible());
        originalVisibility.refresh(); componentVisibility.refresh();
        originalVisibility.setTooltip(Tooltip.create(!rules.hideOriginal() && originalVisible
                ? label("server_hide_disabled") : originalVisibility.getMessage()));
        componentVisibility.setTooltip(Tooltip.create(!rules.hideAttachments() && selected != null && parts.get(selected).visible()
                ? label("server_hide_disabled") : componentVisibility.getMessage()));
        for (int i = 0; i < 3; i++) {
            modes[i].active = rules.positionEditing() && selected != null && !fixedAnchor() && !waiting && !stale && mode != i;
            fields[i].visible = selected != null && (mode != 2 || i == 0); fields[i].setEditable(canEdit());
        }
        undoButton.active = !undo.isEmpty() && !waiting && !stale; redoButton.active = !redo.isEmpty() && !waiting && !stale;
        resetButton.active = canEdit(); wholeButton.active = selected != null && !waiting && !stale;
        if (orbitCenterButton != null) {
            boolean orbit = selectedMotion() != null && selectedMotion().speedDegreesPerSecond() > 0;
            orbitCenterButton.visible = orbitToolButton.visible = orbit;
            orbitCenterButton.active = orbitToolButton.active = orbit && canEdit() && validFields();
            if (selected != null) orbitCenterButton.setMessage(label("orbit_center_" + parts.get(selected).motion().center().name().toLowerCase(Locale.ROOT)));
            orbitToolButton.setMessage(label(tiltOrbit ? "orbit_tilt" : "orbit_radius"));
        }
        if (snapButton != null) {
            snapButton.active = !waiting && !stale;
            snapButton.setMessage(label(snapEnabled ? "snap_on" : "snap_off"));
            snapButton.setTooltip(Tooltip.create(Component.translatable("gui.equipment_structure_api.placement.snap_key",
                    EquipmentAssemblyKeyMappings.TOGGLE_SNAP.getTranslatedKeyMessage())));
        }
        for (int i = 0; i < rows.size(); i++) {
            var button = rows.get(i); int index = listOffset + i;
            button.visible = index < slots.size(); button.active = !waiting && !stale;
            if (button.visible) {
                var id = slots.get(index);
                String prefix = id.equals(selected) ? "> " : "";
                String suffix = parts.get(id).visible() ? "" : " [" + label("hidden").getString() + "]";
                var message = Component.literal(font.plainSubstrByWidth(prefix + entryName(id).getString() + suffix, 120));
                if (!message.equals(button.getMessage())) {
                    button.setMessage(message);
                    button.setTooltip(Tooltip.create(entryTooltip(id, suffix)));
                }
            }
        }
    }
    @Override public void tick() {
        if (minecraft.player == null || minecraft.player.containerMenu != menu || !menu.stillValid(minecraft.player)) { minecraft.setScreen(null); return; }
        if (!waiting && !ItemStack.matches(original, menu.equipmentStack())) { stale = true; status = "changed"; }
        if (waiting && ++waitingTicks > 200) { waiting = false; stale = true; status = "timeout"; }
    }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float tick) { g.fill(0, 0, width, height, 0xFF181A1B); }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        gizmo.clear(); authorAnchor.clear(); capturedFrame = null; capturedPlan = null; orbitGizmo = null;
        renderPreview(g); updateWidgets();
        for (var widget : renderables) widget.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        g.fill(width - 151, 54, width - 150, previewBottom(), 0xFF515658);
        g.enableScissor(12, previewTop(), previewRight(), previewBottom());
        drawSelection(g, mouseX, mouseY); drawOrbitTrajectory(g, mouseX, mouseY); drawAxes(g, mouseX, mouseY); g.disableScissor();
        var name = selected == null ? label("whole") : entryName(selected);
        g.drawString(font, font.plainSubstrByWidth(name.getString(), 132), right(), 56, selected == null ? 0xFFDDDDDD : 0xFFFFD36A, false);
        String state = selected == null ? "camera_mode" : !EquipmentFeatureConfig.rules().positionEditing() ? "server_pose_disabled"
                : fixedAnchor() ? "fixed_part" : !gizmo.valid() ? "unavailable" : "selected_part";
        if (!compact() || selected == null) g.drawString(font, font.plainSubstrByWidth(label(state).getString(), 132), right(), 67, 0xFF9DA6A4, false);
        if (selected != null) {
            for (int i = 0; i < (mode == 2 ? 1 : 3); i++) g.drawString(font, mode == 2 ? "S" : "XYZ".substring(i, i + 1),
                    compact() ? right() + i * 44 + 2 : right(), compact() ? 68 : 84 + i * 26, 0xFFDDDDDD, false);
        } else if (!compact()) {
            g.drawString(font, label("yaw").getString() + " " + Math.round(yaw), right(), 86, 0xFFB7C4C1, false);
            g.drawString(font, label("pitch").getString() + " " + Math.round(pitch), right(), 108, 0xFFB7C4C1, false);
            g.drawString(font, label("zoom").getString() + " " + Math.round(zoom / 130 * 100) + "%", right(), 130, 0xFFB7C4C1, false);
        }
        g.drawString(font, label("parts").getString() + " " + slots.size(), right(), listTop() - 10, 0xFFDDDDDD, false);
        for (int i = 0; i < rows.size(); i++) {
            if (listOffset + i < slots.size() && slots.get(listOffset + i).equals(selected)) {
                var row = rows.get(i); g.renderOutline(row.getX(), row.getY(), row.getWidth(), row.getHeight(), 0xFFFFD36A);
            }
        }
        if (slots.size() > rows.size()) g.drawString(font, (listOffset + 1) + "–" + Math.min(slots.size(), listOffset + rows.size()) + "/" + slots.size(), width - 62, listTop() - 10, 0xFFB4BDBA, false);
        String message = !status.isEmpty() ? status : !validFields() ? "invalid" : "";
        long changed = parts.entrySet().stream().filter(e -> !e.getValue().equals(initial.get(e.getKey()))).count();
        var footer = message.isEmpty() ? Component.translatable("gui.equipment_structure_api.placement.changes", changed) : label(message);
        g.drawString(font, font.plainSubstrByWidth(footer.getString(), width - 24), 12, height - 44, 0xFFE9BA70, false);
    }
    private void renderPreview(GuiGraphics g) {
        g.flush(); g.enableScissor(12, previewTop(), previewRight(), previewBottom()); g.pose().pushPose();
        try {
            g.pose().translate((12 + previewRight()) / 2F + panX, (previewTop() + previewBottom()) / 2F + panY, 150);
            Lighting.setupFor3DItems(); selectionGeometry = new AppearancePreviewSelection(g.bufferSource(), selected);
            capturedFrame = null; capturedPlan = null; nativeFrames.clear(); updateSelectedFrame();
            if (previewRenderer == null) previewRenderer = new EquipmentAppearancePreviewRenderer(minecraft);
            previewRenderer.render(preview, selected, g.pose(), selectionGeometry, zoom, yaw, pitch,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, this::captureFrame,
                    (id, frame) -> { nativeFrames.put(id, new Matrix4f(frame)); updateSelectedFrame(); });
            g.flush();
        } finally { g.pose().popPose(); g.disableScissor(); Lighting.setupFor3DItems(); }
    }
    private void captureFrame(Matrix4f model, AppearancePlan plan) { capturedFrame = new Matrix4f(model); capturedPlan = plan; updateSelectedFrame(); }
    private void updateSelectedFrame() {
        gizmo.clear(); authorAnchor.clear(); selectedAnchor = null; orbitGizmo = null;
        if (selected != null && nativeFrames.containsKey(selected)) {
            var frame = nativeFrames.get(selected);
            var p = pose().transform().position();
            authorAnchor.update(frame, 0, 0, 0);
            gizmo.update(frame, (float) p.x(), (float) p.y(), (float) p.z());
            selectedAnchor = AppearanceTransform.IDENTITY;
            return;
        }
        if (selected == null || capturedFrame == null || capturedPlan == null) return;
        var placement = capturedPlan.placements().stream().filter(p -> p.slotId().equals(selected)).findFirst().orElse(null);
        if (placement == null) return;
        var part = AppearanceRuntime.catalog().components().get(placement.componentId());
        if (part == null) return;
        var pose = fixedAnchor() ? AppearancePose.IDENTITY : pose();
        var mount = part.mount().inverse(); var mp = mount.position();
        var scaledMount = new AppearanceTransform(new AppearanceVector(mp.x() * pose.scale(), mp.y() * pose.scale(), mp.z() * pose.scale()), mount.rotation());
        var attachment = placement.transform().compose(scaledMount.inverse()).compose(pose.transform().inverse());
        selectedAnchor = attachment;
        if (placement.visible() && placement.motion().isPresent() && placement.motion().get().speedDegreesPerSecond() > 0)
            orbitGizmo = new AppearanceOrbitGizmo(capturedFrame, placement.transform(), placement.motion().get());
        var p = attachment.position(); var r = attachment.rotation();
        var model = new Matrix4f(capturedFrame).translate((float) p.x(), (float) p.y(), (float) p.z())
                .rotate(new Quaternionf((float) r.x(), (float) r.y(), (float) r.z(), (float) r.w()));
        authorAnchor.update(model, 0, 0, 0); p = pose.transform().position(); gizmo.update(model, (float) p.x(), (float) p.y(), (float) p.z());
    }
    private void drawSelection(GuiGraphics g, int mouseX, int mouseY) {
        if (selectionGeometry == null) return;
        var hover = inPreview(mouseX, mouseY) ? selectionGeometry.hit(mouseX, mouseY) : null;
        var target = selected != null ? selected : hover;
        if (target == null) return;
        selectionGeometry.bounds(target).ifPresent(b -> {
            int x = (int) b.minX() - 3, y = (int) b.minY() - 3;
            int w = Math.max(8, (int) (b.maxX() - b.minX()) + 6), h = Math.max(8, (int) (b.maxY() - b.minY()) + 6);
            g.pose().pushPose(); g.pose().translate(0, 0, 500);
            g.renderOutline(x, y, w, h, selected != null ? 0xFFFFD36A : 0xFF8CD3FF); g.pose().popPose();
        });
    }
    private void drawAxes(GuiGraphics g, int mouseX, int mouseY) {
        if (!gizmo.valid() || !EquipmentTooltipConfig.showEditorGuides()) return;
        var origin = gizmo.origin(); g.pose().pushPose(); g.pose().translate(0, 0, 500);
        if (canEdit()) {
            int[] colors = {0xFFFF6868, 0xFF74DD89, 0xFF73ACFF}; int hover = gizmo.hit(mouseX, mouseY);
            for (int i = 0; i < 3; i++) {
                if (!gizmo.visible(i)) continue;
                var end = gizmo.end(i); int color = axis == i || hover == i ? 0xFFFFFF77 : colors[i];
                for (int step = 0; step <= 34; step++) {
                    int x = Math.round(origin.x + (end.x - origin.x) * step / 34), y = Math.round(origin.y + (end.y - origin.y) * step / 34);
                    g.fill(x - 1, y - 1, x + 1, y + 1, color);
                }
                g.fill((int) end.x - 3, (int) end.y - 3, (int) end.x + 3, (int) end.y + 3, color);
                g.drawString(font, "XYZ".substring(i, i + 1), (int) end.x + 5, (int) end.y - 4, color, false);
            }
            g.fill((int) origin.x - 3, (int) origin.y - 3, (int) origin.x + 4, (int) origin.y + 4, 0xFFFFFFFF);
        }
        if (authorAnchor.valid()) {
            var anchor = authorAnchor.origin(); g.renderOutline((int) anchor.x - 4, (int) anchor.y - 4, 9, 9, 0xFFEEB35B);
            int x = Math.clamp((int) anchor.x - font.width(label("author_anchor")) - 8, 14, Math.max(14, previewRight() - 90));
            int y = Math.clamp((int) anchor.y + 9, previewTop() + 2, Math.max(previewTop() + 2, previewBottom() - 12));
            g.drawString(font, label("author_anchor"), x, y, 0xFFEEB35B, false);
        }
        g.pose().popPose();
    }
    private AppearanceOrbitMotion selectedMotion() {
        if (selected == null) return null;
        var part = AppearanceRuntime.catalog().components().get(structure.component(selected).orElseThrow().id());
        return part == null ? null : part.motion().orElse(null);
    }
    private void cycleOrbitCenter() {
        if (!canEdit() || !validFields() || selectedMotion() == null) return;
        var settings = parts.get(selected).motion();
        var values = AppearanceMotionSettings.Center.values();
        checkpoint();
        parts.put(selected, parts.get(selected).withMotion(new AppearanceMotionSettings(
                values[(settings.center().ordinal() + 1) % values.length], settings.tilt())));
        rebuildPreview(); updateWidgets();
    }
    /** A single sampled path is shared by drawing and hit testing. */
    private void drawOrbitTrajectory(GuiGraphics g, int mouseX, int mouseY) {
        if (!EquipmentTooltipConfig.showEditorGuides() || orbitGizmo == null) return;
        boolean hover = inPreview(mouseX, mouseY) && orbitGizmo.hit(mouseX, mouseY).distance() <= 12;
        int color = dragOrbit || hover ? 0xFFFFFF77 : 0xB8E8B75E;
        g.pose().pushPose(); g.pose().translate(0, 0, 499);
        var points = orbitGizmo.points();
        for (int i = 1; i < points.size(); i++) drawGuideLine(g, points.get(i - 1), points.get(i), color);
        var placement = capturedPlan.placements().stream().filter(p -> p.slotId().equals(selected)).findFirst().orElseThrow();
        var motion = placement.motion().orElseThrow();
        var pivot = orbitGizmo.project(motion.pivot());
        var axisEnd = orbitGizmo.project(motion.pivot().add(new AppearanceVector(
                motion.axis().x() * 8, motion.axis().y() * 8, motion.axis().z() * 8)));
        drawGuideLine(g, pivot, axisEnd, 0xFF76CBE8);
        g.renderOutline(Math.round(pivot.x) - 3, Math.round(pivot.y) - 3, 7, 7, 0xFF76CBE8);
        var current = points.getFirst(); // Editor freezes at base pose, rather than pretending to show live time.
        g.fill(Math.round(current.x) - 3, Math.round(current.y) - 3,
                Math.round(current.x) + 4, Math.round(current.y) + 4, 0xFFE8B75E);
        if (hover || dragOrbit) g.drawString(font, label(tiltOrbit ? "orbit_tilt" : "orbit_radius"), 16, previewTop() + 4, color, false);
        g.pose().popPose();
    }
    private void drawGuideLine(GuiGraphics g, Vector3f a, Vector3f b, int color) {
        // Bound work even if an author's resource puts the line far outside the viewport.
        int steps = Math.clamp((int) Math.ceil(Math.hypot(b.x - a.x, b.y - a.y)), 1, 1024);
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = Math.round(a.x + (b.x - a.x) * t), y = Math.round(a.y + (b.y - a.y) * t);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
    private boolean orbitHit(double x, double y) {
        if (!EquipmentTooltipConfig.showEditorGuides() || !canEdit() || orbitGizmo == null) return false;
        var hit = orbitGizmo.hit(x, y);
        if (hit.distance() > 12) return false;
        orbitDragAngle = hit.angle(); return true;
    }
    private void dragOrbit(double dx, double dy) {
        if (orbitGizmo == null || selectedAnchor == null) return;
        if (!dragCheckpoint) { checkpoint(); dragCheckpoint = true; }
        double fine = hasShiftDown() ? 0.1 : 1;
        var placement = capturedPlan.placements().stream().filter(p -> p.slotId().equals(selected)).findFirst().orElseThrow();
        var settings = parts.get(selected).motion();
        AppearanceVector delta;
        if (tiltOrbit) {
            var turn = orbitGizmo.tilt(dx * fine, dy * fine);
            var pivot = placement.motion().orElseThrow().pivot();
            var offset = placement.transform().position().add(pivot.negate());
            delta = turn.apply(offset).add(offset.negate());
            parts.put(selected, parts.get(selected).withMotion(new AppearanceMotionSettings(
                    settings.center(), turn.compose(settings.tilt()))));
        } else delta = orbitGizmo.radiusDelta(orbitDragAngle, dx * fine, dy * fine);
        // Pose translations are stored in the attachment frame, whereas the path is in host coordinates.
        delta = selectedAnchor.rotation().inverse().apply(delta);
        var pose = pose(); var p = pose.transform().position();
        change(new AppearancePose(Math.clamp(p.x() + delta.x(), -32, 32),
                Math.clamp(p.y() + delta.y(), -32, 32), Math.clamp(p.z() + delta.z(), -32, 32),
                pose.transform().rotation(), pose.scale()));
    }
    private boolean inPreview(double x, double y) { return x >= 12 && x < previewRight() && y >= previewTop() && y < previewBottom(); }
    private void select(ResourceLocation slot) {
        if (waiting || stale || !validFields() || Objects.equals(selected, slot)) return;
        selected = slot; setFocused(null); status = "";
        ensureSelectedRow();
        updateSelectedFrame(); fillFields(); updateWidgets();
    }
    private void ensureSelectedRow() {
        listOffset = Math.clamp(listOffset, 0, Math.max(0, slots.size() - rows.size()));
        if (selected == null) return;
        int index = slots.indexOf(selected);
        if (index < listOffset) listOffset = index;
        if (index >= listOffset + rows.size()) listOffset = Math.max(0, index - rows.size() + 1);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!(getFocused() instanceof EditBox) && editorKeyAction(InputConstants.Type.MOUSE.getOrCreate(button))) return true;
        if (super.mouseClicked(x, y, button)) return true;
        if (!inPreview(x, y) || waiting || stale || button > 2) return false;
        if (!validFields()) return true;
        setFocused(null); dragButton = button; dragCheckpoint = false; dragDistance = 0;
        dragPart = false; dragOrbit = false; axis = -1;
        if (button == 0) {
            axis = canEdit() && EquipmentTooltipConfig.showEditorGuides() ? gizmo.hit(x, y) : -1;
            boolean center = canEdit() && EquipmentTooltipConfig.showEditorGuides() && Math.hypot(x - gizmo.origin().x, y - gizmo.origin().y) <= 6;
            if (axis >= 0 || center) dragPart = true;
            else if (orbitHit(x, y)) dragOrbit = true;
            else { var hit = selectionGeometry == null ? null : selectionGeometry.hit(x, y); select(hit); dragPart = hit != null && canEdit(); }
        }
        return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragButton != button || waiting || stale) return super.mouseDragged(x, y, button, dx, dy);
        dragDistance += Math.hypot(dx, dy); if (dragDistance < 2) return true;
        if (dragOrbit && canEdit()) {
            dragOrbit(dx, dy);
        } else if (dragPart && canEdit()) {
            if (!dragCheckpoint) { checkpoint(); dragCheckpoint = true; }
            double fine = hasShiftDown() ? 0.1 : 1; var pose = pose(); var p = pose.transform().position();
            if (mode == 0) {
                var delta = axis >= 0 ? new Vector3f().setComponent(axis, (float) (gizmo.amount(axis, dx, dy) * fine)) : gizmo.planeDelta(dx * fine, dy * fine);
                change(new AppearancePose(Math.clamp(p.x() + delta.x, -32, 32), Math.clamp(p.y() + delta.y, -32, 32),
                        Math.clamp(p.z() + delta.z, -32, 32), pose.transform().rotation(), pose.scale()));
            } else if (mode == 1) {
                double angle = Math.toRadians((axis >= 0 ? gizmo.amount(axis, dx, dy) * 5 : dx - dy) * fine) / 2;
                double[] q = {0, 0, 0}; q[axis >= 0 ? axis : 2] = Math.sin(angle);
                change(new AppearancePose(new AppearanceTransform(p, new AppearanceRotation(q[0], q[1], q[2], Math.cos(angle)).compose(pose.transform().rotation())), pose.scale()));
            } else {
                double amount = (axis >= 0 ? gizmo.amount(axis, dx, dy) : (dx - dy) * 0.25) * fine;
                change(new AppearancePose(pose.transform(), Math.clamp(pose.scale() * Math.exp(amount * 0.02), 0.05, 2)));
            }
        } else if (button == 2 || button == 1 && hasShiftDown()) { panX += (float) dx; panY += (float) dy; }
        else { yaw += (float) dx * 0.65F; pitch = Math.clamp(pitch + (float) dy * 0.65F, -89, 89); }
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        boolean dragged = dragButton >= 0;
        if (dragged && dragPart && !dragOrbit && dragDistance >= 2 && mode == 0 && snapEnabled && canEdit()
                && selectionGeometry != null) {
            var origin = gizmo.origin();
            selectionGeometry.nearestHostPoint(origin.x, origin.y).ifPresent(target -> {
                double dx = target.x() - origin.x, dy = target.y() - origin.y;
                if (dx * dx + dy * dy > 24 * 24) return;
                var current = pose();
                var delta = gizmo.planeDelta(dx, dy);
                var p = current.transform().position();
                var next = new AppearancePose(Math.clamp(p.x() + delta.x, -32, 32),
                        Math.clamp(p.y() + delta.y, -32, 32), Math.clamp(p.z() + delta.z, -32, 32),
                        current.transform().rotation(), current.scale());
                next = constrained(next);
                if (!next.equals(current)) {
                    if (!dragCheckpoint) checkpoint();
                    parts.put(selected, parts.get(selected).withPose(next));
                    rebuildPreview(); fillFields(); updateWidgets();
                }
            });
        }
        dragButton = -1; axis = -1; dragPart = false; dragOrbit = false;
        return super.mouseReleased(x, y, button) || dragged;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= right() && y >= listTop() && y < previewBottom()) {
            listOffset = Math.clamp(listOffset - (int) Math.signum(vertical), 0, Math.max(0, slots.size() - rows.size())); updateWidgets(); return true;
        }
        if (!inPreview(x, y)) return super.mouseScrolled(x, y, horizontal, vertical);
        zoom = Math.clamp(zoom * (float) Math.pow(1.1, vertical), 24, 600); return true;
    }
    private void setMode(int value) {
        if (!EquipmentFeatureConfig.rules().positionEditing() || selected == null || fixedAnchor() || waiting || stale || !validFields()) return;
        mode = value; setFocused(null); fillFields(); updateWidgets();
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (selected != null && !waiting && !stale && validFields()) select(null); else onClose(); return true;
        }
        if (getFocused() instanceof EditBox) return super.keyPressed(key, scan, modifiers);
        if (editorKeyAction(InputConstants.getKey(key, scan))) return true;
        if (EquipmentAssemblyKeyMappings.OPEN_ASSEMBLY.isActiveAndMatches(InputConstants.getKey(key, scan))) { onClose(); return true; }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && confirm.active) { confirmPlacement(); return true; }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && !waiting && !stale) {
            if (key == GLFW.GLFW_KEY_Z) { restore(undo, redo); return true; }
            if (key == GLFW.GLFW_KEY_Y) { restore(redo, undo); return true; }
        }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_3) { setMode(key - GLFW.GLFW_KEY_1); return true; }
        if (key == GLFW.GLFW_KEY_TAB) {
            int current = selected == null ? -1 : slots.indexOf(selected);
            int next = Math.floorMod(current + ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1) + 1, slots.size() + 1) - 1;
            select(next < 0 ? null : slots.get(next)); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    private boolean editorKeyAction(InputConstants.Key key) {
        if (EquipmentAssemblyKeyMappings.RESET_PART.isActiveAndMatches(key)) { resetPart(); return true; }
        if (EquipmentAssemblyKeyMappings.RESET_VIEW.isActiveAndMatches(key)) { resetView(); return true; }
        if (EquipmentAssemblyKeyMappings.FOCUS_PART.isActiveAndMatches(key)) { focusSelection(); return true; }
        if (EquipmentAssemblyKeyMappings.TOGGLE_SNAP.isActiveAndMatches(key)) { toggleSnap(); return true; }
        return false;
    }
    private void toggleSnap() {
        if (waiting || stale) return;
        snapEnabled = !snapEnabled;
        updateWidgets();
    }
    private Component viewHelp() {
        return Component.translatable("gui.equipment_structure_api.placement.view_help",
                EquipmentAssemblyKeyMappings.RESET_VIEW.getTranslatedKeyMessage(), EquipmentAssemblyKeyMappings.FOCUS_PART.getTranslatedKeyMessage());
    }
    private void resetPart() {
        if (!canEdit()) return;
        if (!pose().equals(constrained(AppearancePose.IDENTITY)) || !parts.get(selected).motion().equals(AppearanceMotionSettings.DEFAULT)) checkpoint();
        parts.put(selected, parts.get(selected).withMotion(AppearanceMotionSettings.DEFAULT));
        change(AppearancePose.IDENTITY); // Retains visibility, obeys author bounds and remains undoable until saved.
        setFocused(null);
    }
    private void resetView() { yaw = -25; pitch = 15; zoom = 130; panX = panY = 0; }
    private void focusSelection() {
        if (selectionGeometry == null) return;
        var bounds = (selected == null ? slots.stream() : java.util.stream.Stream.of(selected)).flatMap(id -> selectionGeometry.bounds(id).stream()).toList();
        if (bounds.isEmpty()) return;
        float minX = Float.POSITIVE_INFINITY, minY = minX, maxX = -minX, maxY = -minX;
        for (var b : bounds) { minX = Math.min(minX, b.minX()); minY = Math.min(minY, b.minY()); maxX = Math.max(maxX, b.maxX()); maxY = Math.max(maxY, b.maxY()); }
        float cx = (12 + previewRight()) / 2F, cy = (previewTop() + previewBottom()) / 2F;
        float factor = Math.min((previewRight() - 36) / Math.max(1F, maxX - minX), (previewBottom() - previewTop() - 24) / Math.max(1F, maxY - minY)) * 0.75F;
        float next = Math.clamp(zoom * factor, 24, 600); factor = next / zoom;
        panX = (panX - (minX + maxX) / 2 + cx) * factor; panY = (panY - (minY + maxY) / 2 + cy) * factor; zoom = next;
    }
    @Override public void onClose() { if (!waiting) { EquipmentAssemblyKeyEvents.clearQueuedClicks(); minecraft.popGuiLayer(); } }
    private void confirmPlacement() {
        if (waiting || stale || !validFields() || minecraft.player == null || minecraft.player.containerMenu != menu || !ItemStack.matches(original, menu.equipmentStack())) return;
        var edits = new LinkedHashMap<ResourceLocation, AppearancePartPresentation>();
        parts.forEach((id, part) -> { if (!part.equals(initial.get(id))) edits.put(id, part); });
        if (edits.size() > SetAppearanceLayoutPayload.MAX_EDITS) { status = "too_many"; return; }
        waiting = true; waitingTicks = 0; status = "saving";
        PacketDistributor.sendToServer(new SetAppearanceLayoutPayload(menu.containerId, original, editorSlot, edits, originalVisible));
    }
    private Draft snapshot() { return new Draft(parts, originalVisible, selected); }
    private void checkpoint() {
        var draft = snapshot(); if (undo.isEmpty() || !undo.peek().equals(draft)) undo.push(draft);
        if (undo.size() > 100) undo.removeLast(); redo.clear();
    }
    private void restore(ArrayDeque<Draft> from, ArrayDeque<Draft> to) {
        if (from.isEmpty() || waiting || stale) return;
        to.push(snapshot()); var draft = from.pop(); parts.clear(); parts.putAll(draft.parts());
        originalVisible = draft.originalVisible(); selected = draft.selected(); status = "";
        ensureSelectedRow(); rebuildPreview(); updateSelectedFrame(); fillFields(); updateWidgets();
    }
    private void change(AppearancePose next) {
        if (selected == null) return;
        next = constrained(next);
        parts.put(selected, parts.get(selected).withPose(next)); rebuildPreview(); fillFields(); updateWidgets();
    }
    private void toggleVisibility(boolean originalModel) {
        if (waiting || stale || !originalModel && selected == null) return;
        var rules = EquipmentFeatureConfig.rules();
        if (originalModel ? !rules.hideOriginal() && originalVisible : !rules.hideAttachments() && parts.get(selected).visible()) return;
        checkpoint(); if (originalModel) originalVisible = !originalVisible;
        else parts.put(selected, parts.get(selected).withVisible(!parts.get(selected).visible()));
        rebuildPreview(); updateWidgets();
    }
    private void rebuildPreview() {
        preview = original.copy(); var components = new HashMap<>(structure.components());
        parts.forEach((id, part) -> { if (!part.equals(initial.get(id))) components.put(id, List.of(part.apply(structure.component(id).orElseThrow()))); });
        EquipmentStructureApi.setStructure(preview, new EquipmentStructure(structure.hostId(),
                structure.equipmentType(), structure.slots(), components, structure.version(), structure.grid()));
        AppearanceVisibilityStorage.setOriginalVisible(preview, originalVisible);
    }
    private double[] values() {
        var pose = pose(); var p = pose.transform().position();
        if (mode == 0) return new double[]{p.x(), p.y(), p.z()};
        if (mode == 2) return new double[]{pose.scale(), 0, 0};
        var r = pose.transform().rotation();
        var angles = new Quaternionf((float) r.x(), (float) r.y(), (float) r.z(), (float) r.w()).getEulerAnglesZYX(new Vector3f());
        return new double[]{Math.toDegrees(angles.x), Math.toDegrees(angles.y), Math.toDegrees(angles.z)};
    }
    private void fillFields() {
        filling = true; double[] values = values();
        for (int i = 0; i < 3; i++) if (fields[i] != null) fields[i].setValue(String.format(Locale.ROOT, "%.3f", values[i]));
        filling = false;
    }
    private boolean validNumber(double v) { return Double.isFinite(v) && (mode == 2 ? v >= 0.05 && v <= 2 : Math.abs(v) <= (mode == 0 ? 32 : 180)); }
    private boolean validFields() {
        if (selected == null || fields[0] == null) return true;
        try { for (int i = 0; i < (mode == 2 ? 1 : 3); i++) if (!validNumber(Double.parseDouble(fields[i].getValue()))) return false; return true; }
        catch (NumberFormatException invalid) { return false; }
    }
    private void editNumber(int field, String text) {
        if (filling || !canEdit()) return;
        try {
            double value = Double.parseDouble(text); if (!validNumber(value)) return;
            double[] values = values(); values[field] = value; var pose = pose(); AppearancePose next;
            if (mode == 0) next = new AppearancePose(values[0], values[1], values[2], pose.transform().rotation(), pose.scale());
            else if (mode == 2) next = new AppearancePose(pose.transform(), value);
            else {
                var q = new Quaternionf().rotationZYX((float) Math.toRadians(values[2]), (float) Math.toRadians(values[1]), (float) Math.toRadians(values[0]));
                next = new AppearancePose(new AppearanceTransform(pose.transform().position(), new AppearanceRotation(q.x, q.y, q.z, q.w)), pose.scale());
            }
            next = constrained(next);
            if (!next.equals(pose)) { checkpoint(); parts.put(selected, parts.get(selected).withPose(next)); rebuildPreview(); }
        } catch (NumberFormatException invalid) { /* Keep partial input until corrected or undone. */ }
    }
    private static Component label(String key) { return Component.translatable("gui.equipment_structure_api.placement." + key); }

    /** Shares host-local names with the assembly screen. */
    private Component slotName(ResourceLocation slotId) {
        return dev.equipmentstructure.api.ui.EquipmentStructureUiRegistry.get(structure.hostId(), structure.slots()).slot(slotId).name(slotId);
    }

    private Component entryName(ResourceLocation slotId) {
        return slotName(slotId)
                .copy().append(" · ").append(names.getOrDefault(slotId, Component.literal(slotId.getPath())));
    }

    private Component entryTooltip(ResourceLocation slotId, String suffix) {
        var definition = structure.slot(slotId).orElse(null);
        var part = structure.component(slotId).orElse(null);
        if (definition == null || part == null) return entryName(slotId).copy().append("\n").append(slotId.toString()).append(suffix);
        var key = dev.equipmentstructure.api.compat.curios.CuriosSlotKey.parse(slotId).orElse(null);
        Component interfaceName = key == null ? typeName(definition.interfaceType(), "interface")
                : Component.translatableWithFallback("curios.identifier." + key.type(), key.type());
        Component componentType = key == null ? typeName(definition.componentType(), "component_type") : interfaceName;
        var tooltip = Component.translatable("gui.equipment_structure_api.placement.slot_info", slotName(slotId))
                .append("\n").append(Component.translatable("gui.equipment_structure_api.placement.interface_info", interfaceName))
                .append("\n").append(Component.translatable("gui.equipment_structure_api.placement.component_type_info", componentType))
                .append("\n").append(Component.translatable("gui.equipment_structure_api.placement.component_info", names.get(slotId)));
        if (minecraft.options.advancedItemTooltips)
            tooltip.append("\n").append(Component.translatable("gui.equipment_structure_api.placement.slot_id_info", definition.id().toString()));
        return tooltip.append(suffix);
    }

    private static Component typeName(ResourceLocation id, String kind) {
        return Component.translatableWithFallback(kind + "." + id.getNamespace() + "." + id.getPath().replace('/', '.'), id.toString());
    }

}
