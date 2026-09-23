package dev.equipmentstructure.api.compat.curios;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import net.minecraft.resources.ResourceLocation;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

/** Lossless mapping: Curios identifiers are arbitrary strings, not our namespaced interface IDs. */
public record CuriosSlotKey(String type, int index, boolean cosmetic) {
    public CuriosSlotKey {
        if (type == null || type.isBlank() || type.length() > 128 || index < 0 || index >= 256)
            throw new IllegalArgumentException("Invalid armor Curios slot");
    }
    public ResourceLocation interfaceId() { return id("curios/" + encode(type)); }
    public ResourceLocation slotId() { return id("curios/" + encode(type) + "/" + (cosmetic ? "cosmetic/" : "functional/") + index); }
    public ResourceLocation defaultComponent() { return id("curios/" + encode(type) + "/default"); }
    public ResourceLocation itemComponent(ResourceLocation item) {
        return id("curios/" + encode(type) + "/item/" + item.getNamespace() + "/" + item.getPath());
    }
    public static Optional<CuriosSlotKey> parse(ResourceLocation slot) {
        if (!slot.getNamespace().equals(EquipmentStructureApiMod.MOD_ID) || !slot.getPath().startsWith("curios/")) return Optional.empty();
        try {
            var fields = slot.getPath().split("/");
            if (fields.length != 4 || !fields[2].equals("functional") && !fields[2].equals("cosmetic")) return Optional.empty();
            return Optional.of(new CuriosSlotKey(new String(HexFormat.of().parseHex(fields[1]), StandardCharsets.UTF_8),
                    Integer.parseInt(fields[3]), fields[2].equals("cosmetic")));
        } catch (IllegalArgumentException failure) { return Optional.empty(); }
    }
    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, path); }
    private static String encode(String input) { return HexFormat.of().formatHex(input.getBytes(StandardCharsets.UTF_8)); }
}
