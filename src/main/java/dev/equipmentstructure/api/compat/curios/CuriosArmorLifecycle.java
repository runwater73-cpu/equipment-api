package dev.equipmentstructure.api.compat.curios;

import dev.equipmentstructure.api.EquipmentStructureApi;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio.DropRule;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import java.util.*;
import java.util.function.*;

/** Ownership adaptations around the native death algorithm; Curios still creates and publishes drops. */
public final class CuriosArmorLifecycle {
    private CuriosArmorLifecycle() {}
    private static void handlers(LivingEntity entity, Consumer<ArmorCurioStackHandler> operation) {
        if (!(entity instanceof Player) || entity.level().isClientSide()) return;
        CuriosApi.getCuriosInventory(entity).ifPresent(inv -> inv.getCurios().values().forEach(h -> {
            if (h.getStacks() instanceof ArmorCurioStackHandler s) operation.accept(s);
            if (h.getCosmeticStacks() instanceof ArmorCurioStackHandler s) operation.accept(s);
        }));
    }
    public static void freeze(LivingEntity entity) { handlers(entity, ArmorCurioStackHandler::freeze); }
    public static void thaw(LivingEntity entity) { handlers(entity, ArmorCurioStackHandler::thaw); }

    public static List<Tuple<Predicate<ItemStack>, DropRule>> deathRules(String type, LivingEntity entity,
            List<Tuple<Predicate<ItemStack>, DropRule>> rules, IDynamicStackHandler inventory,
            boolean cosmetic, boolean keepInventory, LivingDropsEvent event) {
        if (!(inventory instanceof ArmorCurioStackHandler armor)) return rules;
        var adjusted = new ArrayList<>(rules);
        for (int i = 0; i < inventory.getSlots(); i++) {
            var item = inventory.getStackInSlot(i);
            if (item.isEmpty() || !armor.owns(i)) continue;
            var owner = armor.boundOwner(i);
            // Vanilla drops use the original stack. Other drop handlers may replace it with an exact copy.
            for (var drop : event.getDrops()) if (ItemStack.matches(owner, drop.getItem())) {
                armor.rebindDroppedOwner(i, drop.getItem()); break;
            }
            var context = new SlotContext(type, entity, i, cosmetic, true);
            DropRule rule = null;
            for (var override : rules) if (override.getA().test(item)) rule = override.getB();
            if (rule == null) rule = CuriosApi.getCurio(item)
                    .map(curio -> curio.getDropRule(context, event.getSource(), event.isRecentlyHit())).orElse(DropRule.DEFAULT);
            if (rule == DropRule.DEFAULT) rule = CuriosApi.getSlot(type, entity.level())
                    .map(slot -> slot.getDropRule()).orElse(DropRule.DEFAULT);
            if (rule == DropRule.ALWAYS_KEEP || rule == DropRule.DEFAULT && keepInventory) {
                armor.retainAfterDeath(i);
                adjusted.add(new Tuple<>(candidate -> candidate == item, DropRule.ALWAYS_KEEP));
            } else if (rule == DropRule.DEFAULT
                    && !EnchantmentHelper.has(item, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                // Default items stay nested in the armor drop; never duplicate them as loose Curios drops.
                adjusted.add(new Tuple<>(candidate -> candidate == item, DropRule.ALWAYS_KEEP));
            } else {
                // The native algorithm receives the already resolved rule; third-party callbacks run once.
                adjusted.add(new Tuple<>(candidate -> candidate == item, rule));
            }
        }
        return adjusted;
    }

}
