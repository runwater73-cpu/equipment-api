package dev.equipmentstructure.api.client.appearance;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Item-like PNG extrusion. Pixel silhouette edges are prepared once per reload. */
@OnlyIn(Dist.CLIENT)
final class SpriteQuadRenderer {
    private SpriteQuadRenderer() {}

    static void render(AppearanceModelAssetRenderContext context, SpriteModelDefinition sprite, Mesh mesh) {
        var out = context.buffers().getBuffer(RenderType.entityCutout(sprite.texture()));
        emit(out, context.poseStack(), sprite.width(), sprite.height(), mesh, context.packedLight(), context.packedOverlay());
    }

    static void emit(VertexConsumer out, PoseStack pose, float width, float height, int light, int overlay) {
        emit(out, pose, width, height, new Mesh(0, List.of()), light, overlay);
    }

    static void emit(VertexConsumer out, PoseStack pose, float width, float height, Mesh mesh, int light, int overlay) {
        float x = width * .5f, y = height * .5f;
        // Opposite winding plus culling draws exactly one face per side, with the correct normal.
        for (int side : new int[]{1, -1}) {
            float z = side * mesh.depth() / 2;
            put(out, pose, -x * side, -y, z, side == 1 ? 0 : 1, 1, 0, 0, side, light, overlay);
            put(out, pose,  x * side, -y, z, side == 1 ? 1 : 0, 1, 0, 0, side, light, overlay);
            put(out, pose,  x * side,  y, z, side == 1 ? 1 : 0, 0, 0, 0, side, light, overlay);
            put(out, pose, -x * side,  y, z, side == 1 ? 0 : 1, 0, 0, 0, side, light, overlay);
        }
        for (var edge : mesh.edges()) {
            float z = mesh.depth() / 2;
            put(out, pose, edge.x1(), edge.y1(), -z, edge.u(), edge.v(), edge.nx(), edge.ny(), 0, light, overlay);
            put(out, pose, edge.x2(), edge.y2(), -z, edge.u(), edge.v(), edge.nx(), edge.ny(), 0, light, overlay);
            put(out, pose, edge.x2(), edge.y2(), z, edge.u(), edge.v(), edge.nx(), edge.ny(), 0, light, overlay);
            put(out, pose, edge.x1(), edge.y1(), z, edge.u(), edge.v(), edge.nx(), edge.ny(), 0, light, overlay);
        }
    }

    static Mesh mesh(SpriteModelDefinition sprite, BufferedImage pixels) {
        if (!sprite.extrude()) return new Mesh(0, List.of());
        int w = pixels.getWidth(), h = pixels.getHeight();
        float dx = sprite.width() / w, dy = sprite.height() / h;
        var edges = new ArrayList<Edge>();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (!opaque(pixels, x, y)) continue;
            float x1 = x * dx - sprite.width()/2, x2 = x1 + dx;
            float y2 = sprite.height()/2 - y*dy, y1 = y2 - dy;
            float u = (x+.5f)/w, v = (y+.5f)/h;
            if (!opaque(pixels,x-1,y)) edges.add(new Edge(x1,y2,x1,y1,u,v,-1,0));
            if (!opaque(pixels,x+1,y)) edges.add(new Edge(x2,y1,x2,y2,u,v,1,0));
            if (!opaque(pixels,x,y-1)) edges.add(new Edge(x2,y2,x1,y2,u,v,0,1));
            if (!opaque(pixels,x,y+1)) edges.add(new Edge(x1,y1,x2,y1,u,v,0,-1));
            if (edges.size() > 8192) throw new IllegalArgumentException("Sprite silhouette exceeds 8192 edges; simplify PNG or set extrude=false");
        }
        return new Mesh(Math.min(dx, dy), List.copyOf(edges));
    }

    private static boolean opaque(BufferedImage image, int x, int y) {
        return x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight() && (image.getRGB(x,y) >>> 24) >= 26;
    }
    record Mesh(float depth, List<Edge> edges) {}
    record Edge(float x1, float y1, float x2, float y2, float u, float v, float nx, float ny) {}

    private static void put(VertexConsumer out, PoseStack pose, float x, float y, float z,
                            float u, float v, float nx, float ny, float nz,
                            int light, int overlay) {
        out.addVertex(pose.last(), x, y, z).setColor(255, 255, 255, 255)
                .setUv(u, v).setOverlay(overlay).setLight(light)
                .setNormal(pose.last(), nx, ny, nz);
    }
}
