package dev.equipmentstructure.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/**
 * 已安装到某件具体装备上的部件数据。
 *
 * <p>API 只规定部件身份、部件类型和可扩展数据。部件的具体属性、模型和
 * 制作方式由内容模组自行解释。</p>
 */
public record EquipmentComponentInstance(
        ResourceLocation id,
        ResourceLocation componentType,
        Optional<ResourceLocation> interfaceType,
        CompoundTag data
) {

    public static final Codec<EquipmentComponentInstance> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("id").forGetter(EquipmentComponentInstance::id),
                    ResourceLocation.CODEC.fieldOf("component_type")
                            .forGetter(EquipmentComponentInstance::componentType),
                    ResourceLocation.CODEC.xmap(Optional::of, value -> value.orElseThrow()).fieldOf("interface_type")
                            .forGetter(EquipmentComponentInstance::interfaceType),
                    CompoundTag.CODEC.optionalFieldOf("data", new CompoundTag())
                            .forGetter(EquipmentComponentInstance::data)
            ).apply(instance, EquipmentComponentInstance::new)
    );

    public EquipmentComponentInstance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(componentType, "componentType");
        Objects.requireNonNull(interfaceType, "interfaceType");
        if (interfaceType.isEmpty()) throw new IllegalArgumentException("Component interface_type is required");
        Objects.requireNonNull(data, "data");
        data = data.copy();
    }

    /** Creates a component whose interface and component type use the same explicit ID. */
    public EquipmentComponentInstance(
            ResourceLocation id,
            ResourceLocation componentType,
            CompoundTag data
    ) {
        this(id, componentType, Optional.of(componentType), data);
    }

    public EquipmentComponentInstance(ResourceLocation id, ResourceLocation componentType) {
        this(id, componentType, Optional.of(componentType), new CompoundTag());
    }

    /** 创建明确声明接口类型的部件。 */
    public EquipmentComponentInstance(
            ResourceLocation id,
            ResourceLocation componentType,
            ResourceLocation interfaceType,
            CompoundTag data
    ) {
        this(id, componentType, Optional.of(interfaceType), data);
    }

    public EquipmentComponentInstance(
            ResourceLocation id,
            ResourceLocation componentType,
            ResourceLocation interfaceType
    ) {
        this(id, componentType, Optional.of(interfaceType), new CompoundTag());
    }

    /** 返回扩展数据副本，防止绕过结构 API 修改已安装部件。 */
    @Override
    public CompoundTag data() {
        return data.copy();
    }
}
