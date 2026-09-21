package dev.equipmentstructure.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * 一个有独立 ID 的装备安装点，最多安装一个部件。
 *
 * <p>{@code id} 表示装备上的具体物理安装点，{@code interfaceType} 表示
 * 兼容类别。因此同一件装备可以声明多个相同 {@code interfaceType} 的
 * 附着点，只要为每个安装位置分配不同的 {@code id}。接口名称不要求代表
 * 被替换的原装备部位；内容作者可以用它表达剑穗、符文、挂件或其他装备附件类
 * 附着点。</p>
 */
public record EquipmentSlotDefinition(
        ResourceLocation id,
        ResourceLocation interfaceType,
        ResourceLocation componentType
) {
    /** 创建一个安装点，接口类型和部件类型使用同一个 ID。 */
    public static EquipmentSlotDefinition of(
            ResourceLocation id,
            ResourceLocation componentType
    ) {
        return new EquipmentSlotDefinition(id, componentType, componentType);
    }

    /** 创建一个接口类型和部件类型分别声明的安装点。 */
    public static EquipmentSlotDefinition of(
            ResourceLocation id,
            ResourceLocation interfaceType,
            ResourceLocation componentType
    ) {
        return new EquipmentSlotDefinition(id, interfaceType, componentType);
    }

    public static final Codec<EquipmentSlotDefinition> CODEC =
            EquipmentStructureStorage.StoredSlot.CODEC.xmap(
                    stored -> stored.single(stored.id()),
                    EquipmentStructureStorage.StoredSlot::from);

    public EquipmentSlotDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(interfaceType, "interfaceType");
        Objects.requireNonNull(componentType, "componentType");
    }

    public EquipmentSlotDefinition(
            ResourceLocation id,
            ResourceLocation componentType
    ) {
        this(id, componentType, componentType);
    }

    /**
     * Creates a concrete equipment slot from a registered interface type.
     * The slot ID is intentionally supplied by the host author, allowing
     * several concrete slots to share one interface type.
     */
    public static EquipmentSlotDefinition fromInterface(ResourceLocation slotId,
                                                        ResourceLocation interfaceId) {
        EquipmentInterfaceDefinition definition = EquipmentInterfaceRegistry.get(interfaceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown equipment interface: " + interfaceId));
        return new EquipmentSlotDefinition(slotId, definition.id(), definition.defaultComponentType());
    }

    /**
     * Complete install-time policy for this slot.
     *
     * <p>The host category, connection interface and component category must
     * all match. Concrete components must be registered, because their host
     * category declaration is authoritative.</p>
     */
    public boolean accepts(ResourceLocation equipmentType, EquipmentComponentInstance component) {
        Objects.requireNonNull(equipmentType, "equipmentType");
        if (component == null) {
            return false;
        }
        var registered = EquipmentComponentRegistry.get(component.id());
        if (registered.isEmpty()) {
            return false;
        }
        EquipmentComponentDefinition definition = registered.get();
        ResourceLocation actualInterface = component.interfaceType().orElseThrow();
        if (!definition.componentType().equals(component.componentType())
                || !definition.interfaceType().equals(actualInterface)) {
            return false;
        }
        return definition.supportsEquipmentType(equipmentType)
                && EquipmentComponentTypeRegistry.isCompatible(definition.componentType(), componentType)
                && EquipmentComponentTypeRegistry.isCompatible(definition.interfaceType(), interfaceType);
    }

    /** Structural type check independent of concrete component registrations. */
    boolean acceptsTypes(EquipmentComponentInstance component) {
        return component != null
                && EquipmentComponentTypeRegistry.isCompatible(component.componentType(), componentType)
                && EquipmentComponentTypeRegistry.isCompatible(
                component.interfaceType().orElseThrow(), interfaceType
        );
    }
}
