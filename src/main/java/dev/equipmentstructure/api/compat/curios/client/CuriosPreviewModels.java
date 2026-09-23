package dev.equipmentstructure.api.compat.curios.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.LivingEntity;

/** Some native renderers look up their wearer model through the dispatcher instead of the layer parent. */
public final class CuriosPreviewModels {
    private record Context(Object renderer, EntityModel<?> model) {}
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private CuriosPreviewModels() {}

    public static EntityModel<?> modelFor(Object renderer) {
        var context = CURRENT.get();
        return context != null && context.renderer == renderer ? context.model : null;
    }

    static void render(LivingEntity wearer, EntityModel<?> model, Runnable draw) {
        var previous = CURRENT.get();
        CURRENT.set(new Context(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(wearer), model));
        try { draw.run(); }
        finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
