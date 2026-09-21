package dev.equipmentstructure.api.mixin.client;

import net.minecraft.client.model.AgeableListModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AgeableListModel.class)
public interface AgeableListModelAccessor {
    @Accessor("scaleHead") boolean equipmentStructureApi$scaleHead();
    @Accessor("babyYHeadOffset") float equipmentStructureApi$babyYHeadOffset();
    @Accessor("babyZHeadOffset") float equipmentStructureApi$babyZHeadOffset();
    @Accessor("babyHeadScale") float equipmentStructureApi$babyHeadScale();
    @Accessor("babyBodyScale") float equipmentStructureApi$babyBodyScale();
    @Accessor("bodyYOffset") float equipmentStructureApi$bodyYOffset();
}
