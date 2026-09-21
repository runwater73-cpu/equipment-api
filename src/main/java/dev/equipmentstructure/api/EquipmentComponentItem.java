package dev.equipmentstructure.api;

import net.minecraft.world.item.ItemStack;

/**
 * Native component reader for items owned by a content mod. Existing third-party item classes
 * can instead be bound through {@link EquipmentComponentItemAdapters} without implementing this.
 * The returned identity must exactly match a registered definition. Its factory must restore
 * all input item components; otherwise the shared menu/tooltip read path rejects the input.
 */
public interface EquipmentComponentItem {

    EquipmentComponentInstance createComponent(ItemStack stack);
}
