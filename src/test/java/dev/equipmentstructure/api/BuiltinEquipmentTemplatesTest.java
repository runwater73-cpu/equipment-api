package dev.equipmentstructure.api;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import dev.equipmentstructure.api.grid.GridBoard;
import dev.equipmentstructure.api.grid.GridCell;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinEquipmentTemplatesTest {
    private static final List<ResourceLocation> EXPECTED_SLOTS = List.of(
            BuiltinEquipmentSlots.ARMOR_DECORATION,
            BuiltinEquipmentSlots.PENDANT,
            BuiltinEquipmentSlots.NAMEPLATE,
            BuiltinEquipmentSlots.GRIP_WRAP,
            BuiltinEquipmentSlots.GUARD,
            BuiltinEquipmentSlots.BLADE,
            BuiltinEquipmentSlots.FITTING,
            BuiltinEquipmentSlots.TOOL_MODULE,
            BuiltinEquipmentSlots.SOUL,
            BuiltinEquipmentSlots.WING,
            BuiltinEquipmentSlots.HEAD,
            BuiltinEquipmentSlots.CHEST,
            BuiltinEquipmentSlots.BACK,
            BuiltinEquipmentSlots.LEG,
            BuiltinEquipmentSlots.BOOT
    );

    @Test
    void publishesStableSlotOrder() {
        assertEquals(EXPECTED_SLOTS, BuiltinEquipmentSlots.all());
        assertEquals(15, BuiltinEquipmentSlots.all().size());
        assertEquals(BuiltinEquipmentSlots.all().size(), Set.copyOf(BuiltinEquipmentSlots.all()).size());
    }

    @Test
    void publishesOneSpecificBoardPerEquipmentTemplate() {
        assertEquals(21, BuiltinEquipmentTemplates.all().size());
        assertEquals(BuiltinEquipmentTemplates.all().size(), Set.copyOf(BuiltinEquipmentTemplates.all()).size());

        Set<List<GridCell>> bodyShapes = new HashSet<>();
        for (ResourceLocation templateId : BuiltinEquipmentTemplates.all()) {
            GridBoard board = BuiltinEquipmentTemplates.grid(templateId);
            assertNotNull(board);
            assertFalse(board.body().cells().isEmpty(), templateId.toString());
            assertTrue(board.bodyCells().stream().allMatch(board.area()::contains), templateId.toString());
            bodyShapes.add(board.body().cells());
        }

        // The public presets must express equipment-specific silhouettes, not one shared center marker.
        assertTrue(bodyShapes.size() > 1);
        assertEquals(GridBoard.DEFAULT_WIDTH, BuiltinEquipmentTemplates.defaultGrid().area().width());
        assertEquals(GridBoard.DEFAULT_HEIGHT, BuiltinEquipmentTemplates.defaultGrid().area().height());
        assertEquals(1, BuiltinEquipmentTemplates.defaultGrid().body().area());
    }

    @Test
    void replacementDefinitionRetainsEquipmentSpecificBoard() {
        ResourceLocation templateId = BuiltinEquipmentTemplates.SWORD;
        EquipmentHostDefinition definition = BuiltinEquipmentTemplates.definition(
                templateId, BuiltinEquipmentTypes.SWORD, List.of(BuiltinEquipmentSlots.BLADE));

        assertEquals(templateId, definition.id());
        assertEquals(BuiltinEquipmentTypes.SWORD, definition.equipmentType());
        assertEquals(BuiltinEquipmentTemplates.grid(templateId), definition.grid().orElseThrow());
        assertEquals(List.of(BuiltinEquipmentSlots.BLADE), definition.slots().stream()
                .map(EquipmentSlotDefinition::id).toList());
    }

    @Test
    void gridTemplateRoundTripsThroughHostCodec() {
        EquipmentHostDefinition original = BuiltinEquipmentTemplates.definition(
                BuiltinEquipmentTemplates.CHESTPLATE,
                BuiltinEquipmentTypes.CHESTPLATE,
                List.of(BuiltinEquipmentSlots.ARMOR_DECORATION, BuiltinEquipmentSlots.CHEST));

        var encoded = EquipmentHostDefinition.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        EquipmentHostDefinition decoded = EquipmentHostDefinition.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(original, decoded);
    }

    @Test
    void bundledJsonTemplatesMatchJavaBoards() {
        for (ResourceLocation templateId : BuiltinEquipmentTemplates.all()) {
            String path = "/data/" + templateId.getNamespace()
                    + "/equipment_structure_api/host_definition/" + templateId.getPath() + ".json";
            try (var stream = Objects.requireNonNull(
                    getClass().getResourceAsStream(path), path);
                 var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                EquipmentHostDefinition definition = EquipmentHostDefinition.CODEC
                        .parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
                assertEquals(templateId, definition.id());
                assertEquals(BuiltinEquipmentTemplates.grid(templateId), definition.grid().orElseThrow(), path);
                assertEquals(definition.slots().size(), Set.copyOf(definition.slots().stream()
                        .map(EquipmentSlotDefinition::id).toList()).size(), path);
            } catch (java.io.IOException exception) {
                throw new AssertionError("Could not read " + path, exception);
            }
        }
    }
}
