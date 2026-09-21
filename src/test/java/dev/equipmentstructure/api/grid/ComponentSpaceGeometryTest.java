package dev.equipmentstructure.api.grid;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.grid.space.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ComponentSpaceGeometryTest {
    private static ResourceLocation id(String s) { return ResourceLocation.parse("test:" + s); }
    private static GridLayout board() { return GridLayout.empty(new GridBoard(GridShape.rectangle(6, 6), GridShape.rectangle(1, 1), new GridPlacement(5, 5))); }
    @Test void attachedRegionReservesEvenEmptyCellsAndMovesWithItsOwner() {
        var space = ComponentSpaceDefinition.attached(id("region"), GridShape.mask("##", "#."), new GridCell(1, 0));
        var layout = board().withComponent(id("owner"), GridFootprint.SINGLE_CELL, new GridPlacement(0, 0), List.of(space));
        assertEquals(31, layout.freeArea());
        assertFalse(layout.checkPlacement(id("other"), GridFootprint.SINGLE_CELL, new GridPlacement(1, 0)).allowed());
        assertTrue(layout.checkPlacement(id("other"), GridFootprint.SINGLE_CELL, new GridPlacement(2, 1)).allowed());
        var moved = layout.moved(id("owner"), new GridPlacement(2, 2));
        assertTrue(moved.checkPlacement(id("other"), GridFootprint.SINGLE_CELL, new GridPlacement(1, 0)).allowed());
        assertFalse(moved.checkPlacement(id("other"), GridFootprint.SINGLE_CELL, new GridPlacement(3, 2)).allowed());
    }
    @Test void signedOffsetsRotateAroundSourceWithoutNormalization() {
        var space = ComponentSpaceDefinition.attached(id("region"), GridShape.rectangle(1, 2), new GridCell(-1, 0));
        var shape = GridShape.rectangle(2, 1);
        assertEquals(new GridCell(4, 3), space.toBoard(new GridCell(0, 0), shape, new GridPlacement(4, 4, GridRotation.CLOCKWISE_90)));
        assertEquals(new GridCell(3, 3), space.toBoard(new GridCell(0, 1), shape, new GridPlacement(4, 4, GridRotation.CLOCKWISE_90)));
        var encoded = ComponentSpaceDefinition.CODEC.encodeStart(JsonOps.INSTANCE, space).getOrThrow();
        assertEquals(space, ComponentSpaceDefinition.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }
    @Test void overlappingSpacesSourceBodyAndBoardEdgesRejectWholePlacement() {
        var region = ComponentSpaceDefinition.attached(id("region"), GridShape.rectangle(1, 1), new GridCell(1, 0));
        assertFalse(board().checkPlacement(id("owner"), GridFootprint.SINGLE_CELL, new GridPlacement(5, 0), List.of(region)).allowed());
        assertFalse(board().checkPlacement(id("owner"), GridFootprint.SINGLE_CELL, new GridPlacement(4, 5), List.of(region)).allowed());
        assertEquals(GridLayout.Failure.SPACE_COLLISION, board().checkPlacement(id("owner"), GridFootprint.SINGLE_CELL, new GridPlacement(0, 0),
                List.of(region, ComponentSpaceDefinition.attached(id("other"), GridShape.rectangle(1, 1), new GridCell(1, 0)))).failure());
    }
    @Test void childPanelDoesNotReserveMainBoardAndFirstFitIncludesAttachedRegion() {
        var panel = ComponentSpaceDefinition.panel(id("panel"), GridShape.rectangle(4, 4));
        var layout = board().withComponent(id("owner"), GridFootprint.SINGLE_CELL, new GridPlacement(0, 0), List.of(panel));
        assertEquals(34, layout.freeArea());
        var attached = ComponentSpaceDefinition.attached(id("left"), GridShape.rectangle(1, 1), new GridCell(-1, 0));
        assertEquals(new GridPlacement(2, 0), GridTransactions.firstFit(layout, id("next"), GridFootprint.fixed(GridShape.rectangle(1, 1)), List.of(attached)).orElseThrow());
    }
    @Test void unknownItemDataSurvivesStorageRoundTripAndInputsAreDefensive() {
        var raw = new CompoundTag(); raw.putString("id", "missing:kept_item"); raw.putInt("count", 7);
        var data = new CompoundTag(); data.putString("custom", "kept");
        var original = new EquipmentComponentInstance(id("component"), id("type"), data);
        var entry = new ComponentSpaceContents.Entry(raw, GridFootprint.SINGLE_CELL, new GridPlacement(1, 0));
        var contents = new ComponentSpaceContents("", Map.of()).with(id("space"), new ComponentSpaceContents.Space(GridShape.rectangle(2, 1), List.of(entry)));
        raw.putInt("count", 99);
        var updated = contents.apply(original); var restored = ComponentSpaceContents.read(updated);
        assertEquals(contents, restored); assertEquals(7, restored.spaces().get(id("space")).entries().getFirst().item().getInt("count"));
        assertEquals("kept", updated.data().getString("custom")); assertTrue(ComponentSpaceContents.read(original).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ComponentSpaceContents("bad-token", Map.of()));
    }
    @Test void generationFingerprintIncludesSpacePoliciesAndRuleMetadataRoundTrips() {
        var space = ComponentSpaceDefinition.panel(id("panel"), GridShape.mask("##", "#.")).acceptsItems(id("a"), id("b"));
        var a = new GridDefinitions(Map.of(id("host"), board().board()), Map.of(id("part"), GridFootprint.SINGLE_CELL), Map.of(id("part"), List.of(space)));
        var copy = GridDefinitions.CODEC.parse(JsonOps.INSTANCE, GridDefinitions.CODEC.encodeStart(JsonOps.INSTANCE, a).getOrThrow()).getOrThrow();
        assertEquals(a, copy); assertEquals(a.fingerprint(), copy.fingerprint());
        assertNotEquals(a.fingerprint(), new GridDefinitions(a.hosts(), a.components(), Map.of(id("part"), List.of(space.withStackLimit(1)))).fingerprint());
    }
}
