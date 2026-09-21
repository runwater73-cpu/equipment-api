package dev.equipmentstructure.api.appearance;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import net.minecraft.nbt.CompoundTag;

import java.util.Optional;

/** Namespaced storage for a player's per-slot visual adjustment. */
public final class AppearancePoseStorage {
    private static final String KEY = "equipment_structure_api:appearance_pose";

    private AppearancePoseStorage() {}

    public static Optional<AppearancePose> read(EquipmentComponentInstance component) {
        CompoundTag data = component.data();
        if (!data.contains(KEY, 10)) return Optional.empty();
        CompoundTag value = data.getCompound(KEY);
        try {
            AppearanceRotation rotation = new AppearanceRotation(value.getDouble("rx"), value.getDouble("ry"),
                    value.getDouble("rz"), value.getDouble("rw"));
            return Optional.of(new AppearancePose(value.getDouble("x"), value.getDouble("y"),
                    value.getDouble("z"), rotation, value.getDouble("scale")));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public static EquipmentComponentInstance with(EquipmentComponentInstance component, AppearancePose pose) {
        if (pose == null) throw new NullPointerException("pose");
        CompoundTag data = component.data();
        CompoundTag value = new CompoundTag();
        value.putDouble("x", pose.transform().position().x());
        value.putDouble("y", pose.transform().position().y());
        value.putDouble("z", pose.transform().position().z());
        value.putDouble("rx", pose.transform().rotation().x());
        value.putDouble("ry", pose.transform().rotation().y());
        value.putDouble("rz", pose.transform().rotation().z());
        value.putDouble("rw", pose.transform().rotation().w());
        value.putDouble("scale", pose.scale());
        data.put(KEY, value);
        return new EquipmentComponentInstance(component.id(), component.componentType(),
                component.interfaceType(), data);
    }
}
