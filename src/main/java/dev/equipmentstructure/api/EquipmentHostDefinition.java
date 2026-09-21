package dev.equipmentstructure.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;
import dev.equipmentstructure.api.grid.GridBoard;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.ArrayList;
import java.util.Optional;

/**
 * 可应用到装备上的结构模板。
 *
 * <p>模板描述装备应该拥有哪些槽位。它不保存某一件装备实际安装的部件，
 * 也不依赖材料、属性或具体制作流程。</p>
 */
public record EquipmentHostDefinition(
        ResourceLocation id,
        ResourceLocation equipmentType,
        List<EquipmentSlotDefinition> slots,
        int version,
        Optional<GridBoard> grid
) {

    public static final Codec<EquipmentHostDefinition> CODEC = EquipmentStructureStorage.StoredHost.CODEC
            .flatXmap(EquipmentStructureStorage.StoredHost::decode,
                    host -> DataResult.success(EquipmentStructureStorage.StoredHost.from(host)));

    public EquipmentHostDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(equipmentType, "equipmentType");
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(grid, "grid");
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("An equipment host requires at least one slot");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("Equipment host version must be positive");
        }
        Set<ResourceLocation> ids = new HashSet<>();
        for (EquipmentSlotDefinition slot : slots) {
            Objects.requireNonNull(slot, "slot");
            if (!ids.add(slot.id())) {
                throw new IllegalArgumentException("Duplicate equipment slot: " + slot.id());
            }
        }
        slots = List.copyOf(slots);
    }

    public EquipmentHostDefinition(ResourceLocation id, ResourceLocation equipmentType,
                                   List<EquipmentSlotDefinition> slots) {
        this(id, equipmentType, slots, 1, Optional.empty());
    }

    /** Creates a template with an explicit schema version. */
    public EquipmentHostDefinition(ResourceLocation id, ResourceLocation equipmentType,
                                   List<EquipmentSlotDefinition> slots, int version) {
        this(id, equipmentType, slots, version, Optional.empty());
    }

    /** Creates a template whose board is published with the template. */
    public EquipmentHostDefinition(ResourceLocation id, ResourceLocation equipmentType,
                                   List<EquipmentSlotDefinition> slots, GridBoard grid) {
        this(id, equipmentType, slots, 1, Optional.of(Objects.requireNonNull(grid, "grid")));
    }

    /**
     * 面向内容模组的简化构建入口。
     *
     * <pre>
     * EquipmentHostDefinition sword = EquipmentHostDefinition.builder(id, BuiltinEquipmentTypes.SWORD)
     *         .slot(blade, bladeType)
     *         .slot(core, coreType)
     *         .build();
     * </pre>
     */
    public static Builder builder(ResourceLocation id, ResourceLocation equipmentType) {
        return new Builder(id, equipmentType);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private final ResourceLocation equipmentType;
        private final List<EquipmentSlotDefinition> slots = new ArrayList<>();
        private int version = 1;
        private Optional<GridBoard> grid = Optional.empty();

        private Builder(ResourceLocation id, ResourceLocation equipmentType) {
            this.id = Objects.requireNonNull(id, "id");
            this.equipmentType = Objects.requireNonNull(equipmentType, "equipmentType");
        }

        public Builder slot(EquipmentSlotDefinition slot) {
            slots.add(Objects.requireNonNull(slot, "slot"));
            return this;
        }

        /** Adds one slot whose accepted component type is the interface type. */
        public Builder slot(ResourceLocation slotId, ResourceLocation interfaceType) {
            return slot(new EquipmentSlotDefinition(slotId, interfaceType));
        }

        /** Adds one slot with an explicit interface type and component type. */
        public Builder slot(ResourceLocation slotId, ResourceLocation interfaceType,
                            ResourceLocation componentType) {
            return slot(new EquipmentSlotDefinition(slotId, interfaceType, componentType));
        }

        public Builder version(int version) {
            this.version = version;
            return this;
        }

        /** Adds the equipment board owned by this template. */
        public Builder grid(GridBoard value) {
            this.grid = Optional.of(Objects.requireNonNull(value, "grid"));
            return this;
        }

        public EquipmentHostDefinition build() {
            return new EquipmentHostDefinition(id, equipmentType, slots, version, grid);
        }
    }

    /** Starts a new template with this template's type, slots, version and board. */
    public Builder copyBuilder(ResourceLocation newId) {
        Builder builder = new Builder(Objects.requireNonNull(newId, "newId"), equipmentType)
                .version(version);
        slots.forEach(builder::slot);
        grid.ifPresent(builder::grid);
        return builder;
    }

    /** 创建一份可以写入 ItemStack 的结构实例。 */
    public EquipmentStructure createStructure() {
        return EquipmentStructure.fromDefinition(this);
    }
}
