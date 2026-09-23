package dev.equipmentstructure.api.compat.curios;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.equipmentstructure.api.grid.GridCodecs;
import dev.equipmentstructure.api.grid.GridFootprint;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.Optional;

/** Equipment routing and default geometry; Curios remains authoritative for slot validators and capacity. */
public record CuriosSlotProfile(EquipmentSlot armorSlot, GridFootprint footprint,
        boolean enabled, boolean cosmetics, Optional<ResourceLocation> template) {
    public static final Codec<EquipmentSlot> ARMOR_SLOT = Codec.STRING.xmap(name -> {
        var slot = EquipmentSlot.byName(name);
        if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST
                && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET)
            throw new IllegalArgumentException("Expected head, chest, legs or feet");
        return slot;
    }, EquipmentSlot::getName);
    public static final Codec<CuriosSlotProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ARMOR_SLOT.fieldOf("armor_slot").forGetter(CuriosSlotProfile::armorSlot),
            GridCodecs.FOOTPRINT.fieldOf("footprint").forGetter(CuriosSlotProfile::footprint),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(CuriosSlotProfile::enabled),
            Codec.BOOL.optionalFieldOf("cosmetics", true).forGetter(CuriosSlotProfile::cosmetics),
            ResourceLocation.CODEC.optionalFieldOf("template").forGetter(CuriosSlotProfile::template)
    ).apply(i, CuriosSlotProfile::new));
    public CuriosSlotProfile {
        java.util.Objects.requireNonNull(armorSlot);
        java.util.Objects.requireNonNull(footprint);
        java.util.Objects.requireNonNull(template);
        if (armorSlot != EquipmentSlot.HEAD && armorSlot != EquipmentSlot.CHEST
                && armorSlot != EquipmentSlot.LEGS && armorSlot != EquipmentSlot.FEET)
            throw new IllegalArgumentException("A Curios armor profile requires a humanoid armor position");
    }
    public CuriosSlotProfile(EquipmentSlot slot, GridFootprint footprint) {
        this(slot, footprint, true, true, Optional.empty());
    }
}
