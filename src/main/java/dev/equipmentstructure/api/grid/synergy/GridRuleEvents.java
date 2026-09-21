package dev.equipmentstructure.api.grid.synergy;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.event.*;
import dev.equipmentstructure.api.grid.GridDefinitions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class GridRuleEvents {
    private static final ThreadLocal<Boolean> RUNNING = ThreadLocal.withInitial(() -> false);
    private GridRuleEvents() {}
    @SubscribeEvent public static void changed(EquipmentStructureChangedEvent event) {
        if (EffectiveSide.get().isClient() || RUNNING.get() || event.changeType() == EquipmentStructureChangedEvent.ChangeType.APPEARANCE_UPDATED) return;
        var definitions = GridDefinitions.registered(); if (definitions.rules().isEmpty()) return;
        RUNNING.set(true);
        try {
            var before = GridRuleRegistry.evaluate(event.previous(), definitions, false);
            var after = GridRuleRegistry.evaluate(event.current(), definitions, false);
            if (!before.equals(after)) {
                var keys = new java.util.HashSet<>(before.keySet()); keys.addAll(after.keySet());
                for (var key : keys) if (!java.util.Objects.equals(before.get(key), after.get(key)))
                    EquipmentStructureApiMod.LOGGER.info("[Grid rule] rule={} source={} result={}", key.rule(), key.source(), after.getOrDefault(key, GridRuleResult.unknown("source_removed")));
                NeoForge.EVENT_BUS.post(new EquipmentGridRuleChangedEvent(event.stack(), before, after));
            }
        } finally { RUNNING.set(false); }
    }
}
