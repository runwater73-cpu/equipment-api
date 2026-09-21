package dev.equipmentstructure.api.ui;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GridComponentDisplayTest {
    private static final ResourceLocation ID = ResourceLocation.parse("test:part");
    @AfterEach void clear() { GridComponentDisplayRegistry.clear(); }

    @Test void authoredMetadataRoundTripsIncludingColorAndDefaults() {
        var display = GridComponentDisplay.defaults().withColor(0x102ABC).withScale(.6).withRotation(true)
                .withBox(new GridComponentDisplay.Box(.5, 0, 1.5, 1))
                .withTexture(new GridComponentDisplay.Texture(ResourceLocation.parse("test:textures/grid/part.png"), 32, 16));
        var json = GridComponentDisplay.CODEC.encodeStart(JsonOps.INSTANCE, display).getOrThrow();
        assertEquals("#102ABC", json.getAsJsonObject().get("color").getAsString());
        assertEquals(display, GridComponentDisplay.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(GridComponentDisplay.defaults(), GridComponentDisplay.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow());
    }

    @Test void malformedFieldsAreRejectedInsteadOfSilentlyDefaulting() {
        for (String json : new String[]{"{\"color\":\"#123\"}", "{\"color\":\"#FF112233\"}", "{\"color\":123}",
                "{\"scale\":0}", "{\"scale\":2}", "{\"rotate_with_part\":123}",
                "{\"box\":{\"x\":-1,\"y\":0,\"width\":1,\"height\":1}}",
                "{\"texture\":{\"resource\":\"test:textures/../a.png\",\"width\":16,\"height\":16}}",
                "{\"texture\":{\"resource\":\"test:item/a\",\"width\":16,\"height\":16}}",
                "{\"texture\":{\"resource\":\"test:textures/a.png\",\"width\":0,\"height\":16}}"})
            assertTrue(GridComponentDisplay.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent(), json);
        assertThrows(IllegalArgumentException.class, () -> GridComponentDisplay.defaults().withColor(-1));
        assertThrows(IllegalArgumentException.class, () -> GridComponentDisplay.defaults().withScale(Double.NaN));
    }

    @Test void resourceEntriesOverrideWholeJavaEntryAndDeletionRestoresIt() {
        var java = GridComponentDisplay.defaults().withColor(0xAACCEE).withScale(.75);
        GridComponentDisplayRegistry.register(ID, java);
        GridComponentDisplayRegistry.register(ID, java);
        assertThrows(IllegalStateException.class, () -> GridComponentDisplayRegistry.register(ID, GridComponentDisplay.defaults()));
        GridComponentDisplayRegistry.replaceResources(Map.of(ID, GridComponentDisplay.defaults()));
        assertEquals(GridComponentDisplay.defaults(), GridComponentDisplayRegistry.get(ID));
        GridComponentDisplayRegistry.replaceResources(Map.of());
        assertEquals(java, GridComponentDisplayRegistry.get(ID));
    }

    @Test void automaticColorIsStableWithoutRegistrationAndExplicitBlackIsPreserved() {
        int color = GridComponentDisplayRegistry.get(ID).resolvedColor(ID);
        GridComponentDisplayRegistry.clear();
        assertEquals(color, GridComponentDisplayRegistry.get(ID).resolvedColor(ID));
        assertTrue(color >= 0 && color <= 0xFFFFFF);
        assertNotEquals(color, GridComponentDisplay.defaults().resolvedColor(ResourceLocation.parse("test:different")));
        assertEquals(0, GridComponentDisplay.defaults().withColor(0).resolvedColor(ID));
    }
}
