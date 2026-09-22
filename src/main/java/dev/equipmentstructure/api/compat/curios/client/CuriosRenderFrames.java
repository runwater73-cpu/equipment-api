package dev.equipmentstructure.api.compat.curios.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.compat.curios.CuriosArmorCompat;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client-only calibration. Does not replace or wrap entries in CuriosRendererRegistry. */
public final class CuriosRenderFrames {
    @FunctionalInterface public interface Frame {
        /** Apply the renderer's attachment frame in block units, before the user's model-unit adjustment. */
        void apply(ItemStack item, SlotContext slot, EntityModel<?> model, PoseStack poses);
    }
    public enum Bone implements Frame {
        ROOT, HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG;
        @Override public void apply(ItemStack item, SlotContext slot, EntityModel<?> model, PoseStack poses) {
            if (!(model instanceof HumanoidModel<?> humanoid)) return;
            ModelPart part = switch (this) {
                case ROOT -> null; case HEAD -> humanoid.head; case BODY -> humanoid.body;
                case LEFT_ARM -> humanoid.leftArm; case RIGHT_ARM -> humanoid.rightArm;
                case LEFT_LEG -> humanoid.leftLeg; case RIGHT_LEG -> humanoid.rightLeg;
            };
            if (part != null) part.translateAndRotate(poses);
        }
    }
    private static final Map<ResourceLocation, Frame> ITEMS = new ConcurrentHashMap<>();
    private static final Map<String, Frame> SLOTS = new ConcurrentHashMap<>();
    private CuriosRenderFrames() {}
    public static void registerItem(ResourceLocation item, Frame frame) { ITEMS.put(item, java.util.Objects.requireNonNull(frame)); }
    public static void registerSlot(String nativeSlot, Frame frame) { SLOTS.put(nativeSlot, java.util.Objects.requireNonNull(frame)); }
    static Frame frame(ItemStack item, SlotContext context) {
        var itemFrame = ITEMS.get(BuiltInRegistries.ITEM.getKey(item.getItem()));
        if (itemFrame != null) return itemFrame;
        return SLOTS.getOrDefault(context.identifier(),
                CuriosArmorCompat.definitions(context.entity()).profile(context.identifier()).armorSlot()
                        == net.minecraft.world.entity.EquipmentSlot.HEAD ? Bone.HEAD : Bone.BODY);
    }
}
