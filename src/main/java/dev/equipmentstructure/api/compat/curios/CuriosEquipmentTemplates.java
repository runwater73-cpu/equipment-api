package dev.equipmentstructure.api.compat.curios;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.menu.EquipmentAssemblyLimits;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/** Uses native Curios installation points on armor; the selected template retains its grid and body. */
public final class CuriosEquipmentTemplates {
    private record Prepared(EquipmentStructure structure, CuriosDefinitions config, int actor, long tick) {}
    private static final java.util.Map<ItemStack, Prepared> PREPARED = new java.util.WeakHashMap<>();
    private CuriosEquipmentTemplates() {}
    public static EquipmentSlot position(ItemStack equipment) {
        var slot = equipment.getItem() instanceof ArmorItem armor ? armor.getEquipmentSlot() : null;
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET ? slot : null;
    }
    public static ResourceLocation template(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> BuiltinEquipmentTemplates.HELMET;
            case CHEST -> BuiltinEquipmentTemplates.CHESTPLATE;
            case LEGS -> BuiltinEquipmentTemplates.LEGGINGS;
            case FEET -> BuiltinEquipmentTemplates.BOOTS;
            default -> throw new IllegalArgumentException("Unsupported armor position: " + slot);
        };
    }
    public static void prepare(ItemStack equipment, LivingEntity actor) {
        if (equipment.isEmpty() || actor.level().isClientSide()) return;
        var config = CuriosArmorCompat.definitions(actor);
        if (!config.enabled() || config.excluded().contains(BuiltInRegistries.ITEM.getKey(equipment.getItem()))) return;
        var cached = PREPARED.get(equipment);
        if (cached != null && cached.config == config && cached.actor == actor.getId()
                && cached.tick == actor.level().getGameTime()
                && cached.structure == EquipmentStructureApi.structure(equipment).orElse(null)) return;
        if (!EquipmentStructureApi.hasStructure(equipment))
            EquipmentStructureApi.initializeFromProvider(equipment, actor.registryAccess());
        var structure = EquipmentStructureApi.structure(equipment).orElse(null);
        if (structure == null) return;
        var position = position(equipment);
        var inventory = CuriosApi.getCuriosInventory(actor).orElse(null);
        var slots = CuriosApi.getEntitySlots(actor);
        var targetSlots = new java.util.LinkedHashMap<ResourceLocation, EquipmentSlotDefinition>();
        var occupiedNative = new java.util.LinkedHashMap<ResourceLocation, EquipmentSlotDefinition>();
        // Preset armor slots are replaced, while addon-authored slots and occupied items remain recoverable.
        for (var slot : structure.slots()) {
            boolean preset = position != null && BuiltinEquipmentSlots.all().contains(slot.id());
            boolean nativeSlot = CuriosSlotKey.parse(slot.id()).isPresent();
            if (nativeSlot) {
                if (structure.component(slot.id()).isPresent()) occupiedNative.put(slot.id(), slot);
            } else if (!preset || structure.component(slot.id()).isPresent()) targetSlots.put(slot.id(), slot);
        }
        for (var type : slots.keySet().stream().sorted(CuriosArmorCompat.slotOrder(actor)).toList()) {
            var profile = config.profile(type);
            if (!CuriosArmorCompat.managesType(actor, type) || profile.armorSlot() != position
                    || profile.template().filter(id -> !id.equals(structure.hostId())).isPresent()) continue;
            var nativeHandler = inventory == null ? null : inventory.getStacksHandler(type).orElse(null);
            int count = Math.min(128, nativeHandler == null ? slots.get(type).getSize() : nativeHandler.getSlots());
            boolean cosmetic = profile.cosmetics() && slots.get(type).hasCosmetic();
            for (int index = 0; index < count; index++) {
                for (int appearance = 0; appearance < (cosmetic ? 2 : 1); appearance++) {
                    var key = new CuriosSlotKey(type, index, appearance == 1);
                    var occupied = occupiedNative.remove(key.slotId());
                    if (occupied != null) targetSlots.put(key.slotId(), occupied);
                    else if (targetSlots.size() + occupiedNative.size() < EquipmentAssemblyLimits.MAX_INTERFACES)
                        targetSlots.put(key.slotId(), new EquipmentSlotDefinition(key.slotId(), key.interfaceId()));
                }
            }
        }
        // Inactive occupied slots remain recoverable, without moving active filled slots ahead of empty ones.
        targetSlots.putAll(occupiedNative);
        EquipmentStructureApi.reconcileSlots(equipment, structure, java.util.List.copyOf(targetSlots.values()));
        PREPARED.put(equipment, new Prepared(EquipmentStructureApi.structure(equipment).orElseThrow(), config,
                actor.getId(), actor.level().getGameTime()));
    }
    static void clear() { PREPARED.clear(); }
    public static boolean active(ItemStack equipment, LivingEntity actor, CuriosSlotKey key) {
        var config = CuriosArmorCompat.definitions(actor);
        var profile = config.profile(key.type());
        var type = CuriosApi.getEntitySlots(actor).get(key.type());
        if (!CuriosArmorCompat.managesType(actor, key.type()) || type == null
                || config.excluded().contains(BuiltInRegistries.ITEM.getKey(equipment.getItem()))
                || key.cosmetic() && (!profile.cosmetics() || !type.hasCosmetic())) return false;
        var structure = EquipmentStructureApi.structure(equipment).orElse(null);
        if (structure == null || position(equipment) != profile.armorSlot()
                || profile.template().filter(id -> !id.equals(structure.hostId())).isPresent()) return false;
        int count = CuriosApi.getCuriosInventory(actor).flatMap(inv -> inv.getStacksHandler(key.type()))
                .map(handler -> handler.getStacks().getSlots()).orElse(type.getSize());
        return key.index() < Math.min(128, count);
    }
}
