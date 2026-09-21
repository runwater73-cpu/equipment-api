package dev.equipmentstructure.api.appearance;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructure;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static dev.equipmentstructure.api.appearance.AppearanceDefinitions.ANCHOR_ONLY;
import static org.junit.jupiter.api.Assertions.*;

class AppearanceResourceCodecTest {
    private static final ResourceLocation HOST = id("host_visual");
    private static final ResourceLocation SLOT = id("blade_slot");
    private static final ResourceLocation PORT = id("upper_mount");
    private static final ResourceLocation PART = id("curved_component");
    private static final ResourceLocation ASSET = id("curved_asset");
    private static final ResourceLocation ELEMENT = id("original_blade");

    @Test void hostResourceRoundTripsWithStableCollections() {
        JsonObject source = hostJson();
        var decoded = AppearanceResourceDecoder.decodeHost(HOST, source).getOrThrow();
        assertEquals(HOST, decoded.id());
        assertEquals(new AppearanceTransform(new AppearanceVector(0, 7, 0), AppearanceRotation.IDENTITY),
                decoded.ports().get(PORT).localFrame());
        assertEquals(Set.of(ELEMENT), decoded.replaceableElements());
        var encoded = AppearanceResourceCodec.HOST.encodeStart(JsonOps.INSTANCE,
                new AppearanceResourceCodec.HostResource(3, HOST, decoded.ports(), decoded.bindings(), decoded.replaceableElements())).getOrThrow();
        assertEquals(decoded, AppearanceResourceDecoder.decodeHost(HOST, encoded).getOrThrow());
    }

    @Test void componentResourceRoundTripsAndConvertsToPartDefinition() {
        var decoded = AppearanceResourceDecoder.decodeComponent(PART, componentJson()).getOrThrow();
        assertEquals(PART, decoded.id());
        assertEquals(ASSET, decoded.asset());
        assertEquals(AppearanceTransform.IDENTITY, decoded.mount());
        var encoded = AppearanceResourceCodec.COMPONENT.encodeStart(JsonOps.INSTANCE,
                new AppearanceResourceCodec.ComponentResource(3, PART, ASSET, decoded.mount(), decoded.requiredCapabilities())).getOrThrow();
        assertEquals(decoded, AppearanceResourceDecoder.decodeComponent(PART, encoded).getOrThrow());
    }

    @Test void componentRenderProfileRoundTripsAndIsCopiedToPartDefinition() {
        var source = componentJson();
        var render = new JsonObject();
        render.addProperty("mode", "surface");
        render.addProperty("layer", "surface");
        render.addProperty("order", 17);
        render.addProperty("depth_bias", 0.125F);
        source.add("render", render);

        var decoded = AppearanceResourceDecoder.decodeComponent(PART, source).getOrThrow();
        var profile = decoded.renderProfile();
        assertEquals(AppearanceDefinitions.RenderMode.SURFACE, profile.mode());
        assertEquals(AppearanceDefinitions.RenderLayer.SURFACE, profile.layer());
        assertEquals(17, profile.order());
        assertEquals(0.125F, profile.depthBias());

        var encoded = AppearanceResourceCodec.COMPONENT.encodeStart(JsonOps.INSTANCE,
                new AppearanceResourceCodec.ComponentResource(3, decoded.id(), decoded.asset(), decoded.mount(),
                        decoded.requiredCapabilities(), decoded.renderProfile())).getOrThrow();
        var roundTrip = AppearanceResourceDecoder.decodeComponent(PART, encoded).getOrThrow();
        assertEquals(profile, roundTrip.renderProfile());
    }

    @Test void omittedRenderProfileUsesGeometryStructureDefaults() {
        var decoded = AppearanceResourceDecoder.decodeComponent(PART, componentJson()).getOrThrow();
        assertEquals(AppearanceDefinitions.RenderProfile.DEFAULT, decoded.renderProfile());
    }

    @Test void invalidRenderProfileValuesAreRejected() {
        var unknownMode = componentJson();
        var render = new JsonObject();
        render.addProperty("mode", "unknown");
        unknownMode.add("render", render);
        assertTrue(AppearanceResourceDecoder.decodeComponent(PART, unknownMode).error().isPresent());

        var outOfRangeOrder = componentJson();
        var order = new JsonObject();
        order.addProperty("order", 1025);
        outOfRangeOrder.add("render", order);
        assertTrue(AppearanceResourceDecoder.decodeComponent(PART, outOfRangeOrder).error().isPresent());

        var invalidBias = componentJson();
        var bias = new JsonObject();
        bias.addProperty("depth_bias", 17.0F);
        invalidBias.add("render", bias);
        assertTrue(AppearanceResourceDecoder.decodeComponent(PART, invalidBias).error().isPresent());
    }

    @Test void pathAndPayloadIDsMustMatch() {
        assertTrue(AppearanceResourceDecoder.decodeHost(id("renamed"), hostJson()).error().isPresent());
        assertTrue(AppearanceResourceDecoder.decodeComponent(id("renamed"), componentJson()).error().isPresent());
    }

    @Test void unsupportedVersionsAndMalformedVectorsAreDataErrors() {
        var version = hostJson(); version.addProperty("format_version", 4);
        assertTrue(AppearanceResourceDecoder.decodeHost(HOST, version).error().isPresent());
        var position = hostJson();
        position.getAsJsonObject("ports").getAsJsonObject(PORT.toString()).getAsJsonObject("frame").getAsJsonArray("position").remove(2);
        assertTrue(AppearanceResourceDecoder.decodeHost(HOST, position).error().isPresent());
        var rotation = hostJson();
        JsonArray zero = rotation.getAsJsonObject("ports").getAsJsonObject(PORT.toString()).getAsJsonObject("frame").getAsJsonArray("rotation");
        for (int i = 0; i < zero.size(); i++) zero.set(i, new JsonPrimitive(0));
        assertTrue(AppearanceResourceDecoder.decodeHost(HOST, rotation).error().isPresent());
    }

    @Test void duplicateIDsAreRejectedInsteadOfSilentlyBecomingASet() {
        var duplicate = hostJson(); duplicate.getAsJsonArray("replaceable_elements").add(ELEMENT.toString());
        assertTrue(AppearanceResourceDecoder.decodeHost(HOST, duplicate).error().isPresent());
        var caps = componentJson(); caps.getAsJsonArray("required_capabilities").add(ASSET.toString()); caps.getAsJsonArray("required_capabilities").add(ASSET.toString());
        assertTrue(AppearanceResourceDecoder.decodeComponent(PART, caps).error().isPresent());
    }

    @Test void catalogDecodeIsAtomicAndAggregatesErrors() {
        var broken = hostJson(); broken.addProperty("id", id("wrong_payload").toString());
        var result = AppearanceResourceDecoder.decodeCatalog(4, Map.of(HOST, broken), Map.of(PART, componentJson()));
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("host " + HOST));
        assertTrue(AppearanceResourceDecoder.decodeCatalog(-1, Map.of(), Map.of()).error().isPresent());
    }

    @Test void validCatalogDrivesExistingResolverWithoutClientTypes() {
        var catalog = AppearanceResourceDecoder.decodeCatalog(3, Map.of(HOST, hostJson()), Map.of(PART, componentJson())).getOrThrow();
        var structure = new EquipmentStructure(HOST, HOST, java.util.List.of(EquipmentSlotDefinition.of(SLOT, PART)),
                Map.of(SLOT, java.util.List.of(new EquipmentComponentInstance(PART, PART))));
        var plan = AppearanceResolver.resolve(structure, catalog, new AppearanceSupport(3, Set.of(ASSET), Set.of(), true));
        assertEquals(AppearancePlan.Status.READY, plan.outcomes().getFirst().status());
        assertEquals(Set.of(ELEMENT), plan.hiddenOriginalElements());
    }

    @Test void placementFreedomDefaultsToAdjustableAndDecodesFixed() {
        var legacy = AppearanceResourceDecoder.decodeHost(HOST, hostJson()).getOrThrow();
        assertEquals(AppearanceDefinitions.PlacementFreedom.ADJUSTABLE,
                legacy.bindings().get(SLOT).placementFreedom());
        var fixedJson = hostJson();
        fixedJson.getAsJsonObject("bindings").getAsJsonObject(SLOT.toString())
                .addProperty("placement", "fixed");
        var fixed = AppearanceResourceDecoder.decodeHost(HOST, fixedJson).getOrThrow();
        assertEquals(AppearanceDefinitions.PlacementFreedom.FIXED,
                fixed.bindings().get(SLOT).placementFreedom());
        var encoded = AppearanceResourceCodec.HOST.encodeStart(JsonOps.INSTANCE,
                new AppearanceResourceCodec.HostResource(3, HOST, fixed.ports(), fixed.bindings(), fixed.replaceableElements())).getOrThrow();
        assertEquals("fixed", encoded.getAsJsonObject().getAsJsonObject("bindings").getAsJsonObject(SLOT.toString())
                .get("placement").getAsString());
    }

    @Test void omittedFrameAndJointOffsetUseIdentityDefaults() {
        var root = hostJson();
        root.getAsJsonObject("ports").getAsJsonObject(PORT.toString()).remove("frame");
        root.getAsJsonObject("bindings").getAsJsonObject(SLOT.toString())
                .getAsJsonObject("joint").remove("offset");
        var decoded = AppearanceResourceDecoder.decodeHost(HOST, root).getOrThrow();
        assertEquals(AppearanceTransform.IDENTITY,
                decoded.ports().get(PORT).localFrame());
        assertEquals(AppearanceTransform.IDENTITY,
                decoded.bindings().get(SLOT).joint().offset());
    }

    @Test void connectionPointAndAnimationFollowAreOptional() {
        var root = hostJson();
        var binding = root.getAsJsonObject("bindings").getAsJsonObject(SLOT.toString());
        binding.remove("port");
        binding.addProperty("follow_host_animation", false);
        var catalog = AppearanceResourceDecoder.decodeCatalog(3, Map.of(HOST, root), Map.of(PART, componentJson())).getOrThrow();
        var structure = new EquipmentStructure(HOST, HOST, java.util.List.of(EquipmentSlotDefinition.of(SLOT, PART)),
                Map.of(SLOT, java.util.List.of(new EquipmentComponentInstance(PART, PART))));
        var plan = AppearanceResolver.resolve(structure, catalog, new AppearanceSupport(3, Set.of(ASSET), Set.of(), true));
        assertEquals(AppearancePlan.Status.READY, plan.outcomes().getFirst().status());
        assertFalse(plan.placements().getFirst().followsHostAnimation());
        assertEquals(AppearanceTransform.IDENTITY, plan.placements().getFirst().transform());

        var detached = hostJson();
        var detachedBinding = detached.getAsJsonObject("bindings").getAsJsonObject(SLOT.toString());
        detachedBinding.remove("port");
        detachedBinding.addProperty("follow_host_animation", false);
        var detachedCatalog = AppearanceResourceDecoder.decodeCatalog(4, Map.of(HOST, detached), Map.of(PART, componentJson())).getOrThrow();
        var detachedPlan = AppearanceResolver.resolve(structure, detachedCatalog,
                new AppearanceSupport(4, Set.of(ASSET), Set.of(), true));
        assertEquals(AppearanceTransform.IDENTITY, detachedPlan.placements().getFirst().transform(),
                "an explicitly detached part must not inherit its host port frame");
    }

    @Test void optionalBoundsRoundTripAndClampEditorPose() {
        var root = hostJson();
        var binding = root.getAsJsonObject("bindings").getAsJsonObject(SLOT.toString());
        var bounds = new JsonObject();
        bounds.add("min", vectorJson(-1, -2, -3));
        bounds.add("max", vectorJson(1, 2, 3));
        bounds.addProperty("min_scale", 0.5D);
        bounds.addProperty("max_scale", 1.5D);
        binding.add("bounds", bounds);
        var decoded = AppearanceResourceDecoder.decodeHost(HOST, root).getOrThrow();
        var limits = decoded.bindings().get(SLOT).bounds().orElseThrow();
        assertEquals(new AppearanceVector(-1, -2, -3), limits.min());
        assertEquals(0.5D, limits.minScale());
        var clamped = limits.clamp(new AppearancePose(9, -9, 4, AppearanceRotation.IDENTITY, 1.9D));
        assertEquals(new AppearanceVector(1, -2, 3), clamped.transform().position());
        assertEquals(1.5D, clamped.scale());
    }

    @Test void componentMountDefaultsToIdentityAndInvalidBoundsAreDataErrors() {
        var component = componentJson();
        component.remove("mount");
        var decoded = AppearanceResourceDecoder.decodeComponent(PART, component).getOrThrow();
        assertEquals(AppearanceTransform.IDENTITY, decoded.mount());

        var invalid = hostJson();
        var binding = invalid.getAsJsonObject("bindings").getAsJsonObject(SLOT.toString());
        var bounds = new JsonObject();
        bounds.add("min", vectorJson(2, 0, 0));
        bounds.add("max", vectorJson(-2, 0, 0));
        bounds.addProperty("min_scale", 1.5D);
        bounds.addProperty("max_scale", 0.5D);
        binding.add("bounds", bounds);
        assertDoesNotThrow(() -> AppearanceResourceDecoder.decodeHost(HOST, invalid));
        assertTrue(AppearanceResourceDecoder.decodeHost(HOST, invalid).error().isPresent());
    }

    private static JsonObject hostJson() {
        var root = new JsonObject(); root.addProperty("format_version", 3); root.addProperty("id", HOST.toString());
        var ports = new JsonObject(); var port = new JsonObject(); port.add("frame", transformJson(0, 7, 0)); ports.add(PORT.toString(), port); root.add("ports", ports);
        var binding = new JsonObject(); binding.addProperty("port", PORT.toString());
        var joint = new JsonObject(); joint.addProperty("owner", id("seam_owner").toString()); joint.addProperty("strategy", ANCHOR_ONLY.toString()); joint.add("offset", transformJson(0, 0, 0));
        binding.add("joint", joint); binding.add("replaces", array(ELEMENT));
        var bindings = new JsonObject(); bindings.add(SLOT.toString(), binding); root.add("bindings", bindings); root.add("replaceable_elements", array(ELEMENT));
        return root;
    }

    private static JsonObject componentJson() {
        var root = new JsonObject(); root.addProperty("format_version", 3); root.addProperty("id", PART.toString()); root.addProperty("asset", ASSET.toString());
        root.add("mount", transformJson(0, 0, 0)); root.add("required_capabilities", new JsonArray()); return root;
    }

    private static JsonObject transformJson(double x, double y, double z) {
        var transform = new JsonObject(); var position = new JsonArray(); position.add(x); position.add(y); position.add(z); transform.add("position", position);
        var rotation = new JsonArray(); rotation.add(0); rotation.add(0); rotation.add(0); rotation.add(1); transform.add("rotation", rotation); return transform;
    }

    private static JsonArray vectorJson(double x, double y, double z) {
        var vector = new JsonArray(); vector.add(x); vector.add(y); vector.add(z); return vector;
    }

    private static JsonArray array(ResourceLocation value) { var array = new JsonArray(); array.add(value.toString()); return array; }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("appearance_codec_test", path); }
}
