package dev.equipmentstructure.api.appearance;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppearancePoseStorageTest {
    @Test void roundTripPreservesOtherDataAndDoesNotMutateOriginal() {
        var id = ResourceLocation.parse("test:part");
        var tag = new CompoundTag(); tag.putString("author:data", "kept");
        var part = new EquipmentComponentInstance(id, id, id, tag);
        var pose = new AppearancePose(3, -4, 2, new AppearanceRotation(0, 0, 1, 1), 1.5);
        var changed = AppearancePoseStorage.with(part, pose);
        var actual = AppearancePoseStorage.read(changed).orElseThrow();
        assertEquals(pose.scale(), actual.scale());
        assertEquals(pose.transform().position(), actual.transform().position());
        assertEquals("kept", changed.data().getString("author:data"));
        assertTrue(AppearancePoseStorage.read(part).isEmpty());
        assertEquals(part.id(), changed.id());
        assertEquals(part.interfaceType(), changed.interfaceType());
    }

    @Test void invalidSavedPoseFallsBackAndBoundsRejectNonfiniteInputs() {
        var id = ResourceLocation.parse("test:part");
        var data = new CompoundTag();
        var invalid = new CompoundTag(); invalid.putDouble("rw", 1); invalid.putDouble("scale", Double.NaN);
        data.put("equipment_structure_api:appearance_pose", invalid);
        assertTrue(AppearancePoseStorage.read(new EquipmentComponentInstance(id, id, id, data)).isEmpty());
        for (double coordinate : new double[]{32.01, -32.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new AppearancePose(coordinate, 0, 0, AppearanceRotation.IDENTITY, 1));
        }
        for (double scale : new double[]{0, 2.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new AppearancePose(AppearanceTransform.IDENTITY, scale));
        }
    }
}
