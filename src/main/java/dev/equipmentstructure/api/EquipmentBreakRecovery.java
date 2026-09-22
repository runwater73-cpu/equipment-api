package dev.equipmentstructure.api;

import dev.equipmentstructure.api.event.EquipmentBreakEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import java.util.ArrayList;

/** Prepares the entire return before destroying ownership; never partially salvages a broken host. */
public final class EquipmentBreakRecovery {
    private EquipmentBreakRecovery() {}
    public static boolean beforeBreak(ItemStack equipment, LivingEntity wearer) {
        if (!EquipmentSlotItemAdapters.beforeBreak(equipment, wearer)) return false;
        var structure = EquipmentStructureApi.structure(equipment).orElse(null);
        if (structure == null || structure.components().isEmpty()) return true;
        var event = new EquipmentBreakEvent(equipment, structure, wearer);
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) return false;
        var returned = new ArrayList<ItemStack>();
        for (var entry : structure.components().entrySet()) {
            if (event.action(entry.getKey()) == EquipmentBreakEvent.Action.DESTROY) continue;
            var item = EquipmentComponentRegistry.createValidatedItemStack(entry.getValue().getFirst());
            if (item.isEmpty()) return false; // Missing/lossy factory must never delete an installed component.
            returned.add(item.get());
        }
        if (!returned.isEmpty() && (wearer == null || wearer.level().isClientSide())) return false;
        if (EquipmentStructureApi.structure(equipment).orElse(null) != structure) return false;
        // Binding and canRemove are installation rules. A physical break uses this dedicated policy event.
        EquipmentStructureApi.clearStructure(equipment);
        for (var item : returned) {
            if (wearer instanceof Player player) player.getInventory().placeItemBackInInventory(item);
            else wearer.spawnAtLocation(item);
        }
        return true;
    }
}
