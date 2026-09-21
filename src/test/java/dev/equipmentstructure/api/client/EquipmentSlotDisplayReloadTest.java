package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.ui.EquipmentSlotDisplay;
import dev.equipmentstructure.api.ui.EquipmentStructureUiRegistry;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentSlotDisplayReloadTest {
    private final EquipmentSlotDisplayReloadListener listener = new EquipmentSlotDisplayReloadListener();
    private static final ResourceLocation HOST = ResourceLocation.parse("maker:armor/heavy");
    private static final ResourceLocation SLOT = ResourceLocation.parse("maker:left_core");

    @AfterEach void clear() { reload(Map.of()); EquipmentStructureUiRegistry.clear(); }

    @Test void nestedResourcePathIdentifiesHostAndReloadRemovesDeletedFiles() {
        reload(Map.of("armor/heavy", "{\"slots\":{\"maker:left_core\":{\"name\":\"maker.core\"}}}"));
        assertEquals("maker.core", EquipmentStructureUiRegistry.get(HOST).slot(SLOT).nameKey(SLOT));
        assertTrue(EquipmentSlotDisplayReloadListener.lastErrors().isEmpty());
        reload(Map.of());
        assertEquals(EquipmentSlotDisplay.defaults(), EquipmentStructureUiRegistry.get(HOST).slot(SLOT));
    }

    @Test void mixedValidAndBrokenFilesKeepEntirePreviousGenerationThenRecover() {
        reload(Map.of("armor/heavy", "{\"slots\":{\"maker:left_core\":{\"name\":\"old\"}}}"));
        reload(Map.of("armor/heavy", "{\"slots\":{\"maker:left_core\":{\"name\":\"new\"}}}", "broken", "{"));
        assertEquals("old", EquipmentStructureUiRegistry.get(HOST).slot(SLOT).nameKey(SLOT));
        assertFalse(EquipmentSlotDisplayReloadListener.lastErrors().isEmpty());
        reload(Map.of("armor/heavy", "{\"slots\":{\"maker:left_core\":{\"name\":\"new\"}}}"));
        assertEquals("new", EquipmentStructureUiRegistry.get(HOST).slot(SLOT).nameKey(SLOT));
        assertTrue(EquipmentSlotDisplayReloadListener.lastErrors().isEmpty());
    }

    @Test void invalidDisplayFieldRejectsTheWholeFileInsteadOfSilentlyClearingIt() {
        reload(Map.of("armor/heavy", "{\"slots\":{\"maker:left_core\":{\"name\":\"old\"}}}"));
        reload(Map.of("armor/heavy", "{\"slots\":{\"maker:left_core\":{\"description\":[\"\"]}}}"));
        assertEquals("old", EquipmentStructureUiRegistry.get(HOST).slot(SLOT).nameKey(SLOT));
        assertTrue(EquipmentSlotDisplayReloadListener.lastErrors().getFirst().contains("armor/heavy"));
    }

    private void reload(Map<String, String> files) {
        reload(files, Map.of());
    }

    @Test void interfaceAndHostDisplaysReloadTogetherAndBrokenTypeDoesNotPartiallyPublish() {
        var type = ResourceLocation.parse("maker:connector/test");
        var slots = List.of(EquipmentSlotDefinition.of(SLOT, type));
        reload(Map.of(), Map.of("connector/test", "{\"name\":\"type.old\"}"));
        assertEquals("type.old", EquipmentStructureUiRegistry.get(HOST, slots).slot(SLOT).nameKey(SLOT));
        reload(Map.of("armor/heavy", "{\"slots\":{\"maker:left_core\":{\"name\":\"host.new\"}}}"),
                Map.of("connector/test", "{\"empty_icon\":\"INVALID\"}"));
        assertEquals("type.old", EquipmentStructureUiRegistry.get(HOST, slots).slot(SLOT).nameKey(SLOT));
        assertTrue(EquipmentSlotDisplayReloadListener.lastErrors().getFirst().contains("interface_display/connector/test"));
        reload(Map.of(), Map.of("connector/test", "{\"name\":\"type.new\"}"));
        assertEquals("type.new", EquipmentStructureUiRegistry.get(HOST, slots).slot(SLOT).nameKey(SLOT));
        reload(Map.of());
        assertEquals(EquipmentSlotDisplay.defaults(), EquipmentStructureUiRegistry.get(HOST, slots).slot(SLOT));
    }

    private void reload(Map<String, String> files, Map<String, String> types) {
        var resources = new LinkedHashMap<ResourceLocation, Resource>();
        files.forEach((path, json) -> resources.put(ResourceLocation.parse("maker:" + EquipmentSlotDisplayReloadListener.DIRECTORY + "/" + path + ".json"),
                new Resource(null, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))));
        types.forEach((path, json) -> resources.put(ResourceLocation.parse("maker:" + EquipmentSlotDisplayReloadListener.INTERFACE_DIRECTORY + "/" + path + ".json"),
                new Resource(null, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))));
        var manager = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(), new Class[]{ResourceManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("listResources")) {
                        var matches = new LinkedHashMap<ResourceLocation, Resource>();
                        @SuppressWarnings("unchecked") var filter = (Predicate<ResourceLocation>) args[1];
                        resources.forEach((id, resource) -> {
                            if (id.getPath().startsWith(args[0] + "/") && filter.test(id)) matches.put(id, resource);
                        });
                        return matches;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        listener.apply(listener.prepare(manager, InactiveProfiler.INSTANCE), manager, InactiveProfiler.INSTANCE);
    }
}
