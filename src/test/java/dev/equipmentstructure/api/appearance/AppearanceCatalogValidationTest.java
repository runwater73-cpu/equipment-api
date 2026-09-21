package dev.equipmentstructure.api.appearance;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppearanceCatalogValidationTest {
    private static final ResourceLocation KNOWN = id("known");
    private static final ResourceLocation UNKNOWN = id("unknown");

    @Test
    void reportsOnlyUnregisteredAppearanceComponentIdsInStableOrder() {
        var catalog = new AppearanceCatalog(1, Map.of(), Map.of(
                UNKNOWN, part(UNKNOWN), KNOWN, part(KNOWN)));

        assertEquals(java.util.List.of(UNKNOWN),
                AppearanceCatalogValidation.unknownComponentIds(catalog, KNOWN::equals));
    }

    private static AppearanceDefinitions.Part part(ResourceLocation id) {
        return new AppearanceDefinitions.Part(id, id("appearance/" + id.getPath()),
                AppearanceTransform.IDENTITY, Set.of());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
