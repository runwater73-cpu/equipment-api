package dev.equipmentstructure.api.appearance;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class AppearanceConnectionCodecTest {
    @Test void realV2FixtureRoundTripsAndV1ComponentRemainsValid() {
        var host = AppearanceResourceCodec.HOST.parse(JsonOps.INSTANCE, json("host/chain")).getOrThrow();
        assertEquals(id("root"), host.ports().get(id("next")).componentExit().orElseThrow().slot());
        assertEquals(host, AppearanceResourceCodec.HOST.parse(JsonOps.INSTANCE,
                AppearanceResourceCodec.HOST.encodeStart(JsonOps.INSTANCE, host).getOrThrow()).getOrThrow());
        for (String name : new String[]{"short", "long", "end"}) {
            var part = AppearanceResourceCodec.COMPONENT.parse(JsonOps.INSTANCE, json("component/" + name)).getOrThrow();
            assertEquals(part, AppearanceResourceCodec.COMPONENT.parse(JsonOps.INSTANCE,
                    AppearanceResourceCodec.COMPONENT.encodeStart(JsonOps.INSTANCE, part).getOrThrow()).getOrThrow());
            assertEquals(name.equals("end") ? 0 : 1, part.toDefinition().exits().size());
        }
    }

    @Test void refusesDynamicFeaturesInVersionOneOnDecodeAndEncode() {
        var hostJson = json("host/chain");
        hostJson.addProperty("format_version", 1);
        assertTrue(AppearanceResourceDecoder.decodeHost(id("chain"), hostJson).error().isPresent());
        var partJson = json("component/short");
        partJson.addProperty("format_version", 1);
        assertTrue(AppearanceResourceDecoder.decodeComponent(id("short"), partJson).error().isPresent());
        var host = AppearanceResourceCodec.HOST.parse(JsonOps.INSTANCE, json("host/chain")).getOrThrow();
        assertTrue(AppearanceResourceCodec.HOST.encodeStart(JsonOps.INSTANCE, new AppearanceResourceCodec.HostResource(
                1, host.id(), host.ports(), host.bindings(), host.replaceableElements())).error().isPresent());
        var part = AppearanceResourceCodec.COMPONENT.parse(JsonOps.INSTANCE, json("component/short")).getOrThrow();
        assertTrue(AppearanceResourceCodec.COMPONENT.encodeStart(JsonOps.INSTANCE, new AppearanceResourceCodec.ComponentResource(
                1, part.id(), part.asset(), part.mount(), part.requiredCapabilities(), part.renderProfile(), part.exits())).error().isPresent());
    }

    @Test void ambiguousOrIncompletePortReferencesAreDataErrors() {
        var host = json("host/chain");
        var port = host.getAsJsonObject("ports").getAsJsonObject("esa_connection:next");
        port.addProperty("parent", "esa_connection:base");
        assertTrue(AppearanceResourceDecoder.decodeHost(id("chain"), host).error().isPresent());
        port.remove("parent");
        port.getAsJsonObject("component_exit").remove("exit");
        assertTrue(AppearanceResourceDecoder.decodeHost(id("chain"), host).error().isPresent());
    }

    @Test void malformedExitFramesRejectWholeCatalog() {
        var part = json("component/short");
        part.getAsJsonObject("exits").getAsJsonObject("esa_connection:tip").getAsJsonArray("position").remove(0);
        assertTrue(AppearanceResourceDecoder.decodeCatalog(1, Map.of(id("chain"), json("host/chain")),
                Map.of(id("short"), part, id("end"), json("component/end"))).error().isPresent());
    }

    @Test void omittedExitsKeepStaticDefaults() {
        var end = AppearanceResourceCodec.COMPONENT.parse(JsonOps.INSTANCE, json("component/end")).getOrThrow();
        assertTrue(end.exits().isEmpty());
        assertEquals(end, new AppearanceResourceCodec.ComponentResource(3, end.id(), end.asset(), end.mount(), end.requiredCapabilities()));
        var port = new AppearanceDefinitions.Port(AppearanceTransform.IDENTITY);
        assertTrue(port.componentExit().isEmpty());
        assertEquals(port, new AppearanceDefinitions.Port(java.util.Optional.empty(), AppearanceTransform.IDENTITY));
    }

    private static JsonObject json(String path) {
        try (var reader = new InputStreamReader(Objects.requireNonNull(AppearanceConnectionCodecTest.class.getResourceAsStream(
                "/assets/esa_connection/equipment_structure_api/appearance/" + path + ".json")), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("esa_connection", path); }
}
