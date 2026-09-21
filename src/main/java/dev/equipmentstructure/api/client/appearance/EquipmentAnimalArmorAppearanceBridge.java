package dev.equipmentstructure.api.client.appearance;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.appearance.AppearancePlan;
import dev.equipmentstructure.api.appearance.AppearanceVisibilityStorage;
import dev.equipmentstructure.api.internal.ExtensionGuard;
import dev.equipmentstructure.api.mixin.client.AgeableListModelAccessor;
import dev.equipmentstructure.api.mixin.client.AnimalArmorModelAccessor;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/** Native animal armor uses a body-bone frame shared by the editor and worn layers. */
public final class EquipmentAnimalArmorAppearanceBridge {
    private static final ExtensionGuard<ResourceLocation> GUARD = new ExtensionGuard<>("Animal armor appearance");
    private static final Set<ResourceLocation> AUTOMATIC_DISABLED = ConcurrentHashMap.newKeySet();
    private record Preview(ItemStack stack, ResourceLocation selected, BiConsumer<Matrix4f, AppearancePlan> capture) {}
    private static Preview preview;

    private EquipmentAnimalArmorAppearanceBridge() {}

    public static void setAutomaticEnabled(ResourceLocation host, boolean enabled) {
        Objects.requireNonNull(host, "host");
        if (enabled) AUTOMATIC_DISABLED.remove(host);
        else AUTOMATIC_DISABLED.add(host);
    }

    static void capturePreview(ItemStack stack, ResourceLocation selected,
                               BiConsumer<Matrix4f, AppearancePlan> capture, Runnable render) {
        var previous = preview;
        preview = new Preview(stack, selected, capture);
        try { render.run(); }
        finally { preview = previous; }
    }

    /** Called after native animation, before any native vertex consumer is acquired. */
    public static boolean renderLayer(ItemStack stack, AgeableListModel<?> model, PoseStack poses,
                                      MultiBufferSource buffers, int light, int overlay,
                                      LivingEntity wearer, float partialTick) {
        var structure = EquipmentStructureApi.structure(stack).orElse(null);
        if (structure == null || structure.components().isEmpty() || AUTOMATIC_DISABLED.contains(structure.hostId())
                || !(model instanceof AnimalArmorModelAccessor accessor)) return false;
        return GUARD.call(structure.hostId(), () -> {
            var support = AppearanceModelAssetRegistry.support(Set.of(), false);
            var plan = AppearanceRuntime.resolve(structure, support);
            if (plan.generation() != AppearanceResourceReloadListener.generation()
                    || plan.generation() != support.generation() || plan.placements().isEmpty()) return false;
            var body = accessor.equipmentStructureApi$body();
            if (!body.visible || body.skipDraw) return false;
            boolean inPreview = preview != null && preview.stack() == stack;
            boolean complete = plan.outcomes().stream().allMatch(outcome -> outcome.status() == AppearancePlan.Status.READY);
            for (var placement : plan.orderedPlacements()) {
                boolean capture = inPreview && placement.slotId().equals(preview.selected());
                if (!placement.visible() && !capture) continue;
                PoseStack local = new PoseStack();
                local.mulPose(poses.last().pose());
                local.last().normal().set(poses.last().normal());
                if (model.young) {
                    var age = (AgeableListModelAccessor) model;
                    float scale = 1 / age.equipmentStructureApi$babyBodyScale();
                    local.scale(scale, scale, scale);
                    local.translate(0, age.equipmentStructureApi$bodyYOffset() / 16, 0);
                }
                AppearanceBoneTransform.apply(body, local, placement.followsHostAnimation());
                local.scale(1 / 16F, -1 / 16F, -1 / 16F);
                if (capture) preview.capture().accept(new Matrix4f(local.last().pose()), plan);
                if (placement.visible()) {
                    complete &= AppearanceModelAssetRegistry.renderPlacement(placement, support,
                            EquipmentAppearanceScene.ARMOR, local, buffers, light, overlay,
                            AppearanceRenderSubject.animalArmor(stack, inPreview ? null : wearer, partialTick, inPreview));
                }
            }
            // The hook owns only this BODY armor layer, never the underlying animal mesh.
            return complete && !AppearanceVisibilityStorage.originalVisible(stack);
        }, false);
    }
}
