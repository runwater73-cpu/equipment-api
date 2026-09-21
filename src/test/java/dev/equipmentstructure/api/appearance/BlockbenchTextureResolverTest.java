package dev.equipmentstructure.api.appearance;

import dev.equipmentstructure.api.client.appearance.BlockbenchModelDefinition;
import dev.equipmentstructure.api.client.appearance.BlockbenchTextureResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockbenchTextureResolverTest {
    @AfterEach
    void clear() {
        BlockbenchTextureResolver.clear();
    }

    @Test
    void missingTextureIsAStableEmptyResult() {
        var manager = new net.minecraft.server.packs.resources.MultiPackResourceManager(
                net.minecraft.server.packs.PackType.CLIENT_RESOURCES, java.util.List.of());
        var model = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("texture_test", "models/blade.bbmodel");
        var texture = new BlockbenchModelDefinition.Texture("blade", Optional.of("blade"), false);
        assertTrue(BlockbenchTextureResolver.resolve(manager, model, texture, 1).isEmpty());
        assertTrue(BlockbenchTextureResolver.resolve(manager, model, texture, 1).isEmpty());
        manager.close();
    }
}
