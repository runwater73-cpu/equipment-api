package dev.equipmentstructure.api.client.appearance;

import net.minecraft.world.item.ItemDisplayContext;

/** Display contexts a host-equipment appearance adapter may explicitly support. */
public enum EquipmentAppearanceScene {
    GUI,
    INVENTORY,
    FIRST_PERSON,
    THIRD_PERSON,
    GROUND,
    FIXED,
    ARMOR;

    /** Converts vanilla item display contexts without leaking them into API data. */
    public static EquipmentAppearanceScene from(ItemDisplayContext context) {
        return switch (context) {
            case GUI -> GUI;
            case GROUND -> GROUND;
            case FIXED -> FIXED;
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> FIRST_PERSON;
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> THIRD_PERSON;
            case HEAD, NONE -> INVENTORY;
        };
    }
}
