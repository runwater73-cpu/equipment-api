package dev.equipmentstructure.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** 根据装备物品解析结构模板的扩展点。 */
@FunctionalInterface
public interface EquipmentHostProvider {

    /** Returns the matching template ID. Input is an isolated copy; empty means unsupported. */
    Optional<ResourceLocation> resolve(ItemStack stack);
}
