package dev.equipmentstructure.api.compat.curios;

import dev.equipmentstructure.api.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.Optional;

/** The envelope owns only integration metadata; the complete third-party ItemStack is preserved intact. */
final class CuriosComponentItems implements EquipmentSlotItemAdapters.Adapter {
    static final String STACK = "equipment_structure_api:curios_item";
    static final String TYPE = "equipment_structure_api:curios_type";
    static final String COSMETIC = "equipment_structure_api:curios_cosmetic";
    @Override public boolean handles(EquipmentSlotDefinition slot) { return CuriosSlotKey.parse(slot.id()).isPresent(); }
    @Override public Optional<EquipmentComponentInstance> read(ItemStack input, ItemStack equipment,
            EquipmentSlotDefinition slot, LivingEntity actor) {
        if (input.isEmpty() || actor == null || !CuriosArmorCompat.definitions(actor).enabled()) return Optional.empty();
        var key = CuriosSlotKey.parse(slot.id()).orElseThrow();
        if (!key.cosmetic() && PlayerBoundCurios.eligible(input, actor) && !PlayerBoundCurios.stableSlot(actor, key)) return Optional.empty();
        if (!PlayerBoundCurios.item(actor, slot.id()).isEmpty()) return Optional.empty();
        if (!CuriosEquipmentTemplates.active(equipment, actor, key)) return Optional.empty();
        var context = CuriosArmorCompat.context(actor, key);
        if (!CuriosArmorCompat.canEquipOriginal(context, input, equipment)) return Optional.empty();
        return encode(input, key, actor.registryAccess(), CuriosArmorCompat.definitions(actor));
    }
    static Optional<EquipmentComponentInstance> encode(ItemStack input, CuriosSlotKey key,
            RegistryAccess registries, CuriosDefinitions profiles) {
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(input.getItem());
        var id = profiles.items().containsKey(item) ? key.itemComponent(item) : key.defaultComponent();
        var definition = EquipmentComponentRegistry.get(id).orElse(null);
        if (definition == null || input.isEmpty()) return Optional.empty();
        CompoundTag data = new CompoundTag();
        data.put(STACK, input.copyWithCount(1).save(registries));
        data.putString(TYPE, key.type());
        data.putBoolean(COSMETIC, key.cosmetic());
        return Optional.of(definition.createInstance(data));
    }
    static ItemStack decode(EquipmentComponentInstance part, RegistryAccess registries) {
        return ItemStack.parseOptional(registries, part.data().getCompound(STACK));
    }
    static boolean isCurios(EquipmentComponentInstance part) { return part.data().contains(STACK, 10); }
    @Override public boolean canRemove(ItemStack equipment, EquipmentSlotDefinition slot, LivingEntity actor) {
        var pending = EquipmentStructureApi.component(equipment, slot.id()).orElse(null);
        if (pending != null && actor != null && PlayerBoundCurios.eligible(decode(pending, actor.registryAccess()), actor)) return true;
        return CuriosArmorCompat.canRemove(equipment, CuriosSlotKey.parse(slot.id()).orElseThrow(), actor);
    }
    @Override public ItemStack playerOwnedItem(EquipmentSlotDefinition slot, LivingEntity actor) {
        return PlayerBoundCurios.item(actor, slot.id()).copy();
    }
    @Override public ItemStack removePlayerOwnedItem(EquipmentSlotDefinition slot, LivingEntity actor) {
        return PlayerBoundCurios.remove(actor, slot.id());
    }
    @Override public Optional<Boolean> restores(EquipmentComponentInstance part, ItemStack restored) {
        if (!isCurios(part)) return Optional.empty();
        var registries = CuriosArmorCompat.registries();
        return Optional.of(registries != null && ItemStack.isSameItemSameComponents(decode(part, registries), restored));
    }
    @Override public void prepare(ItemStack equipment, RegistryAccess registries, LivingEntity actor) {
        if (actor != null) CuriosEquipmentTemplates.prepare(equipment, actor);
    }
    @Override public void beforeBreak(ItemStack equipment, LivingEntity actor) { ArmorCurioStackHandler.beforeCopy(equipment); }
}
