package dev.equipmentstructure.api.appearance;

import com.google.gson.JsonParser;
import dev.equipmentstructure.api.client.appearance.BlockbenchModelDefinition;
import dev.equipmentstructure.api.client.appearance.BlockbenchModelLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockbenchModelLoaderTest {
    @Test
    void decodesJavaBlockElementsWithoutBindingToAnEquipmentName() {
        var json = JsonParser.parseString("""
                {"meta":{"model_format":"java_block"},"name":"blade",
                 "resolution":{"width":16,"height":16},
                 "elements":[{"name":"edge","from":[7,0,0],"to":[9,12,1]}]}
                """);
        var model = BlockbenchModelLoader.decode(id("models/equipment/blade.bbmodel"), json).orElseThrow();
        assertEquals("blade", model.name());
        assertEquals(id("models/equipment/blade.bbmodel"), model.modelResource().orElseThrow());
        assertEquals(1, model.elements().size());
        assertEquals(7, model.elements().getFirst().from().x());
        assertEquals(12, model.elements().getFirst().to().y());
        assertTrue(model.elements().getFirst().faces().isEmpty());
    }

    @Test
    void preservesTextureReferencesAndUvMetadataWithoutDecodingImageBytes() {
        var json = JsonParser.parseString("""
                {"meta":{"model_format":"java_block"},"resolution":{"width":16,"height":16},
                 "textures":[{"name":"blade","path":"textures/equipment/blade.png"}],
                 "elements":[{"from":[0,0,0],"to":[1,1,1],"faces":{"north":{"uv":[1,2,3,4],"texture":0}}}]}
                """);
        var model = BlockbenchModelLoader.decode(id("textured"), json).orElseThrow();
        assertEquals("textures/equipment/blade.png", model.textures().getFirst().path().orElseThrow());
        assertEquals(1F, model.elements().getFirst().faces().get("north").u0());
        assertEquals(4F, model.elements().getFirst().faces().get("north").v1());
    }

    @Test
    void decodesStandardMinecraftJavaExportWithNamedTextures() {
        var json = JsonParser.parseString("""
                {"texture_size":[32,32],
                 "textures":{"blade":"example:equipment/blade","particle":"#blade"},
                 "elements":[{"name":"blade_box","from":[6,1,7],"to":[10,15,9],
                 "faces":{"north":{"uv":[2,2,6,16],"texture":"#blade"}}}]}
                """);
        var model = BlockbenchModelLoader.decode(id("models/equipment/blade.json"), json).orElseThrow();
        assertEquals(32, model.textureWidth());
        assertEquals("example:equipment/blade", model.textures().getFirst().path().orElseThrow());
        assertEquals(0, model.elements().getFirst().faces().get("north").textureIndex());
        var face = model.elements().getFirst().faces().get("north");
        assertEquals(4F, face.u0());
        assertEquals(4F, face.v0());
        assertEquals(12F, face.u1());
        assertEquals(32F, face.v1());
    }

    @Test
    void javaAndProjectUvsSampleTheSameRectangleOnNonSquareTextures() {
        var javaJson = JsonParser.parseString("""
                {"texture_size":[64,32], "textures":{"t":"example:equipment/t"},
                 "elements":[{"from":[0,0,0],"to":[1,1,1],
                 "faces":{"north":{"uv":[2,4,14,12],"texture":"#t"}}}]}
                """);
        var projectJson = JsonParser.parseString("""
                {"meta":{"model_format":"java_block"},"resolution":{"width":64,"height":32},
                 "textures":[{"name":"t","path":"example:equipment/t"}],
                 "elements":[{"from":[0,0,0],"to":[1,1,1],
                 "faces":{"north":{"uv":[8,8,56,24],"texture":0}}}]}
                """);
        var javaFace = BlockbenchModelLoader.decode(id("model.json"), javaJson).orElseThrow()
                .elements().getFirst().faces().get("north");
        var projectFace = BlockbenchModelLoader.decode(id("model.bbmodel"), projectJson).orElseThrow()
                .elements().getFirst().faces().get("north");
        assertEquals(projectFace, javaFace);
    }

    @Test
    void rejectsUnsupportedFormatMalformedVectorsAndNonPositiveBoxes() {
        var unsupported = JsonParser.parseString("""
                {"meta":{"model_format":"bedrock"},"resolution":{"width":16,"height":16},"elements":[]}
                """);
        assertTrue(BlockbenchModelLoader.decode(id("unsupported"), unsupported).isEmpty());
        var malformed = JsonParser.parseString("""
                {"meta":{"model_format":"java_block"},"resolution":{"width":16,"height":16},"elements":[{"from":[0,0],"to":[1,1,1]}]}
                """);
        assertTrue(BlockbenchModelLoader.decode(id("malformed"), malformed).isEmpty());
        var inverted = JsonParser.parseString("""
                {"meta":{"model_format":"java_block"},"resolution":{"width":16,"height":16},"elements":[{"from":[1,0,0],"to":[0,1,1]}]}
                """);
        assertTrue(BlockbenchModelLoader.decode(id("inverted"), inverted).isEmpty());
    }

    @Test
    void acceptsNegativeCoordinatesWithinBoundedRange() {
        var json = JsonParser.parseString("""
                {"meta":{"model_format":"java_block"},"resolution":{"width":32,"height":32},"elements":[{"from":[-8,-2,-1],"to":[8,2,1]}]}
                """);
        assertEquals(1, BlockbenchModelLoader.decode(id("bounded"), json).orElseThrow().elements().size());
    }

    @Test
    void preservesElementRotationAroundItsBlockbenchOrigin() {
        var json = JsonParser.parseString("""
                {"meta":{"model_format":"java_block"},"resolution":{"width":16,"height":16},
                 "elements":[{"from":[7,0,0],"to":[9,4,1],
                 "rotation":{"origin":[8,8,0],"axis":"y","angle":22.5}}]}
                """);
        var rotation = BlockbenchModelLoader.decode(id("rotated"), json).orElseThrow()
                .elements().getFirst().rotation().orElseThrow();
        assertEquals("y", rotation.axis());
        assertEquals(22.5F, rotation.angle());
        assertEquals(8.0, rotation.origin().y());
    }

    @Test
    void resolvesRelativeAndQualifiedTexturePathsWithoutAllowingTraversal() {
        var modelResource = id("models/equipment/blade.bbmodel");
        var relative = new BlockbenchModelDefinition.Texture("blade",
                java.util.Optional.of("equipment/blade"), false);
        assertEquals(id("textures/equipment/blade.png"),
                BlockbenchModelLoader.resolveTexture(modelResource, relative).orElseThrow());

        var qualified = new BlockbenchModelDefinition.Texture("blade",
                java.util.Optional.of("othermod:custom/blade.png"), false);
        assertEquals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        "othermod", "textures/custom/blade.png"),
                BlockbenchModelLoader.resolveTexture(modelResource, qualified).orElseThrow());

        var traversal = new BlockbenchModelDefinition.Texture("bad",
                java.util.Optional.of("../secret.png"), false);
        assertTrue(BlockbenchModelLoader.resolveTexture(modelResource, traversal).isEmpty());
        var embedded = new BlockbenchModelDefinition.Texture("embedded", java.util.Optional.empty(), true);
        assertTrue(BlockbenchModelLoader.resolveTexture(modelResource, embedded).isEmpty());
    }

    @Test
    void treatsDecodedModelsWithMissingTexturesAsNotDrawable() {
        var json = JsonParser.parseString("""
                {"meta":{"model_format":"java_block"},"resolution":{"width":16,"height":16},
                 "textures":[{"name":"blade","path":"equipment/missing"}],
                 "elements":[{"from":[0,0,0],"to":[1,1,1],
                 "faces":{"north":{"uv":[0,0,1,1],"texture":0}}}]}
                """);
        var model = BlockbenchModelLoader.decode(id("missing-texture"), json).orElseThrow();
        var manager = net.minecraft.server.packs.resources.ResourceManager.Empty.INSTANCE;
        assertFalse(BlockbenchModelLoader.hasDrawableFace(manager, model));
    }

    private static net.minecraft.resources.ResourceLocation id(String path) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blockbench_loader_test", path);
    }
}
