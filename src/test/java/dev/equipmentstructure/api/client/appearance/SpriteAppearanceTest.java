package dev.equipmentstructure.api.client.appearance;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class SpriteAppearanceTest {
    static final ResourceLocation ASSET = ResourceLocation.parse("test:appearance/gem");
    static final ResourceLocation JSON = ResourceLocation.parse("test:" + SpriteModelAssetRegistry.DIRECTORY + "/appearance/gem.json");
    static final ResourceLocation PNG = ResourceLocation.parse("test:textures/item/gem.png");

    @AfterEach void clear() {
        SpriteModelAssetRegistry.install(Map.of());
        AppearanceModelAssetRegistry.clear();
    }

    @Test void decodesFileTextureAndRetainsNestedAssetId() {
        assertEquals(ASSET, SpriteModelAssetRegistry.logicalId(JSON));
        for (String texture : List.of("test:item/gem", "test:item/gem.png", "test:textures/item/gem.png")) {
            var sprite = SpriteModelAssetRegistry.decode(ASSET,
                    JsonParser.parseString("{\"texture\":\"" + texture + "\",\"width\":4,\"height\":6}").getAsJsonObject());
            assertEquals(PNG, sprite.texture());
            assertEquals(4, sprite.width());
            assertEquals(6, sprite.height());
        }
        for (String extra : List.of("\"width\":0", "\"height\":65", "\"width\":\"NaN\"", "\"format_version\":2")) {
            assertThrows(IllegalArgumentException.class, () -> SpriteModelAssetRegistry.decode(ASSET,
                    JsonParser.parseString("{\"texture\":\"test:item/gem\"," + extra + "}").getAsJsonObject()));
        }
    }

    @Test void reloadRemovesMissingCorruptAndTransparentAssetsAndCanRestoreThem() throws Exception {
        var resources = new HashMap<ResourceLocation, byte[]>();
        resources.put(JSON, "{\"format_version\":1,\"texture\":\"test:item/gem\"}".getBytes(StandardCharsets.UTF_8));
        var manager = manager(resources);
        var listener = new SpriteModelAssetRegistry.ReloadListener();
        resources.put(PNG, png(255));
        reload(listener, manager);
        assertEquals(Set.of(ASSET), SpriteModelAssetRegistry.preparedAssets());
        assertTrue(AppearanceModelAssetRegistry.assets().contains(ASSET));
        resources.remove(PNG);
        reload(listener, manager);
        assertFalse(AppearanceModelAssetRegistry.assets().contains(ASSET));
        for (byte[] broken : List.of(new byte[]{1,2,3}, png(0), png(1))) {
            resources.put(PNG, broken);
            reload(listener, manager);
            assertTrue(SpriteModelAssetRegistry.preparedAssets().isEmpty());
            assertTrue(AppearanceModelAssetRegistry.get(ASSET).isEmpty());
        }
        resources.put(PNG, png(255));
        reload(listener, manager);
        assertTrue(AppearanceModelAssetRegistry.get(ASSET).isPresent());
        resources.remove(JSON);
        reload(listener, manager);
        assertTrue(AppearanceModelAssetRegistry.get(ASSET).isEmpty());
    }

    @Test void reloadDoesNotRemoveAReplacementOwnedByAnAuthor() {
        var sprite = new SpriteModelDefinition(ASSET, PNG, 4, 4);
        SpriteModelAssetRegistry.install(Map.of(ASSET, sprite));
        AppearanceModelAssetRegistry.unregister(ASSET);
        AppearanceModelAssetRenderer author = context -> {};
        AppearanceModelAssetRegistry.register(ASSET, author);
        SpriteModelAssetRegistry.install(Map.of(ASSET, sprite));
        assertSame(author, AppearanceModelAssetRegistry.get(ASSET).orElseThrow());
        assertTrue(SpriteModelAssetRegistry.preparedAssets().isEmpty());
        SpriteModelAssetRegistry.install(Map.of());
        assertSame(author, AppearanceModelAssetRegistry.get(ASSET).orElseThrow());
    }

    @Test void planeHasOppositeWindingConsistentUvAndModelUnitSize() {
        var sink = new Sink();
        var poses = new PoseStack();
        poses.translate(10, 20, 30);
        SpriteQuadRenderer.emit(sink, poses, 4, 6, 0xF000F0, 0);
        assertEquals(8, sink.vertices.size());
        for (int side = 0; side < 2; side++) {
            var a = sink.vertices.get(side * 4);
            var b = sink.vertices.get(side * 4 + 1);
            var c = sink.vertices.get(side * 4 + 2);
            float cross = (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
            assertTrue(cross * a[7] > 0);
        }
        for (var v : sink.vertices) {
            assertEquals(30, v[2]);
            assertEquals((v[0] - 8) / 4, v[3], 1e-6);
            assertEquals((23 - v[1]) / 6, v[4], 1e-6);
            assertEquals(0, v[5]); assertEquals(0, v[6]);
        }
    }

    @Test void extrusionFollowsOpaquePixelsWithoutInternalWallsAndCanBeDisabled() {
        var pixels = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        pixels.setRGB(1, 1, 0xFFFFFFFF); pixels.setRGB(2, 1, 0xFFFFFFFF);
        var mesh = SpriteQuadRenderer.mesh(new SpriteModelDefinition(ASSET, PNG, 4, 4), pixels);
        assertEquals(1, mesh.depth());
        assertEquals(6, mesh.edges().size());
        var sink = new Sink();
        SpriteQuadRenderer.emit(sink, new PoseStack(), 4, 4, mesh, 0, 0);
        assertEquals(32, sink.vertices.size());
        for (int i = 8; i < sink.vertices.size(); i += 4) {
            var a = sink.vertices.get(i); var b = sink.vertices.get(i+1); var c = sink.vertices.get(i+2);
            var cross = new org.joml.Vector3f(b[0]-a[0], b[1]-a[1], b[2]-a[2])
                    .cross(new org.joml.Vector3f(c[0]-a[0],c[1]-a[1],c[2]-a[2]));
            assertTrue(cross.dot(a[5],a[6],a[7]) > 0, "Side winding must match its outward normal");
            assertEquals(255, pixels.getRGB((int)(a[3]*4),(int)(a[4]*4)) >>> 24);
        }
        var flat = SpriteQuadRenderer.mesh(new SpriteModelDefinition(ASSET, PNG, 4, 4, false), pixels);
        assertEquals(0, flat.depth()); assertTrue(flat.edges().isEmpty());
        var complex = new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<128;y++) for (int x=0;x<128;x++) if ((x+y)%2==0) complex.setRGB(x,y,0xFFFFFFFF);
        assertThrows(IllegalArgumentException.class, () -> SpriteQuadRenderer.mesh(new SpriteModelDefinition(ASSET,PNG,4,4),complex));
    }

    private static void reload(SpriteModelAssetRegistry.ReloadListener listener, ResourceManager manager) {
        listener.apply(listener.prepare(manager, null), manager, null);
    }
    private static byte[] png(int alpha) throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1, 1, (alpha << 24) | 0xFFFFFF);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
    @SuppressWarnings("unchecked")
    private static ResourceManager manager(Map<ResourceLocation, byte[]> bytes) {
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(), new Class<?>[]{ResourceManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getResource")) return Optional.ofNullable(bytes.get(args[0]))
                            .map(data -> new Resource(null, () -> new ByteArrayInputStream(data)));
                    if (method.getName().equals("listResources")) {
                        Map<ResourceLocation, Resource> resources = new HashMap<>();
                        bytes.forEach((id, data) -> {
                            if (id.getPath().startsWith(args[0] + "/") && ((Predicate<ResourceLocation>)args[1]).test(id))
                                resources.put(id, new Resource(null, () -> new ByteArrayInputStream(data)));
                        });
                        return resources;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
    static class Sink implements VertexConsumer {
        final List<float[]> vertices = new ArrayList<>();
        float[] current;
        public VertexConsumer addVertex(float x,float y,float z) { current=new float[]{x,y,z,0,0,0,0,0}; vertices.add(current); return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) { return this; }
        public VertexConsumer setUv(float u,float v) { current[3]=u; current[4]=v; return this; }
        public VertexConsumer setUv1(int u,int v) { return this; }
        public VertexConsumer setUv2(int u,int v) { return this; }
        public VertexConsumer setNormal(float x,float y,float z) { current[5]=x; current[6]=y; current[7]=z; return this; }
    }
}
