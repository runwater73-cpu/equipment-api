package dev.equipmentstructure.api.compat.curios.mixin;

import dev.equipmentstructure.api.compat.curios.client.CuriosAttributeTooltipEvents;
import net.neoforged.neoforge.client.event.AddAttributeTooltipsEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.client.ClientEventHandler;

@Mixin(value = ClientEventHandler.class, remap = false)
abstract class CuriosAttributeTooltipMixin {
    @Inject(method = "onAttributeTooltip", at = @At("HEAD"), cancellable = true)
    private void equipment$installedSlotAlreadyIncluded(AddAttributeTooltipsEvent event, CallbackInfo ci) {
        // Only this specific panel event has already included native Curios attributes.
        // Regular inventory tooltips (including nested events) retain Curios' own listener.
        if (CuriosAttributeTooltipEvents.includesNativeCurios(event)) ci.cancel();
    }
}
