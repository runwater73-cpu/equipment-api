package dev.equipmentstructure.api.grid;

import com.mojang.serialization.JsonOps;
import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.appearance.AppearancePose;
import dev.equipmentstructure.api.appearance.AppearancePoseStorage;
import dev.equipmentstructure.api.appearance.AppearanceRotation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static dev.equipmentstructure.api.grid.GridTransactionsTest.*;
import static org.junit.jupiter.api.Assertions.*;

class GridPersistenceTest {
    @Test void savedStructureRoundTripKeepsGridAndIndependentAppearance() {
        var part = AppearancePoseStorage.with(part(PART), new AppearancePose(1, 2, 3, AppearanceRotation.IDENTITY, 1.5));
        var original = GridTransactions.install(empty(), A, part, Optional.of(new GridPlacement(0, 0)), definitions(true)).structure();
        // Actual saves use NBT; JsonOps intentionally narrows numeric NBT tags on conversion.
        var encoded = EquipmentStructure.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, original).getOrThrow();
        var decoded = EquipmentStructure.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(original, decoded);
        var moved = GridTransactions.move(decoded, Map.of(A, new GridPlacement(1, 0)), definitions(true)).structure();
        assertEquals(part.data(), moved.component(A).orElseThrow().data());
        var edited = moved.withComponent(A, AppearancePoseStorage.with(part, AppearancePose.IDENTITY));
        assertEquals(moved.grid(), edited.grid());
        assertEquals(moved.grid(), moved.addSlot(dev.equipmentstructure.api.EquipmentSlotDefinition.of(id("extra"), TYPE)).grid());
    }

    @Test void oldSavesDecodeWithoutRuntimeRegistrationsOrGridData() {
        var old = empty().withComponent(A, part(PART));
        var json = EquipmentStructure.CODEC.encodeStart(JsonOps.INSTANCE, old).getOrThrow().getAsJsonObject();
        assertFalse(json.has("grid"));
        assertEquals(old, EquipmentStructure.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        var saved = install(empty(), A, PART, null, definitions(true));
        var encoded = EquipmentStructure.CODEC.encodeStart(JsonOps.INSTANCE, saved).getOrThrow();
        // Decoding saved positions never consults the current registry, even if the mod is absent.
        assertEquals(saved, EquipmentStructure.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test void codecsRejectMalformedVersionsRotationsOriginsAndBodyBounds() {
        var saved = install(empty(), A, PART, null, definitions(true));
        var json = GridState.CODEC.encodeStart(JsonOps.INSTANCE, saved.grid().orElseThrow()).getOrThrow().getAsJsonObject();
        var future = json.deepCopy(); future.addProperty("format", 2);
        assertTrue(GridState.CODEC.parse(JsonOps.INSTANCE, future).error().isPresent());
        var negative = json.deepCopy(); negative.addProperty("revision", -1);
        assertTrue(GridState.CODEC.parse(JsonOps.INSTANCE, negative).error().isPresent());
        var rotation = json.deepCopy(); rotation.getAsJsonObject("placements").getAsJsonObject(A.toString()).addProperty("rotation", 4);
        assertTrue(GridState.CODEC.parse(JsonOps.INSTANCE, rotation).error().isPresent());
        var origin = json.deepCopy(); origin.getAsJsonObject("placements").getAsJsonObject(A.toString()).addProperty("x", Integer.MAX_VALUE);
        assertTrue(GridState.CODEC.parse(JsonOps.INSTANCE, origin).error().isPresent());
        var board = GridCodecs.BOARD.encodeStart(JsonOps.INSTANCE, definitions(true).hosts().get(HOST)).getOrThrow().getAsJsonObject();
        board.getAsJsonObject("body_placement").addProperty("x", 20);
        assertTrue(GridCodecs.BOARD.parse(JsonOps.INSTANCE, board).error().isPresent());
        var structure = EquipmentStructure.CODEC.encodeStart(JsonOps.INSTANCE, saved).getOrThrow().getAsJsonObject();
        structure.getAsJsonObject("grid").addProperty("format", 2);
        assertTrue(EquipmentStructure.CODEC.parse(JsonOps.INSTANCE, structure).error().isPresent());
    }

    @Test void definitionsAreDeterministicAndRoundTripWithoutRegistrationSideEffects() {
        var defs = definitions(false);
        var json = GridDefinitions.CODEC.encodeStart(JsonOps.INSTANCE, defs).getOrThrow();
        var decoded = GridDefinitions.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(defs, decoded);
        assertEquals(defs.fingerprint(), decoded.fingerprint());
        assertFalse(decoded.hosts().get(HOST).bodyOccupiesCells());
        assertThrows(UnsupportedOperationException.class, () -> decoded.hosts().clear());
        assertTrue(EquipmentGridRegistry.host(HOST).isEmpty());
    }

    @Test void duplicateCellsAndDirectionsBecomeDecodeErrorsNotThrownExceptions() {
        var json = GridCodecs.FOOTPRINT.encodeStart(JsonOps.INSTANCE, GridFootprint.SINGLE_CELL).getOrThrow().getAsJsonObject();
        json.getAsJsonArray("shape").add(json.getAsJsonArray("shape").get(0).deepCopy());
        assertTrue(GridCodecs.FOOTPRINT.parse(JsonOps.INSTANCE, json).error().isPresent());
        var rotations = GridCodecs.FOOTPRINT.encodeStart(JsonOps.INSTANCE, GridFootprint.fixed(GridShape.rectangle(1, 1)))
                .getOrThrow().getAsJsonObject();
        rotations.getAsJsonArray("rotations").add(0);
        assertTrue(GridCodecs.FOOTPRINT.parse(JsonOps.INSTANCE, rotations).error().isPresent());
    }
}
