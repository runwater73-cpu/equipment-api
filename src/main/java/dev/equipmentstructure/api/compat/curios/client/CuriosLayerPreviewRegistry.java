package dev.equipmentstructure.api.compat.curios.client;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.function.BiFunction;

/** Optional previews for addons rendering outside ICurioRenderer. Reuses their original layer/model. */
public final class CuriosLayerPreviewRegistry {
    private static final Map<ResourceLocation, BiFunction<RenderLayerParent, EntityModelSet, RenderLayer>> FACTORIES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> PREVIEW = ThreadLocal.withInitial(() -> false);
    private CuriosLayerPreviewRegistry() {}
    public static void register(ResourceLocation item, BiFunction<RenderLayerParent, EntityModelSet, RenderLayer> factory) {
        FACTORIES.put(item, java.util.Objects.requireNonNull(factory));
    }
    public static boolean previewing() { return PREVIEW.get(); }
    static void preview(Runnable render) {
        var previous = PREVIEW.get(); PREVIEW.set(true);
        try { render.run(); } finally { PREVIEW.set(previous); }
    }
    static RenderLayer create(ItemStack item, RenderLayerParent parent) {
        var factory = FACTORIES.get(BuiltInRegistries.ITEM.getKey(item.getItem()));
        return factory == null ? null : factory.apply(parent, Minecraft.getInstance().getEntityModels());
    }
    public static void initializeEnigmatic() {
        if (!net.neoforged.fml.ModList.get().isLoaded("enigmaticlegacyplus")) return;
        // No runtime dependency or copied model. Resolve the optional layer only on its client.
        try {
            var constructor = Class.forName("auviotre.enigmatic.legacy.client.renderer.layer.EnigmaticElytraLayer")
                    .getConstructor(RenderLayerParent.class, EntityModelSet.class);
            BiFunction<RenderLayerParent, EntityModelSet, RenderLayer> factory = (parent, models) -> {
                try { return (RenderLayer) constructor.newInstance(parent, models); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot create Enigmatic elytra preview", failure); }
            };
            register(ResourceLocation.parse("enigmaticlegacyplus:majestic_elytra"), factory);
            register(ResourceLocation.parse("enigmaticlegacyplus:chaos_elytra"), factory);
        } catch (ReflectiveOperationException failure) {
            EquipmentStructureApiMod.LOGGER.warn("Enigmatic elytra preview adapter unavailable for this addon version", failure);
        }
    }
}
