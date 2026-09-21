package dev.equipmentstructure.api.grid.synergy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Exact identity matching; ANY_COMPONENT excludes the body. */
public record GridSelector(Kind kind, Set<ResourceLocation> ids) {
    public enum Kind { ANY_COMPONENT, COMPONENT, COMPONENT_TYPE, INTERFACE, SLOT, BODY }
    public static final Codec<GridSelector> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(GridSelector::kind),
            ResourceLocation.CODEC.listOf(0, 256).xmap(Set::copyOf, v -> v.stream().sorted().toList())
                    .optionalFieldOf("ids", Set.of()).forGetter(GridSelector::ids)).apply(i, GridSelector::new));
    public GridSelector { Objects.requireNonNull(kind); ids = Set.copyOf(ids); if (ids.size() > 256) throw new IllegalArgumentException("Too many selector IDs"); }
    public static GridSelector any() { return new GridSelector(Kind.ANY_COMPONENT, Set.of()); }
    public static GridSelector components(ResourceLocation... ids) { return new GridSelector(Kind.COMPONENT, Set.of(ids)); }
    public static GridSelector slots(ResourceLocation... ids) { return new GridSelector(Kind.SLOT, Set.of(ids)); }
    public static GridSelector body() { return new GridSelector(Kind.BODY, Set.of()); }
    public boolean matches(GridSpatialContext.Node node) {
        if (kind == Kind.BODY) return node.body();
        if (node.body()) return false;
        return switch (kind) {
            case ANY_COMPONENT -> true;
            case COMPONENT -> ids.contains(node.component().orElseThrow().id());
            case COMPONENT_TYPE -> ids.contains(node.component().orElseThrow().componentType());
            case INTERFACE -> ids.contains(node.slot().orElseThrow().interfaceType());
            case SLOT -> ids.contains(node.id());
            default -> false;
        };
    }
}
