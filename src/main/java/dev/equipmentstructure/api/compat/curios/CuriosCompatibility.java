package dev.equipmentstructure.api.compat.curios;

import dev.equipmentstructure.api.grid.GridFootprint;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Optional integration registration API. Safe to call without Curios installed; register during common setup. */
public final class CuriosCompatibility {
    private static final Map<String, CuriosSlotProfile> SLOTS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, GridFootprint> ITEMS = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> EXCLUDED = ConcurrentHashMap.newKeySet();
    private static final Map<ResourceLocation, Boolean> PLAYER_BOUND = new ConcurrentHashMap<>(Map.of(
            ResourceLocation.parse("enigmaticlegacyplus:cursed_ring"), true,
            ResourceLocation.parse("enigmaticlegacyplus:redemption_ring"), true));
    private CuriosCompatibility() {}
    public static void registerSlot(String nativeSlot, CuriosSlotProfile profile) {
        if (nativeSlot == null || nativeSlot.isBlank() || nativeSlot.length() > 128)
            throw new IllegalArgumentException("Invalid Curios slot identifier");
        SLOTS.put(nativeSlot, java.util.Objects.requireNonNull(profile));
    }
    public static void registerItemFootprint(ResourceLocation item, GridFootprint footprint) {
        ITEMS.put(java.util.Objects.requireNonNull(item), java.util.Objects.requireNonNull(footprint));
    }
    public static void excludeEquipment(ResourceLocation item) { EXCLUDED.add(java.util.Objects.requireNonNull(item)); }
    /** Explicit permanent binding policy; temporary canUnequip restrictions must not opt in. */
    public static void registerPlayerBoundItem(ResourceLocation item, boolean bound) {
        PLAYER_BOUND.put(java.util.Objects.requireNonNull(item), bound);
    }
    static Map<ResourceLocation, Boolean> playerBoundItems() { return Map.copyOf(PLAYER_BOUND); }
    static Map<String, CuriosSlotProfile> slots() { return Map.copyOf(SLOTS); }
    static Map<ResourceLocation, GridFootprint> items() { return Map.copyOf(ITEMS); }
    static Set<ResourceLocation> excluded() { return Set.copyOf(EXCLUDED); }
    static CuriosSlotProfile fallback(String type) {
        EquipmentSlot slot = switch (type) {
            case "head", "face", "eyes", "ear", "ears" -> EquipmentSlot.HEAD;
            case "belt", "legs" -> EquipmentSlot.LEGS;
            case "feet", "shoes", "anklet" -> EquipmentSlot.FEET;
            default -> EquipmentSlot.CHEST;
        };
        // Conservative fallback: opt-in item/slot definitions supply larger and irregular shapes.
        return new CuriosSlotProfile(slot, GridFootprint.SINGLE_CELL);
    }
}
