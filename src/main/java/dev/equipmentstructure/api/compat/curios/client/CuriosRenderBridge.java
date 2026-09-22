package dev.equipmentstructure.api.compat.curios.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.appearance.*;
import dev.equipmentstructure.api.compat.curios.*;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;
import java.util.function.BiConsumer;

/** Affine adjustment around the original renderer's attachment frame; native animation remains intact. */
public final class CuriosRenderBridge {
    private CuriosRenderBridge() {}
    public static <T extends LivingEntity, M extends EntityModel<T>> void render(ICurioRenderer renderer, ItemStack item,
            SlotContext context, PoseStack poses, RenderLayerParent<T, M> parent, MultiBufferSource buffers,
            int light, float swing, float amount, float partial, float age, float yaw, float pitch) {
        render(renderer, item, context, poses, parent, buffers, light, swing, amount, partial, age, yaw, pitch, null);
    }
    public static <T extends LivingEntity, M extends EntityModel<T>> void render(ICurioRenderer renderer, ItemStack item,
            SlotContext context, PoseStack poses, RenderLayerParent<T, M> parent, MultiBufferSource buffers,
            int light, float swing, float amount, float partial, float age, float yaw, float pitch,
            BiConsumer<ResourceLocation, Matrix4f> capture) {
        poses.pushPose();
        try {
            if (apply(item, context, poses, parent.getModel(), capture))
                renderer.render(item, context, poses, parent, buffers, light, swing, amount, partial, age, yaw, pitch);
        } finally { poses.popPose(); }
    }
    /** Caller owns push/pop. Returns false when the attachment is hidden. */
    public static boolean apply(ItemStack item, SlotContext context, PoseStack poses, EntityModel<?> model,
            BiConsumer<ResourceLocation, Matrix4f> capture) {
        var owner = CuriosArmorCompat.owner(context);
        if (owner.isEmpty()) return true;
        var key = new CuriosSlotKey(context.identifier(), context.index(), context.cosmetic());
        var part = EquipmentStructureApi.component(owner, key.slotId()).orElse(null);
        if (part == null) return true;
        var presentation = AppearancePartPresentation.read(part);
        if (capture == null && !presentation.visible()) return false;
        var adjustment = presentation.pose();
        if (capture == null && adjustment.equals(AppearancePose.IDENTITY)) return true;
        var frame = new PoseStack();
        if (CuriosItemModelRenderer.usesFallback(item.getItem())) CuriosItemModelRenderer.frame(item, context, model, frame);
        else CuriosRenderFrames.frame(item, context).apply(item, context, model, frame);
        // Reuse the editor's convention: 16 units per block, +Y up and +Z forward.
        frame.scale(1 / 16F, -1 / 16F, -1 / 16F);
        var basis = new Matrix4f(frame.last().pose());
        if (capture != null) capture.accept(key.slotId(), new Matrix4f(poses.last().pose()).mul(basis));
        if (!presentation.visible()) return false;
        var p = adjustment.transform().position(); var r = adjustment.transform().rotation();
        var delta = new Matrix4f(basis).translate((float) p.x(), (float) p.y(), (float) p.z())
                .rotate(new Quaternionf((float) r.x(), (float) r.y(), (float) r.z(), (float) r.w()))
                .scale((float) adjustment.scale()).mul(new Matrix4f(basis).invert());
        poses.mulPose(delta);
        return true;
    }
}
