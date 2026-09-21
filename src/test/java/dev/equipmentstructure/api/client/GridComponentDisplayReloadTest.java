package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.ui.GridComponentDisplay;
import dev.equipmentstructure.api.ui.GridComponentDisplayRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

class GridComponentDisplayReloadTest {
    private static final ResourceLocation ID = ResourceLocation.parse("maker:tools/part");
    private final GridComponentDisplayReloadListener listener = new GridComponentDisplayReloadListener();
    @AfterEach void clear() { reload(Map.of(), Map.of()); GridComponentDisplayRegistry.clear(); }

    @Test void nestedIdsReloadAndDeletedResourcesReturnToJavaDefault() {
        var defaults = GridComponentDisplay.defaults().withColor(0xABCDEF);
        GridComponentDisplayRegistry.register(ID, defaults);
        reload(Map.of("tools/part", "{\"color\":\"#123456\"}"), Map.of());
        assertEquals(0x123456, GridComponentDisplayRegistry.get(ID).resolvedColor(ID));
        reload(Map.of(), Map.of());
        assertEquals(defaults, GridComponentDisplayRegistry.get(ID));
    }

    @Test void malformedBatchRetainsAllPreviousEntriesAndInvalidatesTextureCachesThenRecovers() {
        reload(Map.of("tools/part", "{\"color\":\"#123456\"}"), Map.of());
        long generation = GridComponentDisplayReloadListener.generation();
        reload(Map.of("tools/part", "{\"color\":\"#FFFFFF\"}", "broken", "{"), Map.of());
        assertEquals(0x123456, GridComponentDisplayRegistry.get(ID).resolvedColor(ID));
        assertTrue(GridComponentDisplayReloadListener.generation() > generation);
        assertFalse(GridComponentDisplayReloadListener.lastErrors().isEmpty());
        reload(Map.of("tools/part", "{}"), Map.of());
        assertEquals(GridComponentDisplay.defaults(), GridComponentDisplayRegistry.get(ID));
        assertTrue(GridComponentDisplayReloadListener.lastErrors().isEmpty());
    }

    @Test void customTextureMustExistAndDeclareItsActualPngDimensions() throws Exception {
        String json = "{\"texture\":{\"resource\":\"maker:textures/part.png\",\"width\":32,\"height\":16}}";
        reload(Map.of("tools/part", json), Map.of());
        assertFalse(GridComponentDisplayReloadListener.lastErrors().isEmpty());
        reload(Map.of("tools/part", json), Map.of("maker:textures/part.png", png(16, 16)));
        assertFalse(GridComponentDisplayReloadListener.lastErrors().isEmpty());
        reload(Map.of("tools/part", json), Map.of("maker:textures/part.png", new byte[]{1, 2, 3}));
        assertFalse(GridComponentDisplayReloadListener.lastErrors().isEmpty());
        reload(Map.of("tools/part", json), Map.of("maker:textures/part.png", png(32, 16)));
        assertTrue(GridComponentDisplayReloadListener.lastErrors().isEmpty());
        assertEquals(32, GridComponentDisplayRegistry.get(ID).texture().orElseThrow().width());
    }

    private static byte[] png(int width, int height) throws Exception {
        var output = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", output);
        return output.toByteArray();
    }
    private void reload(Map<String, String> jsonFiles, Map<String, byte[]> images) {
        var resources = new LinkedHashMap<ResourceLocation, Resource>();
        jsonFiles.forEach((path, json) -> resources.put(ResourceLocation.parse("maker:" + GridComponentDisplayReloadListener.DIRECTORY + "/" + path + ".json"),
                new Resource(null, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))));
        images.forEach((path, bytes) -> resources.put(ResourceLocation.parse(path), new Resource(null, () -> new ByteArrayInputStream(bytes))));
        var manager = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(), new Class[]{ResourceManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getResource")) return Optional.ofNullable(resources.get(args[0]));
                    if (method.getName().equals("listResources")) {
                        var matches = new LinkedHashMap<ResourceLocation, Resource>();
                        @SuppressWarnings("unchecked") var filter = (Predicate<ResourceLocation>) args[1];
                        resources.forEach((id, resource) -> { if (id.getPath().startsWith(args[0] + "/") && filter.test(id)) matches.put(id, resource); });
                        return matches;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        listener.apply(listener.prepare(manager, InactiveProfiler.INSTANCE), manager, InactiveProfiler.INSTANCE);
    }
}
