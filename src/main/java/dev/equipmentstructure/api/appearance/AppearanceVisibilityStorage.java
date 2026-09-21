package dev.equipmentstructure.api.appearance;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Host visibility stays on the equipment; a part's visibility travels with that part. */
public final class AppearanceVisibilityStorage {
    private static final String ORIGINAL_HIDDEN = "equipment_structure_api:original_model_hidden";
    private static final String COMPONENT_HIDDEN = "equipment_structure_api:component_model_hidden";

    private AppearanceVisibilityStorage() {}

    public static boolean originalVisible(ItemStack stack) {
        return !stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(ORIGINAL_HIDDEN);
    }

    public static boolean componentVisible(EquipmentComponentInstance component) {
        return !component.data().getBoolean(COMPONENT_HIDDEN);
    }

    public static AppearanceVisibility read(ItemStack stack, EquipmentComponentInstance component) {
        return new AppearanceVisibility(originalVisible(stack), componentVisible(component));
    }

    public static void setOriginalVisible(ItemStack stack, boolean visible) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (visible) data.remove(ORIGINAL_HIDDEN);
        else data.putBoolean(ORIGINAL_HIDDEN, true);
        if (data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    public static EquipmentComponentInstance withComponentVisible(EquipmentComponentInstance component, boolean visible) {
        var data = component.data();
        if (visible) data.remove(COMPONENT_HIDDEN);
        else data.putBoolean(COMPONENT_HIDDEN, true);
        return new EquipmentComponentInstance(component.id(), component.componentType(), component.interfaceType(), data);
    }
}
