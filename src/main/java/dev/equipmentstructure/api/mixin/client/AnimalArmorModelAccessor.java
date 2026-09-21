package dev.equipmentstructure.api.mixin.client;

import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({HorseModel.class, WolfModel.class})
public interface AnimalArmorModelAccessor {
    @Accessor("body") ModelPart equipmentStructureApi$body();
}
