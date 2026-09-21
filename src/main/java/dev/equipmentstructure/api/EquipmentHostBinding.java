package dev.equipmentstructure.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** 附加到物品 Registry 条目的装备结构模板 ID。 */
public record EquipmentHostBinding(ResourceLocation host) {

    public static final Codec<EquipmentHostBinding> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("host").forGetter(EquipmentHostBinding::host)
            ).apply(instance, EquipmentHostBinding::new)
    );

    public EquipmentHostBinding {
        Objects.requireNonNull(host, "host");
    }
}
