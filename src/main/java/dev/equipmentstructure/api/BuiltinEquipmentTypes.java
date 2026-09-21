package dev.equipmentstructure.api;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Common equipment category IDs for host/component compatibility.
 *
 * <p>These IDs are reusable category vocabulary, not host templates. They do
 * not bind items, create slots, or decide grid layouts. Content authors may
 * use them directly or declare precise categories in their own namespace. A
 * component must list every category it supports explicitly.</p>
 */
public final class BuiltinEquipmentTypes {
    public static final ResourceLocation SWORD = id("sword");
    public static final ResourceLocation AXE = id("axe");
    public static final ResourceLocation PICKAXE = id("pickaxe");
    public static final ResourceLocation SHOVEL = id("shovel");
    public static final ResourceLocation HOE = id("hoe");
    public static final ResourceLocation BOW = id("bow");
    public static final ResourceLocation CROSSBOW = id("crossbow");
    public static final ResourceLocation TRIDENT = id("trident");
    public static final ResourceLocation MACE = id("mace");
    public static final ResourceLocation SHIELD = id("shield");
    public static final ResourceLocation HELMET = id("helmet");
    public static final ResourceLocation CHESTPLATE = id("chestplate");
    public static final ResourceLocation LEGGINGS = id("leggings");
    public static final ResourceLocation BOOTS = id("boots");
    public static final ResourceLocation ELYTRA = id("elytra");
    public static final ResourceLocation HORSE_ARMOR = id("horse_armor");
    public static final ResourceLocation WOLF_ARMOR = id("wolf_armor");
    public static final ResourceLocation FISHING_ROD = id("fishing_rod");
    public static final ResourceLocation SHEARS = id("shears");
    public static final ResourceLocation FLINT_AND_STEEL = id("flint_and_steel");
    public static final ResourceLocation BRUSH = id("brush");

    private static final List<ResourceLocation> ALL = List.of(
            SWORD, AXE, PICKAXE, SHOVEL, HOE, BOW, CROSSBOW, TRIDENT, MACE, SHIELD,
            HELMET, CHESTPLATE, LEGGINGS, BOOTS, ELYTRA, HORSE_ARMOR, WOLF_ARMOR,
            FISHING_ROD, SHEARS, FLINT_AND_STEEL, BRUSH
    );

    private BuiltinEquipmentTypes() {
    }

    public static List<ResourceLocation> all() {
        return ALL;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, path);
    }
}
