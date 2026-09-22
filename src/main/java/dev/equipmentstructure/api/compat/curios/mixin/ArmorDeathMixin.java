package dev.equipmentstructure.api.compat.curios.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.equipmentstructure.api.compat.curios.CuriosArmorLifecycle;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
abstract class ArmorDeathMixin {
    @WrapMethod(method = "dropAllDeathLoot")
    private void equipment$freezeOwnership(net.minecraft.server.level.ServerLevel level, DamageSource source, Operation<Void> original) {
        var entity = (LivingEntity) (Object) this;
        CuriosArmorLifecycle.freeze(entity);
        try { original.call(level, source); } finally { CuriosArmorLifecycle.thaw(entity); }
    }
}
