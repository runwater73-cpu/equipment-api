package dev.equipmentstructure.api.grid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Item-owned positions only. No item copies or appearance poses; runtime policy is checked on use. */
public record GridState(int format, long revision, String definitions, Map<ResourceLocation, GridPlacement> placements) {
    public static final int FORMAT = 1;
    public static final int MAX_PARTS = GridShape.MAX_CELLS;
    public static final Codec<String> HASH = Codec.STRING.validate(value -> value.matches("[0-9a-f]{64}")
            ? com.mojang.serialization.DataResult.success(value)
            : com.mojang.serialization.DataResult.error(() -> "Invalid grid definition fingerprint"));
    public static final Codec<GridState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(FORMAT, FORMAT).fieldOf("format").forGetter(GridState::format),
            Codec.LONG.validate(value -> value >= 0 ? com.mojang.serialization.DataResult.success(value)
                    : com.mojang.serialization.DataResult.error(() -> "Negative grid revision"))
                    .fieldOf("revision").forGetter(GridState::revision),
            HASH.fieldOf("definitions").forGetter(GridState::definitions),
            GridCodecs.boundedMap(GridCodecs.PLACEMENT, MAX_PARTS).fieldOf("placements").forGetter(GridState::placements)
    ).apply(i, GridState::new));

    public GridState {
        Objects.requireNonNull(definitions, "definitions");
        placements = Map.copyOf(placements);
        if (format != FORMAT || revision < 0 || !definitions.matches("[0-9a-f]{64}") || placements.size() > MAX_PARTS)
            throw new IllegalArgumentException("Invalid grid state");
        for (GridPlacement p : placements.values()) {
            if (p.x() < 0 || p.x() >= GridShape.MAX_SIDE || p.y() < 0 || p.y() >= GridShape.MAX_SIDE)
                throw new IllegalArgumentException("Grid origin outside supported bounds");
        }
    }

    /** Recovery removal retains the old fingerprint until remaining geometry is revalidated. */
    public GridState without(ResourceLocation slot) {
        if (!placements.containsKey(slot)) return this;
        var remaining = new HashMap<>(placements);
        remaining.remove(slot);
        return new GridState(format, nextRevision(), definitions, remaining);
    }

    public long nextRevision() { return revision == Long.MAX_VALUE ? Long.MAX_VALUE : revision + 1; }
}
