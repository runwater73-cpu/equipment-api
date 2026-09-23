package dev.equipmentstructure.api.compat.curios;

import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import net.minecraft.core.registries.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import java.util.*;

/** Player-owned permanent bindings; no armor, synthetic equipment or mirrored item storage. */
public final class CuriosBindingActions {
    private CuriosBindingActions() {}
    public static List<CuriosSlotKey> keys(Player player) {
        if (!CuriosArmorCompat.definitions(player).enabled()) return List.of();
        var inventory = CuriosApi.getCuriosInventory(player).orElse(null);
        if (inventory == null) return List.of();
        var types = new HashSet<String>();
        CuriosArmorCompat.definitions(player).playerBoundItems().forEach((id, enabled) -> {
            if (enabled && BuiltInRegistries.ITEM.containsKey(id))
                types.addAll(CuriosApi.getItemStackSlots(new ItemStack(BuiltInRegistries.ITEM.get(id)), player.level()).keySet());
        });
        var result = new ArrayList<CuriosSlotKey>();
        inventory.getCurios().entrySet().stream().sorted(Map.Entry.comparingByKey(CuriosArmorCompat.slotOrder(player))).forEach(entry -> {
            if (!CuriosArmorCompat.managesType(player, entry.getKey())) return;
            for (int i = 0; i < Math.min(128, entry.getValue().getSlots()) && result.size() < 256; i++) {
                var key = new CuriosSlotKey(entry.getKey(), i, false);
                var item = entry.getValue().getStacks().getStackInSlot(i);
                // A shared native index has one owner. Armor-owned items belong in the equipment
                // panel, even when their type also accepts a declared player binding.
                if (PlayerBoundCurios.bound(item, player)
                        || item.isEmpty() && types.contains(entry.getKey()) && PlayerBoundCurios.stableSlot(player, key))
                    result.add(key);
            }
        });
        return List.copyOf(result);
    }
    public static boolean act(EquipmentAssemblyMenu menu, CuriosBindingActionPayload request, Player actor) {
        if (actor.level().isClientSide() || actor.containerMenu != menu || !menu.stillValid(actor)
                || request.containerId() != menu.containerId || request.stateId() != menu.getStateId()
                || !ItemStack.matches(menu.getCarried(), request.expectedCarried()) || !keys(actor).contains(request.key())) return false;
        if (menu.getCarried().isEmpty()) {
            var removed = PlayerBoundCurios.remove(actor, request.key().slotId());
            if (removed.isEmpty()) return false;
            if (request.quick()) actor.getInventory().placeItemBackInInventory(removed); else menu.setCarried(removed);
        } else {
            if (!PlayerBoundCurios.install(actor, request.key(), menu.getCarried())) return false;
            var remaining = menu.getCarried().copy(); remaining.shrink(1); menu.setCarried(remaining);
            notifyMenuEquip(actor, request.key());
        }
        menu.broadcastChanges(); return true;
    }
    public static boolean quickInstall(Player actor, ItemStack input) {
        if (actor.level().isClientSide() || !PlayerBoundCurios.eligible(input, actor)) return false;
        for (var key : keys(actor)) if (PlayerBoundCurios.install(actor, key, input)) {
            input.shrink(1); notifyMenuEquip(actor, key); return true;
        }
        return false;
    }
    private static void notifyMenuEquip(Player actor, CuriosSlotKey key) {
        CuriosApi.getCuriosInventory(actor).flatMap(inv -> inv.getStacksHandler(key.type()))
                .filter(h -> key.index() < h.getActiveStates().size() && h.getActiveStates().get(key.index()))
                .ifPresent(h -> CuriosApi.getCurio(h.getStacks().getStackInSlot(key.index()))
                        .ifPresent(curio -> curio.onEquipFromUse(CuriosArmorCompat.context(actor, key))));
    }
}
