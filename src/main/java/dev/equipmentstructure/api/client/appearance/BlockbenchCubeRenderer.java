package dev.equipmentstructure.api.client.appearance;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;

/**
 * Small, renderer-agnostic default for Blockbench {@code java_block} cube models.
 * It intentionally handles cubes only; authors can register a custom renderer for
 * parent bones, translucent passes, or non-cubic geometry. Single-axis cube rotations are supported.
 */
@OnlyIn(Dist.CLIENT)
public final class BlockbenchCubeRenderer {
    private BlockbenchCubeRenderer() {}

    /** Renders all faces with a valid, present texture and leaves missing faces invisible. */
    public static void render(AppearanceModelAssetRenderContext context,
                              BlockbenchModelDefinition model) {
        var modelResource = model.modelResource().orElse(null);
        if (modelResource == null || model.textures().isEmpty()) return;
        var manager = Minecraft.getInstance().getResourceManager();
        for (var element : model.elements()) {
            context.poseStack().pushPose();
            try {
                applyRotation(context.poseStack(), element.rotation().orElse(null));
                for (var entry : element.faces().entrySet()) {
                    var face = entry.getValue();
                    if (face.textureIndex() < 0 || face.textureIndex() >= model.textures().size()) continue;
                    var texture = BlockbenchTextureResolver.resolve(manager, modelResource,
                                    model.textures().get(face.textureIndex()),
                                    AppearanceResourceReloadListener.generation())
                            .orElse(null);
                    if (texture == null) continue;
                    renderFace(context, model, element, entry.getKey(), face, texture);
                }
            } finally {
                context.poseStack().popPose();
            }
        }
    }

    private static void applyRotation(PoseStack poses, BlockbenchModelDefinition.Rotation rotation) {
        if (rotation == null || rotation.angle() == 0.0F) return;
        var origin = rotation.origin();
        float ox = (float) origin.x() - 8.0F;
        float oy = (float) origin.y() - 8.0F;
        float oz = (float) origin.z() - 8.0F;
        poses.translate(ox, oy, oz);
        Vector3f axis = switch (rotation.axis()) {
            case "x" -> new Vector3f(1, 0, 0);
            case "y" -> new Vector3f(0, 1, 0);
            case "z" -> new Vector3f(0, 0, 1);
            default -> throw new IllegalStateException("Unsupported Blockbench rotation axis");
        };
        poses.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(rotation.angle()),
                axis.x, axis.y, axis.z));
        poses.translate(-ox, -oy, -oz);
    }

    private static void renderFace(AppearanceModelAssetRenderContext context,
                                   BlockbenchModelDefinition model,
                                   BlockbenchModelDefinition.Element element,
                                   String direction,
                                   BlockbenchModelDefinition.Face face,
                                   ResourceLocation texture) {
        float x1 = (float) element.from().x() - 8.0F;
        float x2 = (float) element.to().x() - 8.0F;
        // Blockbench/Java models are Y-up, centred at (8,8,8). Keep UV top rows at max Y.
        float y1 = (float) element.to().y() - 8.0F;
        float y2 = (float) element.from().y() - 8.0F;
        float z1 = (float) element.from().z() - 8.0F;
        float z2 = (float) element.to().z() - 8.0F;
        float u1 = face.u0() / model.textureWidth();
        float u2 = face.u1() / model.textureWidth();
        float v1 = face.v0() / model.textureHeight();
        float v2 = face.v1() / model.textureHeight();
        var vertices = context.buffers().getBuffer(RenderType.entityCutoutNoCull(texture));
        switch (direction) {
            case "north" -> quad(vertices, context.poseStack(),
                    x2, y1, z1, u1, v1, x1, y1, z1, u2, v1,
                    x1, y2, z1, u2, v2, x2, y2, z1, u1, v2, 0, 0, -1,
                    context.packedLight(), context.packedOverlay());
            case "south" -> quad(vertices, context.poseStack(),
                    x1, y1, z2, u1, v1, x2, y1, z2, u2, v1,
                    x2, y2, z2, u2, v2, x1, y2, z2, u1, v2, 0, 0, 1,
                    context.packedLight(), context.packedOverlay());
            case "east" -> quad(vertices, context.poseStack(),
                    x2, y1, z2, u1, v1, x2, y1, z1, u2, v1,
                    x2, y2, z1, u2, v2, x2, y2, z2, u1, v2, 1, 0, 0,
                    context.packedLight(), context.packedOverlay());
            case "west" -> quad(vertices, context.poseStack(),
                    x1, y1, z1, u1, v1, x1, y1, z2, u2, v1,
                    x1, y2, z2, u2, v2, x1, y2, z1, u1, v2, -1, 0, 0,
                    context.packedLight(), context.packedOverlay());
            case "up" -> quad(vertices, context.poseStack(),
                    x1, y1, z1, u1, v1, x2, y1, z1, u2, v1,
                    x2, y1, z2, u2, v2, x1, y1, z2, u1, v2, 0, 1, 0,
                    context.packedLight(), context.packedOverlay());
            case "down" -> quad(vertices, context.poseStack(),
                    x1, y2, z2, u1, v1, x2, y2, z2, u2, v1,
                    x2, y2, z1, u2, v2, x1, y2, z1, u1, v2, 0, -1, 0,
                    context.packedLight(), context.packedOverlay());
            default -> { }
        }
    }

    private static void quad(VertexConsumer vertices, PoseStack poses,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3,
                             float x4, float y4, float z4, float u4, float v4,
                             float nx, float ny, float nz, int light, int overlay) {
        put(vertices, poses, x1, y1, z1, u1, v1, nx, ny, nz, light, overlay);
        put(vertices, poses, x2, y2, z2, u2, v2, nx, ny, nz, light, overlay);
        put(vertices, poses, x3, y3, z3, u3, v3, nx, ny, nz, light, overlay);
        put(vertices, poses, x4, y4, z4, u4, v4, nx, ny, nz, light, overlay);
    }

    private static void put(VertexConsumer vertices, PoseStack poses,
                            float x, float y, float z, float u, float v,
                            float nx, float ny, float nz, int light, int overlay) {
        vertices.addVertex(poses.last(), x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(poses.last(), nx, ny, nz);
    }
}
