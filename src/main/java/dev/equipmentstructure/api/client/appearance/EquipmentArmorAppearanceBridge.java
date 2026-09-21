package dev.equipmentstructure.api.client.appearance;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.appearance.AppearancePlan;
import dev.equipmentstructure.api.appearance.AppearanceSupport;
import dev.equipmentstructure.api.appearance.AppearanceVisibilityStorage;
import dev.equipmentstructure.api.internal.ExtensionGuard;
import dev.equipmentstructure.api.mixin.client.AgeableListModelAccessor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Humanoid armor adapter, called after the armor item's animation hook. */
public final class EquipmentArmorAppearanceBridge {
    private static final ExtensionGuard<ResourceLocation> GUARD = new ExtensionGuard<>("Armor appearance");
    private static ItemStack previewStack;
    private static ResourceLocation previewComponentSlot;
    private static BiConsumer<Matrix4f, AppearancePlan> previewCapture;
    private static boolean previewCaptured;

    private EquipmentArmorAppearanceBridge() {}

    public static void register(ResourceLocation host, Supplier<AppearanceSupport> support) {
        AppearanceArmorBindings.register(host, support);
    }

    public static void register(ResourceLocation host, EquipmentSlot slot, Supplier<AppearanceSupport> support) {
        AppearanceArmorBindings.register(host, slot, support);
    }

    public static void register(ResourceLocation host, EquipmentSlot slot, float modelUnitsPerBlock,
                                Map<ResourceLocation, Set<ArmorAttachment>> attachments,
                                Supplier<AppearanceSupport> support) {
        AppearanceArmorBindings.register(host, slot, modelUnitsPerBlock, attachments, support);
    }

    /** Client render-thread scope used by the placement editor to locate one armor attachment frame. */
    public static void capturePreview(ItemStack stack, ResourceLocation componentSlot,
                                      BiConsumer<Matrix4f, AppearancePlan> capture, Runnable render) {
        ItemStack previousStack = previewStack;
        ResourceLocation previousSlot = previewComponentSlot;
        var previousCapture = previewCapture;
        boolean previousCaptured = previewCaptured;
        previewStack = Objects.requireNonNull(stack, "stack");
        previewComponentSlot = componentSlot;
        previewCapture = Objects.requireNonNull(capture, "capture");
        previewCaptured = false;
        try { render.run(); }
        finally {
            previewStack = previousStack;
            previewComponentSlot = previousSlot;
            previewCapture = previousCapture;
            previewCaptured = previousCaptured;
        }
    }

    /** Draws attachments and returns whether the caller should skip this armor piece's base, trim and foil. */
    public static boolean renderLayer(ItemStack armor, EquipmentSlot slot, Model model, PoseStack poses,
                                      MultiBufferSource buffers, int light, int overlay) {
        return renderLayer(armor, slot, model, poses, buffers, light, overlay, null, 0);
    }

    public static boolean renderLayer(ItemStack armor, EquipmentSlot slot, Model model, PoseStack poses,
                                      MultiBufferSource buffers, int light, int overlay,
                                      LivingEntity wearer, float partialTick) {
        if (!(model instanceof HumanoidModel<?> humanoid) || armor.isEmpty()
                || slot == null || ArmorAttachment.defaults(slot).isEmpty()) return false;
        var structure = EquipmentStructureApi.structure(armor).orElse(null);
        if (structure == null || structure.components().isEmpty()) return false;
        var binding = AppearanceArmorBindings.resolve(structure.hostId(), slot);
        if (binding == null) return false;
        var activeBinding = binding;
        return GUARD.call(structure.hostId(), () -> {
            var supplied = activeBinding.support().get();
            if (supplied == null) return false;
            var support = new AppearanceSupport(supplied.generation(), supplied.preparedAssets(), supplied.capabilities(), false);
            var plan = AppearanceRuntime.resolve(structure, support);
            if (plan.generation() != AppearanceResourceReloadListener.generation() || plan.placements().isEmpty()) return false;
            // Hidden placements remain valid; failed visible callbacks restore the base armor.
            boolean complete = plan.outcomes().stream().allMatch(outcome ->
                    outcome.status() == dev.equipmentstructure.api.appearance.AppearancePlan.Status.READY);
            for (var placement : plan.orderedPlacements()) {
                var anchors = activeBinding.anchors(placement.slotId(), slot);
                for (var anchor : ArmorAttachment.values()) {
                    if (!anchors.contains(anchor)) continue;
                    if (!anchor.supports(slot) || AppearanceModelAssetRegistry.get(placement.asset()).isEmpty()) {
                        complete = false;
                        continue;
                    }
                    var part = anchor.part(humanoid);
                    boolean previewTarget = armor == previewStack && !previewCaptured
                            && Objects.equals(placement.slotId(), previewComponentSlot);
                    if (!placement.visible() && !previewTarget) continue;
                    if (!part.visible || part.skipDraw) {
                        if (placement.visible()) complete = false;
                        continue;
                    }
                    PoseStack local = new PoseStack();
                    local.mulPose(poses.last().pose());
                    local.last().normal().set(poses.last().normal());
                    applyAge(humanoid, anchor, local);
                    anchor.apply(humanoid, local, placement.followsHostAnimation());
                    float scale = 1 / activeBinding.modelUnitsPerBlock();
                    local.scale(scale, -scale, -scale);
                    if (previewTarget) {
                        previewCaptured = true;
                        previewCapture.accept(new Matrix4f(local.last().pose()), plan);
                    }
                    if (!placement.visible()) continue;
                    var subject = armor == previewStack
                            ? AppearanceRenderSubject.editorArmor(armor, slot, anchor)
                            : AppearanceRenderSubject.armor(armor, slot, anchor, wearer, partialTick);
                    complete &= AppearanceModelAssetRegistry.renderPlacement(placement, support,
                            EquipmentAppearanceScene.ARMOR, local, buffers, light, overlay,
                            subject);
                }
            }
            return activeBinding.canHideOriginal() && complete && !AppearanceVisibilityStorage.originalVisible(armor);
        }, false);
    }

    private static void applyAge(HumanoidModel<?> model, ArmorAttachment anchor, PoseStack poses) {
        if (!model.young) return;
        var age = (AgeableListModelAccessor) model;
        if (anchor == ArmorAttachment.HEAD) {
            if (age.equipmentStructureApi$scaleHead()) {
                float scale = 1.5F / age.equipmentStructureApi$babyHeadScale();
                poses.scale(scale, scale, scale);
            }
            poses.translate(0, age.equipmentStructureApi$babyYHeadOffset() / 16,
                    age.equipmentStructureApi$babyZHeadOffset() / 16);
        } else {
            float scale = 1 / age.equipmentStructureApi$babyBodyScale();
            poses.scale(scale, scale, scale);
            poses.translate(0, age.equipmentStructureApi$bodyYOffset() / 16, 0);
        }
    }

    /** Manual adapter entry; caller owns animation, units, base-model visibility and pose. */
    public static int renderSlot(ItemStack armor, EquipmentSlot slot, AppearanceSupport support,
                                 PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        if (armor.isEmpty() || slot == null || ArmorAttachment.defaults(slot).isEmpty()
                || support == null || poses == null || buffers == null) return 0;
        var structure = EquipmentStructureApi.structure(armor).orElse(null);
        if (structure == null || AppearanceArmorBindings.get(structure.hostId(), slot) == null) return 0;
        return GUARD.call(structure.hostId(), () -> AppearanceModelAssetRegistry.render(
                AppearanceRuntime.resolve(structure, support), support, EquipmentAppearanceScene.ARMOR,
                poses, buffers, light, overlay, AppearanceRenderSubject.armor(armor, slot, null, null, 0)), 0);
    }
}
