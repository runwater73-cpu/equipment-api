package dev.equipmentstructure.api.menu;

/**
 * Shared pixel coordinates for the assembly menu and its client screen.
 *
 * <p>The menu owns item-slot locations while the screen owns texture drawing.
 * Keeping the coordinates here prevents texture frames and interactive slots
 * from drifting apart as the visual layout evolves.</p>
 */
public final class EquipmentAssemblyLayout {
    public static final int GUI_WIDTH = 400;
    public static final int GUI_HEIGHT = 256;

    public static final int INVENTORY_X = 104;
    public static final int INVENTORY_Y = 166;
    public static final int PREVIEW_X = 0;
    public static final int PREVIEW_Y = 2;
    public static final int STATS_X = 0;
    public static final int STATS_Y = 100;
    public static final int WORKSPACE_X = INVENTORY_X;
    public static final int WORKSPACE_Y = PREVIEW_Y;
    public static final int WORKSPACE_WIDTH = 176;
    public static final int WORKSPACE_HEIGHT = INVENTORY_Y - WORKSPACE_Y - 2;
    public static final int COMPONENT_PREVIEW_X = 288;
    public static final int COMPONENT_PREVIEW_Y = PREVIEW_Y;
    public static final int COMPONENT_PREVIEW_WIDTH = 96;
    public static final int COMPONENT_PREVIEW_HEIGHT = 72;
    public static final int COMPONENT_INFO_X = COMPONENT_PREVIEW_X;
    public static final int COMPONENT_INFO_Y = 76;
    public static final int COMPONENT_INFO_WIDTH = 96;
    public static final int COMPONENT_INFO_HEIGHT = 58;
    public static final int DETAILS_X = COMPONENT_INFO_X;
    public static final int DETAILS_Y = COMPONENT_INFO_Y;
    public static final int PLAYER_INVENTORY_X = 112;
    public static final int PLAYER_INVENTORY_Y = 175;
    public static final int PLAYER_INVENTORY_ROW_HEIGHT = 18;
    public static final int PLAYER_INVENTORY_ROWS = 3;
    public static final int HOTBAR_Y_OFFSET = 58;

    public static final int EQUIPMENT_SLOT_SIZE = 16;
    public static final int EQUIPMENT_FRAME_WIDTH = 28;
    public static final int EQUIPMENT_FRAME_HEIGHT = 28;
    public static final int EQUIPMENT_FRAME_INSET = 6;
    /** The host input and its rendered preview share the same left-panel coordinates. */
    public static final int EQUIPMENT_SLOT_X = PREVIEW_X + 40;
    public static final int EQUIPMENT_SLOT_Y = PREVIEW_Y + 40;

    /** Vanilla item-icon position in the equipment preview panel. */
    public static int equipmentSlotX() {
        return EQUIPMENT_SLOT_X;
    }

    public static int equipmentSlotY() {
        return EQUIPMENT_SLOT_Y;
    }

    /** Top-left of the authored 28 px equipment frame surrounding the item slot. */
    public static int equipmentFrameX() {
        return equipmentSlotX() - EQUIPMENT_FRAME_INSET;
    }

    public static int equipmentFrameY() {
        return equipmentSlotY() - EQUIPMENT_FRAME_INSET;
    }

    /** Center of the equipment preview, in GUI-local pixels. */
    public static int equipmentCoreX() {
        return equipmentSlotX() + EQUIPMENT_SLOT_SIZE / 2;
    }

    public static int equipmentCoreY() {
        return equipmentSlotY() + EQUIPMENT_SLOT_SIZE / 2;
    }

    /** Internal staging coordinates; item transfers use the component sidebar. */
    public static final int COMPONENT_SLOT_X = -10000;
    public static final int COMPONENT_SLOT_Y = -10000;
    public static final int COMPONENT_FRAME_INSET = 1;

    /** The authored texture's inner inventory grid starts 8 px right and 9 px down. */
    public static final int INVENTORY_GRID_INSET_X = 8;
    public static final int INVENTORY_GRID_INSET_Y = 9;

    public static int playerSlotX(int column) {
        if (column < 0 || column >= 9) {
            throw new IllegalArgumentException("Player inventory column must be 0..8");
        }
        return INVENTORY_X + INVENTORY_GRID_INSET_X + column * 18;
    }

    public static int playerSlotY(int row) {
        if (row < 0 || row > 3) {
            throw new IllegalArgumentException("Player inventory row must be 0..3");
        }
        // The atlas separates the hotbar from the three storage rows by 4 px.
        int offset = row == PLAYER_INVENTORY_ROWS ? HOTBAR_Y_OFFSET
                : row * PLAYER_INVENTORY_ROW_HEIGHT;
        return INVENTORY_Y + INVENTORY_GRID_INSET_Y + offset;
    }

    private EquipmentAssemblyLayout() {
    }
}
