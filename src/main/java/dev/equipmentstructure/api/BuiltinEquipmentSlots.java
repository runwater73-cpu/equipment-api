package dev.equipmentstructure.api;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Stable public slot vocabulary shipped by the API.
 *
 * <p>These are interface categories, not item bindings. A content mod may use
 * them directly, override the built-in templates, or define another template
 * with its own slot IDs and types.</p>
 */
public final class BuiltinEquipmentSlots {
    public static final ResourceLocation ARMOR_DECORATION = id("armor_decoration");
    public static final ResourceLocation PENDANT = id("pendant");
    public static final ResourceLocation NAMEPLATE = id("nameplate");
    public static final ResourceLocation GRIP_WRAP = id("grip_wrap");
    public static final ResourceLocation GUARD = id("guard");
    public static final ResourceLocation BLADE = id("blade");
    public static final ResourceLocation FITTING = id("fitting");
    public static final ResourceLocation TOOL_MODULE = id("tool_module");
    public static final ResourceLocation SOUL = id("soul");
    public static final ResourceLocation WING = id("wing");
    public static final ResourceLocation HEAD = id("head");
    public static final ResourceLocation CHEST = id("chest");
    public static final ResourceLocation BACK = id("back");
    public static final ResourceLocation LEG = id("leg");
    public static final ResourceLocation BOOT = id("boot");

    private static final List<ResourceLocation> ALL = List.of(
            ARMOR_DECORATION, PENDANT, NAMEPLATE, GRIP_WRAP, GUARD,
            BLADE, FITTING, TOOL_MODULE, SOUL, WING, HEAD, CHEST, BACK, LEG, BOOT
    );

    private BuiltinEquipmentSlots() {
    }

    /** Returns the slots in the published Chinese presentation order. */
    public static List<ResourceLocation> all() {
        return ALL;
    }

    /** Creates a concrete slot using the same ID for interface and component type. */
    public static EquipmentSlotDefinition definition(ResourceLocation slotId) {
        return EquipmentSlotDefinition.of(slotId, slotId);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, path);
    }
}
