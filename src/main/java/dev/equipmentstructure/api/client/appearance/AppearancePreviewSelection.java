package dev.equipmentstructure.api.client.appearance;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import java.util.*;

/** Preview-local geometry picking and tinting. No global render state or changes to saved appearance. */
public final class AppearancePreviewSelection implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final ResourceLocation selected;
    private final Map<ResourceLocation, Mesh> meshes = new LinkedHashMap<>();
    /** Geometry emitted by the host item/model. It is a snap target, never a selectable part. */
    private final Mesh hostMesh = new Mesh();
    private int triangles;

    public AppearancePreviewSelection(MultiBufferSource delegate, ResourceLocation selected) {
        this.delegate = delegate;
        this.selected = selected;
    }

    @Override public VertexConsumer getBuffer(RenderType type) {
        return new Capture(delegate.getBuffer(type), hostMesh,
                new Primitive(type.mode()), false);
    }

    public MultiBufferSource forPart(ResourceLocation slot) {
        var mesh = meshes.computeIfAbsent(slot, ignored -> new Mesh());
        Map<RenderType, Primitive> primitives = new HashMap<>();
        return type -> new Capture(delegate.getBuffer(type), mesh,
                primitives.computeIfAbsent(type, ignored -> new Primitive(type.mode())), slot.equals(selected));
    }

    VertexConsumer capture(ResourceLocation slot, VertexConsumer output, VertexFormat.Mode mode) {
        return new Capture(output, meshes.computeIfAbsent(slot, ignored -> new Mesh()), new Primitive(mode), slot.equals(selected));
    }

    public ResourceLocation hit(double x, double y) {
        ResourceLocation best = null;
        double depth = -Double.MAX_VALUE;
        for (var entry : meshes.entrySet()) {
            for (var triangle : entry.getValue().faces) {
                double value = triangle.depth(x, y);
                if (value > depth) { depth = value; best = entry.getKey(); }
            }
        }
        return best;
    }

    public Optional<Bounds> bounds(ResourceLocation slot) {
        var mesh = meshes.get(slot);
        if (mesh == null || mesh.faces.isEmpty()) return Optional.empty();
        return Optional.of(new Bounds(mesh.minX, mesh.minY, mesh.maxX, mesh.maxY));
    }

    /** Returns the nearest point on captured host geometry in preview screen coordinates. */
    public Optional<SnapPoint> nearestHostPoint(double x, double y) {
        if (hostMesh.faces.isEmpty() || !Double.isFinite(x) || !Double.isFinite(y)) return Optional.empty();
        SnapPoint best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (var face : hostMesh.faces) {
            var point = face.closest(x, y);
            double dx = point.x() - x, dy = point.y() - y;
            double squared = dx * dx + dy * dy;
            if (squared < distance) {
                distance = squared;
                best = point;
            }
        }
        return Optional.ofNullable(best);
    }

    public record SnapPoint(float x, float y, float z) {}

    public record Bounds(float minX, float minY, float maxX, float maxY) {
        public float centerX() { return (minX + maxX) / 2; }
        public float centerY() { return (minY + maxY) / 2; }
    }

    private static final class Mesh {
        final List<Triangle> faces = new ArrayList<>();
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
    }

    private record Triangle(Vector3f a, Vector3f b, Vector3f c) {
        double depth(double x, double y) {
            double det = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y);
            if (Math.abs(det) < 1e-7) return -Double.MAX_VALUE;
            double u = ((b.y - c.y) * (x - c.x) + (c.x - b.x) * (y - c.y)) / det;
            double v = ((c.y - a.y) * (x - c.x) + (a.x - c.x) * (y - c.y)) / det;
            if (u < 0 || v < 0 || u + v > 1) return -Double.MAX_VALUE;
            return u * a.z + v * b.z + (1 - u - v) * c.z;
        }

        SnapPoint closest(double x, double y) {
            double det = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y);
            if (Math.abs(det) >= 1e-7) {
                double u = ((b.y - c.y) * (x - c.x) + (c.x - b.x) * (y - c.y)) / det;
                double v = ((c.y - a.y) * (x - c.x) + (a.x - c.x) * (y - c.y)) / det;
                double w = 1 - u - v;
                if (u >= 0 && v >= 0 && w >= 0) {
                    return new SnapPoint((float) x, (float) y, (float) (u * a.z + v * b.z + w * c.z));
                }
            }
            SnapPoint ab = edgeClosest(a, b, x, y), bc = edgeClosest(b, c, x, y), ca = edgeClosest(c, a, x, y);
            double da = distanceSquared(ab, x, y), db = distanceSquared(bc, x, y), dc = distanceSquared(ca, x, y);
            return da <= db && da <= dc ? ab : db <= dc ? bc : ca;
        }

        private static SnapPoint edgeClosest(Vector3f p, Vector3f q, double x, double y) {
            double dx = q.x - p.x, dy = q.y - p.y;
            double t = dx * dx + dy * dy < 1e-9 ? 0 : Math.clamp(((x - p.x) * dx + (y - p.y) * dy) / (dx * dx + dy * dy), 0, 1);
            return new SnapPoint((float) (p.x + dx * t), (float) (p.y + dy * t), (float) (p.z + (q.z - p.z) * t));
        }

        private static double distanceSquared(SnapPoint p, double x, double y) {
            double dx = p.x() - x, dy = p.y() - y;
            return dx * dx + dy * dy;
        }
    }

    private final class Primitive {
        final VertexFormat.Mode mode;
        final Vector3f[] points = new Vector3f[4];
        int count;
        Primitive(VertexFormat.Mode mode) { this.mode = mode; }
        void vertex(Mesh mesh, float x, float y, float z) {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) { count = 0; return; }
            if (triangles >= 32768) return; // Bound preview work for arbitrary third-party meshes.
            mesh.minX = Math.min(mesh.minX, x); mesh.maxX = Math.max(mesh.maxX, x);
            mesh.minY = Math.min(mesh.minY, y); mesh.maxY = Math.max(mesh.maxY, y);
            points[count++] = new Vector3f(x, y, z);
            if (mode == VertexFormat.Mode.QUADS && count == 4) {
                add(mesh, points[0], points[1], points[2]); add(mesh, points[0], points[2], points[3]); count = 0;
            } else if (mode == VertexFormat.Mode.TRIANGLES && count == 3) {
                add(mesh, points[0], points[1], points[2]); count = 0;
            } else if (mode == VertexFormat.Mode.TRIANGLE_STRIP && count == 3) {
                add(mesh, points[0], points[1], points[2]); points[0] = points[1]; points[1] = points[2]; count = 2;
            } else if (mode == VertexFormat.Mode.TRIANGLE_FAN && count == 3) {
                add(mesh, points[0], points[1], points[2]); points[1] = points[2]; count = 2;
            } else if (count == 4) count = 0;
        }
        void add(Mesh mesh, Vector3f a, Vector3f b, Vector3f c) { mesh.faces.add(new Triangle(a, b, c)); triangles++; }
    }

    private final class Capture implements VertexConsumer {
        final VertexConsumer out;
        final Mesh mesh;
        final Primitive primitive;
        final boolean highlight;
        Capture(VertexConsumer out, Mesh mesh, Primitive primitive, boolean highlight) {
            this.out = out; this.mesh = mesh; this.primitive = primitive; this.highlight = highlight;
        }
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            primitive.vertex(mesh, x, y, z); out.addVertex(x, y, z); return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) {
            out.setColor(highlight ? 255 : r, highlight ? Math.max(180, g * 4 / 5) : g,
                    highlight ? b / 3 : b, a); return this;
        }
        @Override public VertexConsumer setUv(float u, float v) { out.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { out.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { out.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { out.setNormal(x, y, z); return this; }
    }
}
