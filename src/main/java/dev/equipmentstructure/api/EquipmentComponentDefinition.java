package dev.equipmentstructure.api;

import dev.equipmentstructure.api.grid.GridFootprint;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;

/** Describes one installable component type without imposing gameplay semantics. */
public record EquipmentComponentDefinition(
        ResourceLocation id,
        ResourceLocation componentType,
        ResourceLocation interfaceType,
        Set<ResourceLocation> suitableEquipmentTypes,
        boolean removable,
        Optional<EquipmentComponentItemFactory> itemFactory,
        GridFootprint footprint
) {
    public EquipmentComponentDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(componentType, "componentType");
        Objects.requireNonNull(interfaceType, "interfaceType");
        suitableEquipmentTypes = Set.copyOf(Objects.requireNonNull(
                suitableEquipmentTypes, "suitableEquipmentTypes"));
        if (suitableEquipmentTypes.isEmpty()) {
            throw new IllegalArgumentException("A component requires at least one suitable equipment type");
        }
        Objects.requireNonNull(itemFactory, "itemFactory");
        Objects.requireNonNull(footprint, "footprint");
    }

    /** Starts an explicit component registration. Equipment types and footprint are mandatory. */
    public static Builder builder(ResourceLocation id, ResourceLocation componentType,
                                  ResourceLocation interfaceType) {
        return new Builder(id, componentType, interfaceType);
    }

    /** Adds component-owned geometry without changing identity, item restoration or compatibility. */
    public EquipmentComponentDefinition withFootprint(GridFootprint footprint) {
        return new EquipmentComponentDefinition(id, componentType, interfaceType,
                suitableEquipmentTypes, removable, itemFactory, footprint);
    }

    public boolean supportsEquipmentType(ResourceLocation equipmentType) {
        return suitableEquipmentTypes.contains(Objects.requireNonNull(equipmentType, "equipmentType"));
    }

    /** Creates an instance with the exact registered identity and a defensive data copy. */
    public EquipmentComponentInstance createInstance(CompoundTag data) {
        return new EquipmentComponentInstance(id, componentType, interfaceType, data);
    }

    public EquipmentComponentInstance createInstance() {
        return createInstance(new CompoundTag());
    }

    /** Recreates the component item used for safe GUI removal, when supplied by its author. */
    public Optional<ItemStack> createItemStack(EquipmentComponentInstance instance) {
        if (!id.equals(instance.id())) {
            return Optional.empty();
        }
        return itemFactory.map(factory -> factory.create(instance));
    }

    @FunctionalInterface
    public interface EquipmentComponentItemFactory {
        ItemStack create(EquipmentComponentInstance instance);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private final ResourceLocation componentType;
        private final ResourceLocation interfaceType;
        private final Set<ResourceLocation> suitableEquipmentTypes = new LinkedHashSet<>();
        private boolean removable = true;
        private Optional<EquipmentComponentItemFactory> itemFactory = Optional.empty();
        private GridFootprint footprint;

        private Builder(ResourceLocation id, ResourceLocation componentType,
                        ResourceLocation interfaceType) {
            this.id = Objects.requireNonNull(id, "id");
            this.componentType = Objects.requireNonNull(componentType, "componentType");
            this.interfaceType = Objects.requireNonNull(interfaceType, "interfaceType");
        }

        public Builder suitableFor(ResourceLocation... equipmentTypes) {
            Objects.requireNonNull(equipmentTypes, "equipmentTypes");
            for (ResourceLocation equipmentType : equipmentTypes) {
                suitableEquipmentTypes.add(Objects.requireNonNull(equipmentType, "equipmentType"));
            }
            return this;
        }

        public Builder footprint(GridFootprint footprint) {
            this.footprint = Objects.requireNonNull(footprint, "footprint");
            return this;
        }

        public Builder removable(boolean removable) {
            this.removable = removable;
            return this;
        }

        public Builder itemFactory(EquipmentComponentItemFactory itemFactory) {
            this.itemFactory = Optional.of(Objects.requireNonNull(itemFactory, "itemFactory"));
            return this;
        }

        public EquipmentComponentDefinition build() {
            if (suitableEquipmentTypes.isEmpty()) {
                throw new IllegalStateException("Declare at least one suitable equipment type");
            }
            if (footprint == null) {
                throw new IllegalStateException("Declare the component grid footprint explicitly");
            }
            return new EquipmentComponentDefinition(id, componentType, interfaceType,
                    suitableEquipmentTypes, removable, itemFactory, footprint);
        }
    }
}
