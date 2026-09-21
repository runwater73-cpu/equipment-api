package dev.equipmentstructure.api.client.appearance;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppearancePreviewSelectionTest {
    private static final ResourceLocation BACK = ResourceLocation.parse("test:back"), FRONT = ResourceLocation.parse("test:front");
    private static final class Sink implements VertexConsumer {
        int r, g, b, vertices;
        public VertexConsumer addVertex(float x, float y, float z) { vertices++; return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) { this.r = r; this.g = g; this.b = b; return this; }
        public VertexConsumer setUv(float u, float v) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
    @Test void picksFrontmostGeometryAndIgnoresEmptySpaceInsideBounds() {
        var sink = new Sink();
        var selection = new AppearancePreviewSelection(type -> sink, FRONT);
        var back = selection.capture(BACK, sink, VertexFormat.Mode.QUADS);
        quad(back, 0);
        var front = selection.capture(FRONT, sink, VertexFormat.Mode.QUADS);
        quad(front, 10);
        assertEquals(FRONT, selection.hit(2, 2));
        assertNull(selection.hit(20, 20));
        assertEquals(8, sink.vertices);
        assertEquals(255, sink.r);
        assertTrue(sink.b < sink.g);
        // A separate preview has no lingering geometry or tint from the selected preview.
        var fresh = new AppearancePreviewSelection(t -> sink, null);
        quad(fresh.capture(BACK, sink, VertexFormat.Mode.QUADS), 0);
        assertEquals(BACK, fresh.hit(2, 2));
        assertEquals(255, sink.b);
        assertNull(fresh.hit(9, 9)); // Outside the slanted quad although inside its bounding rectangle.
    }
    private static void quad(VertexConsumer v, float z) {
        for (var p : new float[][]{{0, 0}, {10, 0}, {4, 10}, {0, 10}}) {
            v.addVertex(p[0], p[1], z).setColor(255, 255, 255, 255);
        }
    }
}
