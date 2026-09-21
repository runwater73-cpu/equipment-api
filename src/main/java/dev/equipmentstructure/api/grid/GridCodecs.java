package dev.equipmentstructure.api.grid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Bounded, checked codecs shared by persisted layouts and server definition snapshots. */
public final class GridCodecs {
    private GridCodecs() {}

    public static final Codec<GridCell> CELL = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, 63).fieldOf("x").forGetter(GridCell::x),
            Codec.intRange(0, 63).fieldOf("y").forGetter(GridCell::y)
    ).apply(i, GridCell::new));
    public static final Codec<GridShape> SHAPE = CELL.listOf(1, GridShape.MAX_CELLS)
            .comapFlatMap(cells -> checked(() -> new GridShape(cells)), GridShape::cells);
    public static final Codec<GridRotation> ROTATION = Codec.intRange(0, 3)
            .xmap(value -> GridRotation.values()[value], GridRotation::ordinal);
    public static final Codec<GridPlacement> PLACEMENT = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, 63).fieldOf("x").forGetter(GridPlacement::x),
            Codec.intRange(0, 63).fieldOf("y").forGetter(GridPlacement::y),
            ROTATION.optionalFieldOf("rotation", GridRotation.NONE).forGetter(GridPlacement::rotation)
    ).apply(i, GridPlacement::new));
    private record Footprint(GridShape shape, List<GridRotation> rotations) {}
    public static final Codec<GridFootprint> FOOTPRINT = RecordCodecBuilder.<Footprint>create(i -> i.group(
            SHAPE.fieldOf("shape").forGetter(Footprint::shape),
            ROTATION.listOf(1, 4).fieldOf("rotations").forGetter(Footprint::rotations)
    ).apply(i, Footprint::new)).comapFlatMap(raw -> checked(() -> {
        if (Set.copyOf(raw.rotations()).size() != raw.rotations().size())
            throw new IllegalArgumentException("Duplicate grid rotation");
        return new GridFootprint(raw.shape(), Set.copyOf(raw.rotations()));
    }), value -> new Footprint(value.shape(), value.rotations().stream().sorted().toList()));
    private record Board(GridShape area, GridShape body, GridPlacement placement, boolean occupies) {}
    public static final Codec<GridBoard> BOARD = RecordCodecBuilder.<Board>create(i -> i.group(
            SHAPE.fieldOf("area").forGetter(Board::area),
            SHAPE.fieldOf("body").forGetter(Board::body),
            PLACEMENT.fieldOf("body_placement").forGetter(Board::placement),
            Codec.BOOL.optionalFieldOf("body_occupies_cells", true).forGetter(Board::occupies)
    ).apply(i, Board::new)).comapFlatMap(raw -> checked(() ->
            new GridBoard(raw.area(), raw.body(), raw.placement(), raw.occupies())),
            value -> new Board(value.area(), value.body(), value.bodyPlacement(), value.bodyOccupiesCells()));

    public static <T> Codec<Map<ResourceLocation, T>> boundedMap(Codec<T> value, int limit) {
        return Codec.unboundedMap(ResourceLocation.CODEC, value).validate(map -> map.size() <= limit
                ? DataResult.success(map) : DataResult.error(() -> "Too many grid entries: " + map.size()));
    }

    public static <T> DataResult<T> checked(Supplier<T> constructor) {
        try { return DataResult.success(constructor.get()); }
        catch (IllegalArgumentException | NullPointerException e) {
            return DataResult.error(() -> "Invalid grid data: " + e.getMessage());
        }
    }
}
