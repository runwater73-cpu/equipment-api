package dev.equipmentstructure.api;

import dev.equipmentstructure.api.grid.GridBoard;
import dev.equipmentstructure.api.grid.GridCell;
import dev.equipmentstructure.api.grid.GridShape;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Java view of the API's bundled equipment templates.
 *
 * <p>The same definitions are shipped as data-pack registry entries under
 * {@code data/equipment_structure_api/equipment_structure_api/host_definition}.
 * This class is useful when an integration needs the stable IDs or wants to
 * derive a replacement template. It does not bind any Minecraft item.</p>
 */
public final class BuiltinEquipmentTemplates {
    public static final ResourceLocation SWORD = type(BuiltinEquipmentTypes.SWORD);
    public static final ResourceLocation AXE = type(BuiltinEquipmentTypes.AXE);
    public static final ResourceLocation PICKAXE = type(BuiltinEquipmentTypes.PICKAXE);
    public static final ResourceLocation SHOVEL = type(BuiltinEquipmentTypes.SHOVEL);
    public static final ResourceLocation HOE = type(BuiltinEquipmentTypes.HOE);
    public static final ResourceLocation BOW = type(BuiltinEquipmentTypes.BOW);
    public static final ResourceLocation CROSSBOW = type(BuiltinEquipmentTypes.CROSSBOW);
    public static final ResourceLocation TRIDENT = type(BuiltinEquipmentTypes.TRIDENT);
    public static final ResourceLocation MACE = type(BuiltinEquipmentTypes.MACE);
    public static final ResourceLocation SHIELD = type(BuiltinEquipmentTypes.SHIELD);
    public static final ResourceLocation HELMET = type(BuiltinEquipmentTypes.HELMET);
    public static final ResourceLocation CHESTPLATE = type(BuiltinEquipmentTypes.CHESTPLATE);
    public static final ResourceLocation LEGGINGS = type(BuiltinEquipmentTypes.LEGGINGS);
    public static final ResourceLocation BOOTS = type(BuiltinEquipmentTypes.BOOTS);
    public static final ResourceLocation ELYTRA = type(BuiltinEquipmentTypes.ELYTRA);
    public static final ResourceLocation HORSE_ARMOR = type(BuiltinEquipmentTypes.HORSE_ARMOR);
    public static final ResourceLocation WOLF_ARMOR = type(BuiltinEquipmentTypes.WOLF_ARMOR);
    public static final ResourceLocation FISHING_ROD = type(BuiltinEquipmentTypes.FISHING_ROD);
    public static final ResourceLocation SHEARS = type(BuiltinEquipmentTypes.SHEARS);
    public static final ResourceLocation FLINT_AND_STEEL = type(BuiltinEquipmentTypes.FLINT_AND_STEEL);
    public static final ResourceLocation BRUSH = type(BuiltinEquipmentTypes.BRUSH);

    private static final GridBoard SWORD_GRID = board("..#..", "..#..", "..#..", ".###.", "..#..", "..#..");
    private static final GridBoard AXE_GRID = board("..###.", ".####.", "..##..", "...#..", "...#..");
    private static final GridBoard PICKAXE_GRID = board("#...", "####", "..#.", "..#.", "..#.");
    private static final GridBoard SHOVEL_GRID = board("..#..", ".###.", "..#..", "..#..", "..#..");
    private static final GridBoard HOE_GRID = board("..##.", "...#.", "####.", "..#..", "..#..");
    private static final GridBoard BOW_GRID = board("#...#", "##.##", "#...#", "#...#", "##.##");
    private static final GridBoard CROSSBOW_GRID = board("..#..", "#####", "..#..", "..#..");
    private static final GridBoard TRIDENT_GRID = board("..#..", "#####", "..#..", "..#..", "..#..", "..#..");
    private static final GridBoard MACE_GRID = board("..#..", "..#..", ".###.", "#####", "..#..", "..#..");
    private static final GridBoard SHIELD_GRID = board(".###.", "#####", "#####", ".###.", "..#..");
    private static final GridBoard HELMET_GRID = board(".###.", "#####", "##.##", "..#..");
    private static final GridBoard CHESTPLATE_GRID = board("..#..", ".###.", "#####", ".###.", "..#..");
    private static final GridBoard LEGGINGS_GRID = board("##.##", "##.##", "..#..", "..#..");
    private static final GridBoard BOOTS_GRID = board("##.##", "##.##", "##.##");
    private static final GridBoard ELYTRA_GRID = board("#...#", "##.##", "#####", "##.##", "#...#");
    private static final GridBoard HORSE_ARMOR_GRID = board("..###.", "######", "######", ".####.");
    private static final GridBoard WOLF_ARMOR_GRID = board("...##", "#####", "####.", "..#..");
    private static final GridBoard FISHING_ROD_GRID = board("#..", "##.", "..#", "..#", "..#", "..#");
    private static final GridBoard SHEARS_GRID = board("#..#", ".##.", "..#.", "..#.", "#..#");
    private static final GridBoard FLINT_AND_STEEL_GRID = board("###.", "####", ".##.");
    private static final GridBoard BRUSH_GRID = board("..#..", "..#..", "#####", "..#..", "..#..");
    private static final GridBoard DEFAULT_GRID = board("#");

    private static final List<ResourceLocation> ALL = List.of(
            SWORD, AXE, PICKAXE, SHOVEL, HOE, BOW, CROSSBOW, TRIDENT, MACE, SHIELD,
            HELMET, CHESTPLATE, LEGGINGS, BOOTS, ELYTRA, HORSE_ARMOR, WOLF_ARMOR,
            FISHING_ROD, SHEARS, FLINT_AND_STEEL, BRUSH
    );

    private static final Map<ResourceLocation, GridBoard> GRIDS = Map.ofEntries(
            Map.entry(SWORD, SWORD_GRID), Map.entry(AXE, AXE_GRID),
            Map.entry(PICKAXE, PICKAXE_GRID), Map.entry(SHOVEL, SHOVEL_GRID),
            Map.entry(HOE, HOE_GRID), Map.entry(BOW, BOW_GRID),
            Map.entry(CROSSBOW, CROSSBOW_GRID), Map.entry(TRIDENT, TRIDENT_GRID),
            Map.entry(MACE, MACE_GRID), Map.entry(SHIELD, SHIELD_GRID),
            Map.entry(HELMET, HELMET_GRID), Map.entry(CHESTPLATE, CHESTPLATE_GRID),
            Map.entry(LEGGINGS, LEGGINGS_GRID), Map.entry(BOOTS, BOOTS_GRID),
            Map.entry(ELYTRA, ELYTRA_GRID), Map.entry(HORSE_ARMOR, HORSE_ARMOR_GRID),
            Map.entry(WOLF_ARMOR, WOLF_ARMOR_GRID), Map.entry(FISHING_ROD, FISHING_ROD_GRID),
            Map.entry(SHEARS, SHEARS_GRID), Map.entry(FLINT_AND_STEEL, FLINT_AND_STEEL_GRID),
            Map.entry(BRUSH, BRUSH_GRID)
    );

    private BuiltinEquipmentTemplates() {
    }

    /** Returns the 21 bundled templates in the equipment type order. */
    public static List<ResourceLocation> all() {
        return ALL;
    }

    /** Returns a generic fallback board for callers that have no equipment-specific template. */
    public static GridBoard defaultGrid() {
        return DEFAULT_GRID;
    }

    /** Returns the equipment-specific placeholder body board for one built-in template. */
    public static GridBoard grid(ResourceLocation templateId) {
        return Objects.requireNonNull(GRIDS.get(templateId), "Unknown built-in template: " + templateId);
    }

    /** Builds a replacement definition with the supplied slot sequence. */
    public static EquipmentHostDefinition definition(ResourceLocation templateId,
                                                     ResourceLocation equipmentType,
                                                     List<ResourceLocation> slots) {
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(equipmentType, "equipmentType");
        Objects.requireNonNull(slots, "slots");
        var builder = EquipmentHostDefinition.builder(templateId, equipmentType).grid(grid(templateId));
        slots.forEach(slot -> builder.slot(BuiltinEquipmentSlots.definition(slot)));
        return builder.build();
    }

    private static ResourceLocation type(ResourceLocation equipmentType) {
        return equipmentType;
    }

    private static GridBoard board(String... rows) {
        GridShape body = GridShape.mask(rows);
        return GridBoard.defaultBoard(body);
    }
}
