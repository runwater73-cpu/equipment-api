package dev.equipmentstructure.api.client.appearance;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.appearance.AppearanceSupport;
import dev.equipmentstructure.api.appearance.AppearanceVisibilityStorage;
import dev.equipmentstructure.api.internal.ExtensionGuard;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Bridge for ElytraLayer, whose wings are not HumanoidArmorLayer bones. */
public final class EquipmentElytraAppearanceBridge {
    private static final Map<ResourceLocation, Supplier<AppearanceSupport>> BINDINGS = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> AUTOMATIC_DISABLED = ConcurrentHashMap.newKeySet();
    private static final ExtensionGuard<ResourceLocation> GUARD = new ExtensionGuard<>("Elytra appearance");
    private static final Supplier<AppearanceSupport> DEFAULT_SUPPORT =
            () -> AppearanceModelAssetRegistry.support(Set.of(), false);
    private EquipmentElytraAppearanceBridge() {}

    public static void register(ResourceLocation host, Supplier<AppearanceSupport> support) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(support, "support");
        if (BINDINGS.putIfAbsent(host, support) != null) {
            throw new IllegalStateException("Elytra appearance already registered for " + host);
        }
    }

    /**
     * Disables the standard ElytraLayer attachment mapping for a host that owns
     * its own attachment rendering. Explicit registrations still take priority.
     */
    public static void setAutomaticEnabled(ResourceLocation host, boolean enabled) {
        Objects.requireNonNull(host, "host");
        if (enabled) AUTOMATIC_DISABLED.remove(host);
        else AUTOMATIC_DISABLED.add(host);
    }

    public static boolean unregister(ResourceLocation host) {
        Objects.requireNonNull(host, "host");
        GUARD.forget(host);
        return BINDINGS.remove(host) != null;
    }

    /** Renders attached parts on both wings. Returns true when the original Elytra mesh should be skipped. */
    public static boolean renderLayer(ItemStack armor, ElytraModel<?> model, PoseStack poses,
                                      MultiBufferSource buffers, int light, int overlay,
                                      LivingEntity wearer, float partialTick) {
        if (armor.isEmpty() || model == null || poses == null || buffers == null) return false;
        var structure = EquipmentStructureApi.structure(armor).orElse(null);
        if (structure == null || structure.components().isEmpty()) return false;
        var host = structure.hostId();
        var factory = BINDINGS.get(host);
        boolean explicit = factory != null;
        if (factory == null) {
            if (AUTOMATIC_DISABLED.contains(host)) return false;
            factory = DEFAULT_SUPPORT;
        }
        var supportFactory = factory;
        return GUARD.call(host, () -> {
            var supplied = supportFactory.get();
            if (supplied == null) return false;
            var support = new AppearanceSupport(supplied.generation(), supplied.preparedAssets(), supplied.capabilities(), false);
            var plan = AppearanceRuntime.resolve(structure, support);
            if (plan.generation() != AppearanceResourceReloadListener.generation()
                    || plan.generation() != support.generation() || plan.placements().isEmpty()) return false;
            var left = ((dev.equipmentstructure.api.mixin.client.ElytraModelAccessor) model).equipmentStructureApi$leftWing();
            var right = ((dev.equipmentstructure.api.mixin.client.ElytraModelAccessor) model).equipmentStructureApi$rightWing();
            boolean complete = plan.outcomes().stream().allMatch(outcome ->
                    outcome.status() == dev.equipmentstructure.api.appearance.AppearancePlan.Status.READY);
            for (var placement : plan.orderedPlacements()) {
                if (!placement.visible()) continue;
                if (AppearanceModelAssetRegistry.get(placement.asset()).isEmpty()) { complete = false; continue; }
                for (var wing : new ModelPart[]{left, right}) {
                    if (!wing.visible || wing.skipDraw) { complete = false; continue; }
                    PoseStack local = new PoseStack();
                    local.mulPose(poses.last().pose());
                    local.last().normal().set(poses.last().normal());
                    if (model.young) {
                        var age = (dev.equipmentstructure.api.mixin.client.AgeableListModelAccessor) model;
                        float scale = 1 / age.equipmentStructureApi$babyBodyScale();
                        local.scale(scale, scale, scale);
                        local.translate(0, age.equipmentStructureApi$bodyYOffset() / 16, 0);
                    }
                    AppearanceBoneTransform.apply(wing, local, placement.followsHostAnimation());
                    local.scale(1 / 16F, -1 / 16F, -1 / 16F);
                    complete &= AppearanceModelAssetRegistry.renderPlacement(placement, support,
                            EquipmentAppearanceScene.ARMOR, local, buffers, light, overlay,
                            AppearanceRenderSubject.armor(armor, EquipmentSlot.CHEST, null, wearer, partialTick));
                }
            }
            // A default binding is overlay-only. It must never suppress the
            // vanilla Elytra mesh, even when the player requested hiding it.
            return complete && explicit && !AppearanceVisibilityStorage.originalVisible(armor);
        }, false);
    }

    public static void clear() {
        BINDINGS.clear();
        AUTOMATIC_DISABLED.clear();
        GUARD.clear();
    }
}
