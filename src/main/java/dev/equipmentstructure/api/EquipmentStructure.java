package dev.equipmentstructure.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;
import dev.equipmentstructure.api.grid.GridState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * 一件具体装备的结构快照。
 *
 * <p>结构快照保存主体模板的身份、当前有效槽位以及已安装部件。槽位列表
 * 可以包含由材料、能力或其他模组动态生成的接口，因此旧装备不依赖运行时
 * 重新推导接口。</p>
 *
 * <p>{@code version} 是作者定义的数据模式版本。安装部件和追加动态接口保持版本；
 * 模式升级由已注册的迁移步骤显式推进，与界面同步或修改次数无关。</p>
 */
public record EquipmentStructure(
        ResourceLocation hostId,
        ResourceLocation equipmentType,
        List<EquipmentSlotDefinition> slots,
        Map<ResourceLocation, List<EquipmentComponentInstance>> components,
        int version,
        Optional<GridState> grid
) {

    public static final Codec<EquipmentStructure> CODEC = EquipmentStructureStorage.StoredStructure.CODEC
            .flatXmap(EquipmentStructureStorage.StoredStructure::decode,
                    structure -> DataResult.success(EquipmentStructureStorage.StoredStructure.from(structure)));

    public EquipmentStructure {
        Objects.requireNonNull(hostId, "hostId");
        Objects.requireNonNull(equipmentType, "equipmentType");
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(components, "components");
        Objects.requireNonNull(grid, "grid");
        if (version <= 0) {
            throw new IllegalArgumentException("Equipment structure version must be positive");
        }

        Set<ResourceLocation> slotIds = new HashSet<>();
        for (EquipmentSlotDefinition slot : slots) {
            Objects.requireNonNull(slot, "slot");
            if (!slotIds.add(slot.id())) {
                throw new IllegalArgumentException("Duplicate equipment slot: " + slot.id());
            }
        }

        TreeMap<ResourceLocation, List<EquipmentComponentInstance>> normalized = new TreeMap<>(
                java.util.Comparator.comparing(ResourceLocation::toString)
        );
        for (Map.Entry<ResourceLocation, List<EquipmentComponentInstance>> entry : components.entrySet()) {
            ResourceLocation slotId = Objects.requireNonNull(entry.getKey(), "component slot id");
            if (!slotIds.contains(slotId)) {
                throw new IllegalArgumentException("Component refers to unknown slot: " + slotId);
            }
            List<EquipmentComponentInstance> values = Objects.requireNonNull(entry.getValue(), "components");
            if (values.isEmpty()) {
                continue;
            }
            List<EquipmentComponentInstance> copy = new ArrayList<>(values.size());
            for (EquipmentComponentInstance component : values) {
                EquipmentComponentInstance nonNullComponent =
                        Objects.requireNonNull(component, "component");
                // Persisted instances survive missing/changed runtime type registrations.
                // Compatibility is enforced when adding new components, not while loading.
                copy.add(nonNullComponent);
            }
            if (copy.size() > 1) {
                throw new IllegalArgumentException("Interface " + slotId + " accepts only one component");
            }
            normalized.put(slotId, List.copyOf(copy));
        }
        slots = List.copyOf(slots);
        components = Map.copyOf(normalized);
    }

    public EquipmentStructure(
            ResourceLocation hostId, ResourceLocation equipmentType, List<EquipmentSlotDefinition> slots,
            Map<ResourceLocation, List<EquipmentComponentInstance>> components, int version
    ) {
        this(hostId, equipmentType, slots, components, version, Optional.empty());
    }

    public EquipmentStructure(
            ResourceLocation hostId,
            ResourceLocation equipmentType,
            List<EquipmentSlotDefinition> slots,
            Map<ResourceLocation, List<EquipmentComponentInstance>> components
    ) {
        this(hostId, equipmentType, slots, components, 1);
    }

    public static EquipmentStructure fromDefinition(EquipmentHostDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return new EquipmentStructure(
                definition.id(),
                definition.equipmentType(),
                definition.slots(),
                Map.of(),
                definition.version()
        );
    }

    public Optional<EquipmentSlotDefinition> slot(ResourceLocation slotId) {
        Objects.requireNonNull(slotId, "slotId");
        return slots.stream().filter(slot -> slot.id().equals(slotId)).findFirst();
    }

    public List<EquipmentComponentInstance> components(ResourceLocation slotId) {
        Objects.requireNonNull(slotId, "slotId");
        return components.getOrDefault(slotId, List.of());
    }

    public int componentCount(ResourceLocation slotId) {
        return components(slotId).size();
    }

    /** The sole component occupying an interface, or empty when unoccupied. */
    public Optional<EquipmentComponentInstance> component(ResourceLocation slotId) {
        return components(slotId).stream().findFirst();
    }

    /** Creates a snapshot with the sole component in one existing interface replaced. */
    public EquipmentStructure withComponent(ResourceLocation slotId, EquipmentComponentInstance component) {
        Objects.requireNonNull(component, "component");
        return withComponents(slotId, List.of(component));
    }

    public EquipmentStructure withComponents(
            ResourceLocation slotId,
            List<EquipmentComponentInstance> newComponents
    ) {
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(newComponents, "newComponents");
        EquipmentSlotDefinition definition = slot(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown equipment slot: " + slotId));
        if (newComponents.size() > 1) {
            throw new IllegalArgumentException("Interface " + slotId + " accepts only one component");
        }
        for (EquipmentComponentInstance component : newComponents) {
            if (!definition.acceptsTypes(component)) {
                throw new IllegalArgumentException("Incompatible component for interface " + slotId);
            }
        }
        TreeMap<ResourceLocation, List<EquipmentComponentInstance>> updated = new TreeMap<>(
                java.util.Comparator.comparing(ResourceLocation::toString)
        );
        updated.putAll(components);
        if (newComponents.isEmpty()) {
            updated.remove(slotId);
        } else {
            updated.put(slotId, List.copyOf(newComponents));
        }
        return new EquipmentStructure(hostId, equipmentType, slots, updated, version,
                newComponents.isEmpty() ? grid.map(state -> state.without(slotId)) : grid);
    }

    /** Appends one interface without advancing the host's schema version. */
    public EquipmentStructure addSlot(EquipmentSlotDefinition slot) {
        Objects.requireNonNull(slot, "slot");
        if (this.slot(slot.id()).isPresent()) {
            throw new IllegalArgumentException("Equipment slot already exists: " + slot.id());
        }
        List<EquipmentSlotDefinition> updated = new ArrayList<>(slots);
        updated.add(slot);
        return new EquipmentStructure(hostId, equipmentType, updated, components, version, grid);
    }

    /**
     * Creates a snapshot with an explicit schema version, for pure migration functions.
     * This does not write an item or run migrations; use EquipmentStructureApi.migrate for that.
     */
    public EquipmentStructure withVersion(int newVersion) {
        return newVersion == version ? this : new EquipmentStructure(hostId, equipmentType, slots, components, newVersion, grid);
    }

    /** Pure snapshot operation; installation/geometry authority belongs to EquipmentStructureApi. */
    public EquipmentStructure withGrid(GridState state) {
        return new EquipmentStructure(hostId, equipmentType, slots, components, version, Optional.of(state));
    }

}
