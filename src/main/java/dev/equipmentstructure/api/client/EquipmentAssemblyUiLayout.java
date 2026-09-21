package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.menu.EquipmentAssemblyLayout;

/** Single source of truth for the visual panel geometry. */
public record EquipmentAssemblyUiLayout(Panel preview, Panel stats, Panel workspace,
                                        Panel details, Panel inventory) {
    public static EquipmentAssemblyUiLayout standard() {
        EquipmentAssemblyUiLayout layout = new EquipmentAssemblyUiLayout(
                new Panel(EquipmentAssemblyLayout.PREVIEW_X, EquipmentAssemblyLayout.PREVIEW_Y, 96, 96),
                new Panel(EquipmentAssemblyLayout.STATS_X, EquipmentAssemblyLayout.STATS_Y, 96, 64),
                new Panel(EquipmentAssemblyLayout.WORKSPACE_X, EquipmentAssemblyLayout.WORKSPACE_Y,
                        EquipmentAssemblyLayout.WORKSPACE_WIDTH, EquipmentAssemblyLayout.WORKSPACE_HEIGHT),
                new Panel(EquipmentAssemblyLayout.DETAILS_X, EquipmentAssemblyLayout.DETAILS_Y,
                        EquipmentAssemblyLayout.COMPONENT_INFO_WIDTH,
                        EquipmentAssemblyLayout.COMPONENT_INFO_HEIGHT),
                new Panel(EquipmentAssemblyLayout.INVENTORY_X,
                        EquipmentAssemblyLayout.INVENTORY_Y, 176, 90));
        layout.validate();
        return layout;
    }

    public int equipmentSlotX() {
        return EquipmentAssemblyLayout.equipmentSlotX();
    }

    public int equipmentSlotY() {
        return EquipmentAssemblyLayout.equipmentSlotY();
    }

    public Panel componentPreview() {
        return new Panel(EquipmentAssemblyLayout.COMPONENT_PREVIEW_X,
                EquipmentAssemblyLayout.COMPONENT_PREVIEW_Y,
                EquipmentAssemblyLayout.COMPONENT_PREVIEW_WIDTH,
                EquipmentAssemblyLayout.COMPONENT_PREVIEW_HEIGHT);
    }

    /** Independent information panel for the selected installed component. */
    public Panel componentInfo() {
        return componentInfo(EquipmentAssemblyLayout.COMPONENT_INFO_HEIGHT);
    }

    /** Returns a component panel with an author/content-driven height. */
    public Panel componentInfo(int height) {
        return new Panel(EquipmentAssemblyLayout.COMPONENT_INFO_X,
                EquipmentAssemblyLayout.COMPONENT_INFO_Y,
                EquipmentAssemblyLayout.COMPONENT_INFO_WIDTH,
                height);
    }

    /** Returns the left equipment information panel with a content-driven height. */
    public Panel stats(int height) {
        return new Panel(EquipmentAssemblyLayout.STATS_X, EquipmentAssemblyLayout.STATS_Y,
                96, height);
    }

    private void validate() {
        if (workspace.right() > details.x()) {
            throw new IllegalStateException("Workspace overlaps interface details panel");
        }
        if (EquipmentAssemblyLayout.equipmentFrameX() < preview.x()
                || EquipmentAssemblyLayout.equipmentFrameX() + EquipmentAssemblyLayout.EQUIPMENT_FRAME_WIDTH > preview.right()
                || EquipmentAssemblyLayout.equipmentFrameY() < preview.y()
                || EquipmentAssemblyLayout.equipmentFrameY() + EquipmentAssemblyLayout.EQUIPMENT_FRAME_HEIGHT > preview.bottom()) {
            throw new IllegalStateException("Equipment input frame is outside preview panel");
        }
        Panel componentPreview = componentPreview();
        // Component input uses the sidebar; the component preview is display-only.
        if (componentPreview.bottom() > componentInfo().y()
                || details.x() != componentInfo().x()
                || details.y() != componentInfo().y()
                || details.width() != componentInfo().width()) {
            throw new IllegalStateException("Right-side component panel geometry is inconsistent");
        }
    }

    public record Panel(int x, int y, int width, int height) {
        public Panel {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid panel size");
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }
    }
}
