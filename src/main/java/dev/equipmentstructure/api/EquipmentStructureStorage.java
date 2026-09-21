package dev.equipmentstructure.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import dev.equipmentstructure.api.grid.GridState;
import dev.equipmentstructure.api.grid.GridBoard;
import dev.equipmentstructure.api.grid.GridCodecs;
import java.util.Set;
import java.util.function.Supplier;

/** Serialized forms for equipment host definitions and structure snapshots. */
final class EquipmentStructureStorage {
    private EquipmentStructureStorage() {}

    record StoredSlot(ResourceLocation id, ResourceLocation interfaceType,
                      ResourceLocation componentType) {
        static final Codec<StoredSlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(StoredSlot::id),
                ResourceLocation.CODEC.fieldOf("interface_type").forGetter(StoredSlot::interfaceType),
                ResourceLocation.CODEC.fieldOf("component_type").forGetter(StoredSlot::componentType)
        ).apply(instance, StoredSlot::new));

        static StoredSlot from(EquipmentSlotDefinition slot) {
            return new StoredSlot(slot.id(), slot.interfaceType(), slot.componentType());
        }

        EquipmentSlotDefinition single(ResourceLocation slotId) {
            return new EquipmentSlotDefinition(slotId, interfaceType, componentType);
        }
    }

    record StoredHost(ResourceLocation id, ResourceLocation equipmentType,
                      List<StoredSlot> slots, int version, Optional<GridBoard> grid) {
        static final Codec<StoredHost> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(StoredHost::id),
                ResourceLocation.CODEC.fieldOf("equipment_type").forGetter(StoredHost::equipmentType),
                StoredSlot.CODEC.listOf().fieldOf("slots").forGetter(StoredHost::slots),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("version", 1).forGetter(StoredHost::version),
                GridCodecs.BOARD.optionalFieldOf("grid").forGetter(StoredHost::grid)
        ).apply(instance, StoredHost::new));

        DataResult<EquipmentHostDefinition> decode() {
            return checked(() -> new EquipmentHostDefinition(
                    id, equipmentType, normalize(slots, Map.of()).slots(), version, grid));
        }

        static StoredHost from(EquipmentHostDefinition host) {
            return new StoredHost(host.id(), host.equipmentType(),
                    host.slots().stream().map(StoredSlot::from).toList(), host.version(), host.grid());
        }
    }

    record StoredStructure(ResourceLocation hostId, ResourceLocation equipmentType, List<StoredSlot> slots,
                           Map<ResourceLocation, List<EquipmentComponentInstance>> components, int version,
                           Optional<GridState> grid) {
        static final Codec<StoredStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("host_id").forGetter(StoredStructure::hostId),
                ResourceLocation.CODEC.fieldOf("equipment_type").forGetter(StoredStructure::equipmentType),
                StoredSlot.CODEC.listOf().fieldOf("slots").forGetter(StoredStructure::slots),
                Codec.unboundedMap(ResourceLocation.CODEC, EquipmentComponentInstance.CODEC.listOf())
                        .optionalFieldOf("components", Map.of()).forGetter(StoredStructure::components),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("version", 1)
                        .forGetter(StoredStructure::version),
                GridState.CODEC.optionalFieldOf("grid").forGetter(StoredStructure::grid)
        ).apply(instance, StoredStructure::new));

        DataResult<EquipmentStructure> decode() {
            return checked(() -> {
                Normalized normalized = normalize(slots, components);
                return new EquipmentStructure(hostId, equipmentType,
                        normalized.slots(), normalized.components(), version, grid);
            });
        }

        static StoredStructure from(EquipmentStructure structure) {
            return new StoredStructure(structure.hostId(), structure.equipmentType(),
                    structure.slots().stream().map(StoredSlot::from).toList(),
                    structure.components(), structure.version(), structure.grid());
        }
    }

    /** Validates every installation point and component, including component data and order. */
    private static Normalized normalize(List<StoredSlot> stored,
                                         Map<ResourceLocation, List<EquipmentComponentInstance>> components) {
        Set<ResourceLocation> reserved = new HashSet<>();
        for (StoredSlot slot : stored) {
            if (!reserved.add(slot.id())) {
                throw new IllegalArgumentException("Duplicate equipment slot: " + slot.id());
            }
        }
        if (!reserved.containsAll(components.keySet())) {
            throw new IllegalArgumentException("Component refers to an unknown equipment slot");
        }
        List<EquipmentSlotDefinition> slots = new ArrayList<>();
        Map<ResourceLocation, List<EquipmentComponentInstance>> migrated = new LinkedHashMap<>();
        for (StoredSlot slot : stored) {
            List<EquipmentComponentInstance> values = components.getOrDefault(slot.id(), List.of());
            if (values.size() > 1) {
                throw new IllegalArgumentException("An equipment slot can contain at most one component: " + slot.id());
            }
            slots.add(slot.single(slot.id()));
            if (!values.isEmpty()) {
                migrated.put(slot.id(), List.of(values.getFirst()));
            }
        }
        return new Normalized(List.copyOf(slots), Map.copyOf(migrated));
    }

    private static <T> DataResult<T> checked(Supplier<T> factory) {
        try {
            return DataResult.success(factory.get());
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private record Normalized(List<EquipmentSlotDefinition> slots,
                              Map<ResourceLocation, List<EquipmentComponentInstance>> components) {}
}
