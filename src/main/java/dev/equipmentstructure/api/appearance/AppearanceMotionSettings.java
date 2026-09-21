package dev.equipmentstructure.api.appearance;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import net.minecraft.nbt.CompoundTag;
import java.util.Objects;

/** Player orbit edits. Speeds and self-spin remain author-owned resource settings. */
public record AppearanceMotionSettings(Center center, AppearanceRotation tilt) {
    public enum Center { AUTHOR, HOST, LOCAL }
    public static final AppearanceMotionSettings DEFAULT = new AppearanceMotionSettings(Center.AUTHOR, AppearanceRotation.IDENTITY);
    private static final String KEY = "equipment_structure_api:appearance_motion";

    public AppearanceMotionSettings {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(tilt, "tilt");
    }

    public static AppearanceMotionSettings read(EquipmentComponentInstance part) {
        var data = part.data();
        if (!data.contains(KEY, 10)) return DEFAULT;
        var tag = data.getCompound(KEY);
        try {
            return new AppearanceMotionSettings(Center.valueOf(tag.getString("center")),
                    new AppearanceRotation(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getDouble("w")));
        } catch (IllegalArgumentException ignored) { return DEFAULT; }
    }

    public EquipmentComponentInstance apply(EquipmentComponentInstance part) {
        if (equals(read(part))) return part;
        var data = part.data();
        if (equals(DEFAULT)) data.remove(KEY);
        else {
            var tag = new CompoundTag();
            tag.putString("center", center.name());
            tag.putDouble("x", tilt.x()); tag.putDouble("y", tilt.y());
            tag.putDouble("z", tilt.z()); tag.putDouble("w", tilt.w());
            data.put(KEY, tag);
        }
        return new EquipmentComponentInstance(part.id(), part.componentType(), part.interfaceType(), data);
    }
}
