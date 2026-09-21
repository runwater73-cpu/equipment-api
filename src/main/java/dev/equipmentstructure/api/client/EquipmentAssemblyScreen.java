package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.client.appearance.AppearanceGuiRenderer;
import dev.equipmentstructure.api.client.appearance.AppearanceGuiAssetRegistry;
import dev.equipmentstructure.api.client.appearance.AppearanceRuntime;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenus;
import dev.equipmentstructure.api.menu.EquipmentAssemblyLayout;
import dev.equipmentstructure.api.menu.EquipmentAssemblyLimits;
import dev.equipmentstructure.api.ui.EquipmentStructureUiDefinition;
import dev.equipmentstructure.api.network.ClearInterfacePayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.ArrayList;

/** Compact equipment structure view with an inventory-anchored reveal animation. */
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID, value = Dist.CLIENT)
public final class EquipmentAssemblyScreen extends AbstractContainerScreen<EquipmentAssemblyMenu> {
    private static final int PANEL_PADDING = 9;
    private static final int GUI_WIDTH = EquipmentAssemblyLayout.GUI_WIDTH;
    private static final int GUI_HEIGHT = EquipmentAssemblyLayout.GUI_HEIGHT;
    private static final int TEXTURE_SIZE = 512;
    private static final int UPPER_HEIGHT = EquipmentAssemblyLayout.INVENTORY_Y;
    private static final int INVENTORY_Y = EquipmentAssemblyLayout.INVENTORY_Y;
    private static final int REVEAL_MILLIS = 430;
    private static final int STAT_VALUE_OFFSET = 38;
    private static final int STAT_COLUMN_GAP = 2;
    private static final int PLACEMENT_BUTTON_WIDTH = 64;
    private static final int PLACEMENT_BUTTON_HEIGHT = 14;
    private static final int PREVIEW_NAME_Y = 80;
    private static final int PREVIEW_ACTION_GAP = 2;

    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID,
                    "textures/gui/monochrome.png");
    private static final Region EQUIPMENT_PREVIEW = new Region(28, 2, 96, 96);
    private static final Region INVENTORY_COMPLETE = new Region(180, 148, 176, 90);
    private static final Region EQUIPMENT_INPUT = new Region(42, 252, 28, 28);

    private final long openTime = Util.getMillis();
    private final EquipmentAssemblyUiLayout uiLayout = EquipmentAssemblyUiLayout.standard();
    private final EquipmentDetailsView detailView = new EquipmentDetailsView();
    private ResourceLocation selectedInterface;
    private ResourceLocation selectionHost;
    private Item selectionEquipmentItem;
    private List<EquipmentSlotDefinition> selectionDefinitions = List.of();
    private EquipmentAssemblyDisplaySnapshot displaySnapshot;
    private ItemStack snapshotEquipment = ItemStack.EMPTY;
    private boolean refreshDisplay = true;
    private boolean consumeNodeRelease;
    private ResourceLocation pendingPlacement;
    private ResourceLocation pendingQuickSelection;
    private net.minecraft.client.gui.components.Button placementButton;
    private final EquipmentAssemblyGridView gridView;

    public EquipmentAssemblyScreen(EquipmentAssemblyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        gridView = new EquipmentAssemblyGridView(menu);
        imageWidth = GUI_WIDTH;
        imageHeight = GUI_HEIGHT;
        inventoryLabelY = -1000;
        titleLabelY = -1000;
    }

    @Override protected void init() {
        super.init();
        gridView.cancelGesture();
        EquipmentAssemblyUiLayout.Panel preview = uiLayout.preview();
        // Reserve the name row below the action and leave the authored frame clear.
        int buttonX = leftPos + preview.x() + (preview.width() - PLACEMENT_BUTTON_WIDTH) / 2;
        int buttonY = topPos + preview.y() + PREVIEW_NAME_Y - PREVIEW_ACTION_GAP - PLACEMENT_BUTTON_HEIGHT;
        placementButton = addRenderableWidget(EquipmentStructureButton.builder(
                Component.translatable("gui.equipment_structure_api.placement.edit"),
                ignored -> requestPlacement(selectedInterface)).bounds(buttonX, buttonY,
                PLACEMENT_BUTTON_WIDTH, PLACEMENT_BUTTON_HEIGHT)
                .build(EquipmentStructureButton::new));
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(EquipmentAssemblyMenus.ASSEMBLY.get(), EquipmentAssemblyScreen::new);
    }

    /** Keep the vanilla HUD hotbar from rendering underneath the custom inventory layer. */
    @SubscribeEvent
    public static void hideVanillaHud(RenderGuiEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof EquipmentAssemblyScreen
                || Minecraft.getInstance().screen instanceof EquipmentAppearancePlacementScreen) {
            event.setCanceled(true);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshDisplay = true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // All visible information for this frame comes from one immutable
        // read. Layout measurement and drawing therefore see identical data.
        EquipmentAssemblyDisplaySnapshot previousSnapshot = displaySnapshot;
        // Author callbacks and component item factories run at most once per client tick
        // while idle. A synchronized equipment change still appears in the next frame.
        if (refreshDisplay || displaySnapshot == null || !ItemStack.matches(snapshotEquipment, menu.equipmentStack())) {
            displaySnapshot = EquipmentAssemblyDisplaySnapshot.capture(menu, selectedInterface);
            snapshotEquipment = menu.equipmentStack().copy();
            refreshDisplay = false;
        }
        ResourceLocation selectionBeforeUpdate = selectedInterface;
        updateSelection(previousSnapshot, displaySnapshot);
        // An equipment/schema sync or component removal can clear the selected ID. Recapture now so
        // the details panel cannot render the previous component for one frame.
        if (!Objects.equals(selectionBeforeUpdate, selectedInterface)) {
            displaySnapshot = EquipmentAssemblyDisplaySnapshot.capture(menu, selectedInterface);
        }
        if (pendingQuickSelection != null) {
            if (EquipmentStructureApi.component(menu.equipmentStack(), pendingQuickSelection).isPresent()) {
                selectedInterface = pendingQuickSelection;
                gridView.select(selectedInterface);

                displaySnapshot = EquipmentAssemblyDisplaySnapshot.capture(menu, selectedInterface);
            }
            pendingQuickSelection = null;
        }
        gridView.refresh(displaySnapshot);
        if (!Objects.equals(selectedInterface, gridView.selected())) {
            selectedInterface = gridView.selected();
            displaySnapshot = EquipmentAssemblyDisplaySnapshot.capture(menu, selectedInterface);
        }
        placementButton.visible = !detailView.active() && revealProgress(0) >= 0.98F
                && EquipmentStructureApi.structure(menu.equipmentStack()).map(s -> !s.components().isEmpty()).orElse(false);
        placementButton.active = !gridView.busy();
        if (launchPlacementAfterInstall(displaySnapshot)) return;
        if (displaySnapshot.equipment().isEmpty()) detailView.open(EquipmentDetailsView.Page.HOME, null);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!detailView.active()) {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (detailView.active()) return;
        if (gridView.tooltip(graphics, font, leftPos, topPos, mouseX, mouseY)) return;
        if (renderInformationEntryHover(graphics, mouseX, mouseY)) return;
        if (hoveredSlot != null && hoveredSlot.index >= EquipmentAssemblyMenu.PART_SLOT_START
                && hoveredSlot.index < EquipmentAssemblyMenu.PART_SLOT_START + EquipmentAssemblyMenu.stagingSlotCount()) {
            return;
        }
        // Real inventory and host-input slots retain vanilla item tooltips and mod hooks.
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    public void requestPlacement(ResourceLocation slot) {
        gridView.closeSpacePanel();
        if (gridView.busy()) return;
        gridView.cancelGesture();
        var structure = EquipmentStructureApi.structure(menu.equipmentStack()).orElse(null);
        pendingPlacement = structure == null ? null : slot != null && structure.component(slot).isPresent() ? slot
                : structure.slots().stream().map(dev.equipmentstructure.api.EquipmentSlotDefinition::id)
                        .filter(id -> structure.component(id).isPresent()).findFirst().orElse(null);
    }

    public static void receiveSelection(dev.equipmentstructure.api.network.AssemblySelectionPayload payload) {
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen instanceof EquipmentAssemblyScreen screen
                && mc.player.containerMenu == screen.menu && screen.menu.containerId == payload.containerId()) {
            screen.pendingQuickSelection = payload.slotId();
        }
    }

    /** Wait for vanilla slot synchronization after the server's installation notification. */
    private boolean launchPlacementAfterInstall(EquipmentAssemblyDisplaySnapshot snapshot) {
        if (pendingPlacement == null || minecraft.screen != this
                || EquipmentStructureApi.component(menu.equipmentStack(), pendingPlacement).isEmpty()) return false;
        selectedInterface = pendingPlacement;

        pendingPlacement = null;
        minecraft.pushGuiLayer(new EquipmentAppearancePlacementScreen(this, selectedInterface));
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The presentation uses transparent PNG layers only. Do not add the
        // default screen dimmer behind the GUI assets.
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (detailView.active()) {
            detailView.render(graphics, font, leftPos, topPos, mouseX, mouseY, currentSnapshot());
            return;
        }
        int left = leftPos;
        int top = topPos;
        float progress = revealProgress(partialTick);
        // The vanilla-sized inventory is visible from the first frame. Its
        // frames are baked into inventory_complete, so item slots only render
        // item icons later in renderSlot.
        blitRegion(graphics, INVENTORY_COMPLETE, left + uiLayout.inventory().x(),
                top + uiLayout.inventory().y());
        if (progress <= 0.0F) {
            return;
        }
        if (progress >= 1.0F) {
            // Once the reveal finishes, do not impose a logical bottom edge
            // on authored information panels. The game viewport clips pixels
            // outside the screen, while GUI scale remains the player's way to
            // inspect a taller panel.
            drawUpperArea(graphics, left, top, mouseX - left, mouseY - top);
            return;
        }
        int revealTop = top + Mth.ceil(UPPER_HEIGHT * (1.0F - progress));
        int revealBottom = top + INVENTORY_Y;
        // The preview panels intentionally sit 20 px to the left of the
        // vanilla inventory anchor; include that gutter in the reveal clip.
        graphics.enableScissor(left, revealTop, left + GUI_WIDTH, revealBottom);
        drawUpperArea(graphics, left, top, mouseX - left, mouseY - top);
        graphics.disableScissor();
    }

    private void drawUpperArea(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        EquipmentAssemblyPanelRenderer.render(graphics, left, top, uiLayout.workspace());
        var snapshot = currentSnapshot();
        renderPreviewPanel(graphics, left, top, snapshot);
        if (snapshot.equipment().isEmpty()) return;
        gridView.render(graphics, font, left, top, mouseX, mouseY);
    }

    private void renderPreviewPanel(GuiGraphics graphics, int left, int top,
                                    EquipmentAssemblyDisplaySnapshot snapshot) {
        ItemStack equipment = snapshot.equipment();
        blitRegion(graphics, EQUIPMENT_PREVIEW, left + uiLayout.preview().x(),
                top + uiLayout.preview().y());
        EquipmentStructureUiDefinition.Texts labels = snapshot.uiDefinition().texts();
        graphics.drawString(font, font.plainSubstrByWidth(
                        Component.translatable(labels.preview()).getString(),
                        uiLayout.preview().width() - PANEL_PADDING * 2),
                left + uiLayout.preview().x() + PANEL_PADDING,
                top + uiLayout.preview().y() + PANEL_PADDING,
                snapshot.uiDefinition().accentColor(), false);
        blitRegion(graphics, EQUIPMENT_INPUT, left + EquipmentAssemblyLayout.equipmentFrameX(),
                top + EquipmentAssemblyLayout.equipmentFrameY());
        if (equipment.isEmpty()) {
            graphics.drawString(font, font.plainSubstrByWidth(
                            Component.translatable("gui.equipment_structure_api.assembly.empty").getString(),
                            uiLayout.preview().width() - PANEL_PADDING * 2),
                    left + uiLayout.preview().x() + PANEL_PADDING,
                    top + uiLayout.preview().y() + PREVIEW_NAME_Y, 0xFF999999, false);
            return;
        }
        List<EquipmentAssemblyDisplaySnapshot.EquipmentStatRow> statRows = snapshot.equipmentStats();
        EquipmentAssemblyUiLayout.Panel stats = dynamicStatsPanel(statRows);
        EquipmentAssemblyPanelRenderer.render(graphics, left, top, stats);
        graphics.drawString(font, font.plainSubstrByWidth(
                        Component.translatable(labels.stats()).getString(),
                        stats.width() - PANEL_PADDING * 2),
                left + stats.x() + PANEL_PADDING, top + stats.y() + PANEL_PADDING,
                snapshot.uiDefinition().accentColor(), false);

        renderEquipmentPreview(graphics, left, top, snapshot);
        renderStats(graphics, left, top, stats, statRows);
    }

    /** Calculates the left panel height from the same rows that are rendered. */
    private EquipmentAssemblyUiLayout.Panel dynamicStatsPanel(
            List<EquipmentAssemblyDisplaySnapshot.EquipmentStatRow> rows) {
        if (rows.isEmpty()) return uiLayout.stats(EquipmentAssemblyInfoLayout.MIN_HEIGHT);
        List<Integer> rowLines = new ArrayList<>();
        for (EquipmentAssemblyDisplaySnapshot.EquipmentStatRow row : rows) {
            rowLines.add(statRowLines(row));
        }
        // The panel sits in the left gutter, beside (not above) the inventory.
        // Its lower edge may therefore use the full GUI height.
        int height = EquipmentAssemblyInfoLayout.heightForStatRows(rowLines,
                Integer.MAX_VALUE);
        return uiLayout.stats(height);
    }

    private int statRowLines(EquipmentAssemblyDisplaySnapshot.EquipmentStatRow row) {
        int labelWidth = STAT_VALUE_OFFSET - STAT_COLUMN_GAP;
        int valueWidth = EquipmentAssemblyLayout.COMPONENT_INFO_WIDTH
                - PANEL_PADDING * 2 - STAT_VALUE_OFFSET;
        float valueScale = EquipmentAssemblyInfoLayout.statValueScale(
                row.value().getString(), font.width(row.value()), valueWidth);
        return Math.max(1, Math.max(font.split(row.label(), labelWidth).size(),
                font.split(row.value(), (int) Math.floor(valueWidth / valueScale)).size()));
    }

    private void updateSelection(EquipmentAssemblyDisplaySnapshot previousSnapshot,
                                 EquipmentAssemblyDisplaySnapshot snapshot) {
        List<EquipmentSlotDefinition> definitions = snapshot.definitions();
        ResourceLocation hostId = snapshot.hostId().orElse(null);
        Item currentEquipmentItem = snapshot.equipment().getItem();
        if (!EquipmentAssemblyLimits.supports(definitions.size())) {
            clearSelection();
            return;
        }
        if (!Objects.equals(selectionHost, hostId)
                || selectionEquipmentItem != currentEquipmentItem
                || !selectionDefinitions.equals(definitions)) {
            selectionHost = hostId;
            selectionEquipmentItem = currentEquipmentItem;
            selectionDefinitions = List.copyOf(definitions);
            clearSelection();
        }
        if (selectedInterface != null && previousSnapshot != null
                && previousSnapshot.selectedComponent(selectedInterface)
                        .flatMap(EquipmentAssemblyDisplaySnapshot.ComponentInfoSnapshot::installed).isPresent()
                && snapshot.selectedComponent(selectedInterface)
                        .flatMap(EquipmentAssemblyDisplaySnapshot.ComponentInfoSnapshot::installed).isEmpty()) {
            // Empty nodes cannot dismiss their details by clicking. Close only after a
            // previously installed component disappears from the synchronized host snapshot;
            // a newly selected empty target may still be waiting for installation to arrive.
            clearSelection();
        }
    }

    private void renderEquipmentPreview(GuiGraphics graphics, int left, int top,
                                        EquipmentAssemblyDisplaySnapshot snapshot) {
        ItemStack equipment = snapshot.equipment();
        int anchorX = left + EquipmentAssemblyLayout.equipmentCoreX();
        int anchorY = top + EquipmentAssemblyLayout.equipmentCoreY();
        // The original item remains the authoritative fallback. Explicitly
        // registered appearance assets are drawn on top of the same anchor;
        // no component ItemStack is inferred as a visual resource.
        graphics.renderItem(equipment, anchorX - 8, anchorY - 8);
        graphics.renderItemDecorations(font, equipment, anchorX - 8, anchorY - 8);
        EquipmentStructureApi.structure(equipment).ifPresent(structure -> {
            var plan = AppearanceRuntime.resolve(structure, AppearanceGuiAssetRegistry.support());
            AppearanceGuiRenderer.render(graphics, font, plan, anchorX, anchorY);
        });
        graphics.drawString(font, font.plainSubstrByWidth(snapshot.equipmentName().getString(),
                        uiLayout.preview().width() - PANEL_PADDING * 2),
                left + uiLayout.preview().x() + PANEL_PADDING,
                top + uiLayout.preview().y() + PREVIEW_NAME_Y,
                0xFFFFFFFF, false);
    }

    private void renderStats(GuiGraphics graphics, int left, int top,
                             EquipmentAssemblyUiLayout.Panel stats,
                             List<EquipmentAssemblyDisplaySnapshot.EquipmentStatRow> rows) {
        int x = left + stats.x() + PANEL_PADDING;
        int y = top + stats.y();
        int row = y + EquipmentAssemblyInfoLayout.STAT_FIRST_LINE_OFFSET;
        for (EquipmentAssemblyDisplaySnapshot.EquipmentStatRow stat : rows) {
            row = drawStatRow(graphics, stat.label(), stat.value(), x, row, stats, stat.color());
        }
    }

    private int drawStatRow(GuiGraphics graphics, Component label, String value, int x, int y,
                            EquipmentAssemblyUiLayout.Panel stats) {
        return drawStatRow(graphics, label, Component.literal(value), x, y, stats, 0xFFFFFFFF);
    }

    private int drawStatRow(GuiGraphics graphics, Component label, Component value, int x, int y,
                            EquipmentAssemblyUiLayout.Panel stats, int valueColor) {
        // x already includes the left panel padding; subtract both paddings
        // here so the value column ends inside the authored panel border.
        int valueRight = x + stats.width() - PANEL_PADDING * 2;
        int valueLeft = x + STAT_VALUE_OFFSET;
        int labelWidth = valueLeft - x - STAT_COLUMN_GAP;
        int valueWidth = valueRight - valueLeft;
        float valueScale = EquipmentAssemblyInfoLayout.statValueScale(
                value.getString(), font.width(value), valueWidth);
        List<FormattedCharSequence> labelLines = font.split(label, labelWidth);
        List<FormattedCharSequence> valueLines = font.split(value, (int) Math.floor(valueWidth / valueScale));
        int lines = Math.max(1, Math.max(labelLines.size(), valueLines.size()));
        for (int index = 0; index < lines; index++) {
            if (index < labelLines.size()) {
                graphics.drawString(font, labelLines.get(index), x,
                        y + index * EquipmentAssemblyInfoLayout.STAT_LINE_HEIGHT, 0xFF999999, false);
            }
            if (index < valueLines.size()) {
                graphics.pose().pushPose();
                graphics.pose().translate(valueLeft, y + index * EquipmentAssemblyInfoLayout.STAT_LINE_HEIGHT, 0);
                graphics.pose().scale(valueScale, valueScale, 1.0F);
                graphics.drawString(font, valueLines.get(index), 0, 0, valueColor, false);
                graphics.pose().popPose();
            }
        }
        return y + lines * EquipmentAssemblyInfoLayout.STAT_LINE_HEIGHT;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Labels are placed relative to their panels so resource packs can
        // replace the layout without moving the vanilla inventory slots.
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (detailView.active() || slot.index == EquipmentAssemblyMenu.EQUIPMENT_SLOT
                || slot.index == EquipmentAssemblyMenu.PART_SLOT_START) return;
        super.renderSlot(graphics, slot);
    }

    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        if (detailView.active() || gridView.busy()) return false;
        if (x == EquipmentAssemblyLayout.COMPONENT_SLOT_X && y == EquipmentAssemblyLayout.COMPONENT_SLOT_Y) return false;
        if (x == EquipmentAssemblyLayout.equipmentSlotX() && y == EquipmentAssemblyLayout.equipmentSlotY())
            return revealProgress(0) >= .98F && super.isHovering(x, y, width, height, mouseX, mouseY);
        return super.isHovering(x, y, width, height, mouseX, mouseY);
    }

    @Override
    protected void renderSlotHighlight(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        if (slot.index == EquipmentAssemblyMenu.PART_SLOT_START) return;
        super.renderSlotHighlight(graphics, slot, mouseX, mouseY, partialTick);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        if (detailView.active() || gridView.busy() || slotId == EquipmentAssemblyMenu.PART_SLOT_START
                || slot != null && slot.index == EquipmentAssemblyMenu.PART_SLOT_START) return;
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (EquipmentAssemblyKeyMappings.OPEN_ASSEMBLY.isActiveAndMatches(
                com.mojang.blaze3d.platform.InputConstants.getKey(keyCode, scanCode))) {
            onClose();
            return true;
        }
        if (detailView.active()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || minecraft.options.keyInventory.isActiveAndMatches(
                    com.mojang.blaze3d.platform.InputConstants.getKey(keyCode, scanCode))) {
                onClose();
                return true;
            }
            return detailView.keyPressed(keyCode);
        }
        // Vanilla retains Escape/inventory-key handling and normal container cleanup.
        {
            refreshGridInput();
            if (gridView.key(keyCode)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        gridView.closeSpacePanel();
        gridView.cancelGesture();
        EquipmentAssemblyKeyEvents.clearQueuedClicks();
        super.onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (EquipmentAssemblyKeyMappings.OPEN_ASSEMBLY.matchesMouse(button)
                && EquipmentAssemblyKeyMappings.OPEN_ASSEMBLY.isConflictContextAndModifierActive()) {
            onClose();
            return true;
        }
        if (detailView.active()) {
            consumeNodeRelease = true;
            return detailView.mouseClicked(mouseX - leftPos, mouseY - topPos, button);
        }
        {
            refreshGridInput();
            if (gridView.click(mouseX - leftPos, mouseY - topPos, button, hasShiftDown())) {
                selectedInterface = gridView.selected();
                isQuickCrafting = false;
                quickCraftSlots.clear();
                hoveredSlot = null;
                consumeNodeRelease = true;
                return true;
            }
        }
        var entry = informationEntryAt(mouseX, mouseY);
        if (entry.isPresent()) {
            if (button == 0) {
                isQuickCrafting = false;
                quickCraftSlots.clear();
                hoveredSlot = null;
                detailView.open(entry.get().page(), selectedInterface);
            }
            consumeNodeRelease = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (detailView.active()) return true;
        {
            refreshGridInput();
            if (gridView.drag(mouseX - leftPos, mouseY - topPos, button, dragX, dragY)) return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        {
            refreshGridInput();
            if (gridView.release(mouseX - leftPos, mouseY - topPos, button)) {
                consumeNodeRelease = false;
                return true;
            }
        }
        if (consumeNodeRelease) {
            consumeNodeRelease = false;
            return true;
        }
        if (detailView.active()) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (detailView.active()) return detailView.mouseScrolled(mouseX - leftPos, mouseY - topPos, scrollY);
        refreshGridInput();
        return gridView.scroll(mouseX - leftPos, mouseY - topPos, scrollY)
                || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private Optional<InformationEntry> informationEntryAt(double mouseX, double mouseY) {
        var snapshot = currentSnapshot();
        if (detailView.active() || revealProgress(0) < .98F || snapshot.equipment().isEmpty()) return Optional.empty();
        double x = mouseX - leftPos, y = mouseY - topPos;
        var stats = dynamicStatsPanel(snapshot.equipmentStats());
        if (contains(stats, x, y)) return Optional.of(new InformationEntry(EquipmentDetailsView.Page.ATTRIBUTES, stats));
        return menu.getCarried().isEmpty() && selectedInterface != null
                && EquipmentStructureApi.component(menu.equipmentStack(), selectedInterface).isPresent()
                && contains(gridView.details(), x, y)
                ? Optional.of(new InformationEntry(EquipmentDetailsView.Page.COMPONENTS, gridView.details()))
                : Optional.empty();
    }

    public Optional<Component> informationHintAt(double mouseX, double mouseY) {
        return informationEntryAt(mouseX, mouseY).map(InformationEntry::hint);
    }

    private boolean renderInformationEntryHover(GuiGraphics graphics, int mouseX, int mouseY) {
        var entry = informationEntryAt(mouseX, mouseY).orElse(null);
        if (entry == null) return false;
        EquipmentAssemblyUiLayout.Panel panel = entry.panel();
        graphics.renderOutline(leftPos + panel.x() + 3, topPos + panel.y() + 3,
                panel.width() - 6, panel.height() - 6, currentSnapshot().uiDefinition().accentColor());
        graphics.renderComponentTooltip(font, List.of(entry.hint()), mouseX, mouseY);
        return true;
    }

    private record InformationEntry(EquipmentDetailsView.Page page, EquipmentAssemblyUiLayout.Panel panel) {
        Component hint() {
            return EquipmentDetailsView.text(page == EquipmentDetailsView.Page.ATTRIBUTES ? "open_attributes" : "open_components");
        }
    }

    private static boolean contains(EquipmentAssemblyUiLayout.Panel panel, double x, double y) {
        return x >= panel.x() && x < panel.right() && y >= panel.y() && y < panel.bottom();
    }

    /** Read-only state for accessibility and development acceptance, never a second menu. */
    public String detailPage() { return detailView.page().name(); }

    private EquipmentAssemblyDisplaySnapshot currentSnapshot() {
        if (displaySnapshot == null) {
            displaySnapshot = EquipmentAssemblyDisplaySnapshot.capture(menu, selectedInterface);
        }
        return displaySnapshot;
    }

    private void clearSelection() {
        selectedInterface = null;
        gridView.select(null);
        // Visual dismissal and slot activation share the same selected ID.
        // This also runs when an equipment/schema change closes the details.
        if (menu.selectedInterface().isPresent()) {
            menu.selectInterface((ResourceLocation) null);
            PacketDistributor.sendToServer(new ClearInterfacePayload(menu.containerId));
        }
    }

    private float revealProgress(float partialTick) {
        float elapsed = (Util.getMillis() - openTime + partialTick * 16.0F) / (float) REVEAL_MILLIS;
        return Mth.clamp(elapsed, 0.0F, 1.0F);
    }

    private void refreshGridInput() {
        // Geometry and cursor state are read from the live menu by refresh. Input does not need
        // a second full attribute/item display read between rendered frames.
        gridView.refresh(currentSnapshot());
        selectedInterface = gridView.selected();
    }

    private static void blitRegion(GuiGraphics graphics, Region region, int x, int y) {
        graphics.blit(GUI_TEXTURE, x, y, region.u(), region.v(), region.width(), region.height(),
                TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private record Region(int u, int v, int width, int height) {
    }
}
