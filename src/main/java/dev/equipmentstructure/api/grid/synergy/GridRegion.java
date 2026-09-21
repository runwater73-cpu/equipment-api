package dev.equipmentstructure.api.grid.synergy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.equipmentstructure.api.grid.*;
import java.util.Set;

public record GridRegion(GridShape shape, GridPlacement placement) {
    public static final Codec<GridRegion> CODEC = RecordCodecBuilder.create(i -> i.group(
            GridCodecs.SHAPE.fieldOf("shape").forGetter(GridRegion::shape),
            GridCodecs.PLACEMENT.fieldOf("placement").forGetter(GridRegion::placement)).apply(i, GridRegion::new));
    public Set<GridCell> cells() { return new GridLayout.Part(GridFootprint.freelyRotating(shape), placement).cells(); }
}
