package dev.equipmentstructure.api.grid.synergy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public record GridRuleDefinition(ResourceLocation id, Optional<ResourceLocation> host, GridSelector sources, GridCondition condition) {
    public static final Codec<GridRuleDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(GridRuleDefinition::id),
            ResourceLocation.CODEC.optionalFieldOf("host").forGetter(GridRuleDefinition::host),
            GridSelector.CODEC.fieldOf("sources").forGetter(GridRuleDefinition::sources),
            GridCondition.CODEC.fieldOf("condition").forGetter(GridRuleDefinition::condition)).apply(i, GridRuleDefinition::new));
    public GridRuleDefinition { Objects.requireNonNull(id); Objects.requireNonNull(host); Objects.requireNonNull(sources); Objects.requireNonNull(condition); }
    public GridRuleDefinition(ResourceLocation id, GridSelector sources, GridCondition condition) { this(id, Optional.empty(), sources, condition); }
    public GridRuleDefinition inHost(ResourceLocation value) { return new GridRuleDefinition(id, Optional.of(value), sources, condition); }
}
