package dev.equipmentstructure.api.test;

import dev.equipmentstructure.api.BuiltinEquipmentTypes;
import dev.equipmentstructure.api.EquipmentComponentDefinition;
import dev.equipmentstructure.api.grid.GridFootprint;
import net.minecraft.resources.ResourceLocation;

/** Explicit component defaults shared only by sword-based game-test fixtures. */
final class GameTestFixtures {
    private GameTestFixtures() {
    }

    static EquipmentComponentDefinition component(
            ResourceLocation id,
            ResourceLocation componentType,
            ResourceLocation interfaceType
    ) {
        return builder(id, componentType, interfaceType).build();
    }

    static EquipmentComponentDefinition component(
            ResourceLocation id,
            ResourceLocation componentType,
            ResourceLocation interfaceType,
            boolean removable
    ) {
        return builder(id, componentType, interfaceType)
                .removable(removable)
                .build();
    }

    static EquipmentComponentDefinition component(
            ResourceLocation id,
            ResourceLocation componentType,
            ResourceLocation interfaceType,
            boolean removable,
            EquipmentComponentDefinition.EquipmentComponentItemFactory itemFactory
    ) {
        return builder(id, componentType, interfaceType)
                .removable(removable)
                .itemFactory(itemFactory)
                .build();
    }

    private static EquipmentComponentDefinition.Builder builder(
            ResourceLocation id,
            ResourceLocation componentType,
            ResourceLocation interfaceType
    ) {
        return EquipmentComponentDefinition.builder(id, componentType, interfaceType)
                .suitableFor(BuiltinEquipmentTypes.SWORD)
                .footprint(GridFootprint.SINGLE_CELL);
    }
}
