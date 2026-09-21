package dev.equipmentstructure.api.grid.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.equipmentstructure.api.grid.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Serializable server policy. Items in these spaces are storage, never automatically installed components. */
public record ComponentSpaceDefinition(ResourceLocation id, Mode mode, GridShape shape, GridCell offset,
        boolean followsRotation, Set<ResourceLocation> items, Set<ResourceLocation> tags,
        Optional<ResourceLocation> predicate, Map<ResourceLocation, GridFootprint> itemShapes,
        int stackLimit, Removal removal, Optional<Integer> color) {
    public enum Mode { ATTACHED_REGION, CHILD_PANEL }
    public enum Removal { REQUIRE_EMPTY, KEEP_WITH_COMPONENT }
    private static final Codec<GridCell> OFFSET = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(-64, 64).fieldOf("x").forGetter(GridCell::x),
            Codec.intRange(-64, 64).fieldOf("y").forGetter(GridCell::y)).apply(i, GridCell::new));
    public static final Codec<ComponentSpaceDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(ComponentSpaceDefinition::id),
            Codec.STRING.xmap(Mode::valueOf, Mode::name).fieldOf("mode").forGetter(ComponentSpaceDefinition::mode),
            GridCodecs.SHAPE.fieldOf("shape").forGetter(ComponentSpaceDefinition::shape),
            OFFSET.optionalFieldOf("offset", new GridCell(0, 0)).forGetter(ComponentSpaceDefinition::offset),
            Codec.BOOL.optionalFieldOf("follows_rotation", true).forGetter(ComponentSpaceDefinition::followsRotation),
            ResourceLocation.CODEC.listOf(0, 256).xmap(Set::copyOf, v -> v.stream().sorted().toList()).optionalFieldOf("items", Set.of()).forGetter(ComponentSpaceDefinition::items),
            ResourceLocation.CODEC.listOf(0, 256).xmap(Set::copyOf, v -> v.stream().sorted().toList()).optionalFieldOf("tags", Set.of()).forGetter(ComponentSpaceDefinition::tags),
            ResourceLocation.CODEC.optionalFieldOf("predicate").forGetter(ComponentSpaceDefinition::predicate),
            GridCodecs.boundedMap(GridCodecs.FOOTPRINT, 256).optionalFieldOf("item_shapes", Map.of()).forGetter(ComponentSpaceDefinition::itemShapes),
            Codec.intRange(1, 99).optionalFieldOf("stack_limit", 64).forGetter(ComponentSpaceDefinition::stackLimit),
            Codec.STRING.xmap(Removal::valueOf, Removal::name).optionalFieldOf("removal", Removal.REQUIRE_EMPTY).forGetter(ComponentSpaceDefinition::removal),
            Codec.intRange(0, 0xFFFFFF).optionalFieldOf("color").forGetter(ComponentSpaceDefinition::color)
    ).apply(i, ComponentSpaceDefinition::new));
    public ComponentSpaceDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(mode); Objects.requireNonNull(shape); Objects.requireNonNull(offset);
        items = Set.copyOf(items); tags = Set.copyOf(tags); itemShapes = Collections.unmodifiableMap(new TreeMap<>(itemShapes));
        Objects.requireNonNull(predicate); Objects.requireNonNull(removal); Objects.requireNonNull(color);
        if (Math.abs((long) offset.x()) > 64 || Math.abs((long) offset.y()) > 64 || stackLimit < 1 || stackLimit > 99
                || items.size() > 256 || tags.size() > 256 || itemShapes.size() > 256 || shape.area() > 256
                || color.filter(v -> v < 0 || v > 0xFFFFFF).isPresent()) throw new IllegalArgumentException("Invalid component space");
    }
    public static ComponentSpaceDefinition attached(ResourceLocation id, GridShape shape, GridCell offset) {
        return new ComponentSpaceDefinition(id, Mode.ATTACHED_REGION, shape, offset, true, Set.of(), Set.of(), Optional.empty(), Map.of(), 64, Removal.REQUIRE_EMPTY, Optional.empty());
    }
    public static ComponentSpaceDefinition panel(ResourceLocation id, GridShape shape) {
        return new ComponentSpaceDefinition(id, Mode.CHILD_PANEL, shape, new GridCell(0, 0), false, Set.of(), Set.of(), Optional.empty(), Map.of(), 64, Removal.REQUIRE_EMPTY, Optional.empty());
    }
    public ComponentSpaceDefinition acceptsItems(ResourceLocation... ids) { return new ComponentSpaceDefinition(id, mode, shape, offset, followsRotation, Set.of(ids), tags, predicate, itemShapes, stackLimit, removal, color); }
    public ComponentSpaceDefinition acceptsTags(ResourceLocation... ids) { return new ComponentSpaceDefinition(id, mode, shape, offset, followsRotation, items, Set.of(ids), predicate, itemShapes, stackLimit, removal, color); }
    public ComponentSpaceDefinition filteredBy(ResourceLocation key) { return new ComponentSpaceDefinition(id, mode, shape, offset, followsRotation, items, tags, Optional.of(key), itemShapes, stackLimit, removal, color); }
    public ComponentSpaceDefinition withItemShapes(Map<ResourceLocation, GridFootprint> value) { return new ComponentSpaceDefinition(id, mode, shape, offset, followsRotation, items, tags, predicate, value, stackLimit, removal, color); }
    public ComponentSpaceDefinition withStackLimit(int value) { return new ComponentSpaceDefinition(id, mode, shape, offset, followsRotation, items, tags, predicate, itemShapes, value, removal, color); }
    public ComponentSpaceDefinition withRemoval(Removal value) { return new ComponentSpaceDefinition(id, mode, shape, offset, followsRotation, items, tags, predicate, itemShapes, stackLimit, value, color); }
    public ComponentSpaceDefinition withColor(int value) { return new ComponentSpaceDefinition(id, mode, shape, offset, followsRotation, items, tags, predicate, itemShapes, stackLimit, removal, Optional.of(value)); }
    public ComponentSpaceDefinition withRotation(boolean value) { return new ComponentSpaceDefinition(id, mode, shape, offset, value, items, tags, predicate, itemShapes, stackLimit, removal, color); }

    /** Transforms signed offsets without normalizing away their relation to the source. */
    public GridCell toBoard(GridCell local, GridShape sourceShape, GridPlacement source) {
        int x = local.x() + offset.x(), y = local.y() + offset.y();
        int width = sourceShape.width(), height = sourceShape.height();
        if (followsRotation) for (int n = 0; n < source.rotation().ordinal(); n++) {
            int nextX = height - 1 - y; y = x; x = nextX;
            int nextWidth = height; height = width; width = nextWidth;
        }
        return new GridCell(Math.addExact(source.x(), x), Math.addExact(source.y(), y));
    }
    public Set<GridCell> boardCells(GridShape sourceShape, GridPlacement source) {
        var result = new HashSet<GridCell>();
        for (GridCell cell : shape.cells()) {
            result.add(toBoard(cell, sourceShape, source));
        }
        return Set.copyOf(result);
    }
}
