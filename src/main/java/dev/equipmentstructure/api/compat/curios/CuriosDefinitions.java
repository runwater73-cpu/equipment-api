package dev.equipmentstructure.api.compat.curios;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.equipmentstructure.api.grid.GridCodecs;
import dev.equipmentstructure.api.grid.GridFootprint;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.List;
import java.util.Set;

public record CuriosDefinitions(boolean enabled, Map<String, CuriosSlotProfile> slots,
        Map<ResourceLocation, GridFootprint> items, Set<ResourceLocation> excluded,
        Map<ResourceLocation, Boolean> playerBoundItems) {
    public static final Codec<CuriosDefinitions> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(CuriosDefinitions::enabled),
            Codec.unboundedMap(Codec.STRING, CuriosSlotProfile.CODEC).optionalFieldOf("slots", Map.of()).forGetter(CuriosDefinitions::slots),
            GridCodecs.boundedMap(GridCodecs.FOOTPRINT, 1024).optionalFieldOf("items", Map.of()).forGetter(CuriosDefinitions::items),
            ResourceLocation.CODEC.listOf(0, 1024).xmap(Set::copyOf, List::copyOf)
                    .optionalFieldOf("excluded_equipment", Set.of()).forGetter(CuriosDefinitions::excluded),
            GridCodecs.boundedMap(Codec.BOOL, 1024).optionalFieldOf("player_bound_items", Map.of()).forGetter(CuriosDefinitions::playerBoundItems)
    ).apply(i, CuriosDefinitions::new));
    public CuriosDefinitions {
        slots = Map.copyOf(slots); items = Map.copyOf(items); excluded = Set.copyOf(excluded);
        playerBoundItems = Map.copyOf(playerBoundItems);
        if (slots.size() > 256 || slots.keySet().stream().anyMatch(s -> s.isBlank() || s.length() > 128))
            throw new IllegalArgumentException("Too many or invalid Curios slot profiles");
    }
    public CuriosSlotProfile profile(String nativeType) {
        return slots.getOrDefault(nativeType, slots.getOrDefault("*", CuriosCompatibility.fallback(nativeType)));
    }
    public static CuriosDefinitions javaDefaults() {
        return new CuriosDefinitions(true, CuriosCompatibility.slots(), CuriosCompatibility.items(), CuriosCompatibility.excluded(), CuriosCompatibility.playerBoundItems());
    }
    public CuriosDefinitions overlay(CuriosDefinitions next) {
        var s = new java.util.HashMap<>(slots); s.putAll(next.slots);
        var i = new java.util.HashMap<>(items); i.putAll(next.items);
        var e = new java.util.HashSet<>(excluded); e.addAll(next.excluded);
        var b = new java.util.HashMap<>(playerBoundItems); b.putAll(next.playerBoundItems);
        return new CuriosDefinitions(next.enabled, s, i, e, b);
    }
}
