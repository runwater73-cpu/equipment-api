package dev.equipmentstructure.api.event;

import dev.equipmentstructure.api.grid.synergy.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import java.util.Map;

/** Server notification after a successful structure commit. Does not imply the equipment is worn. */
public final class EquipmentGridRuleChangedEvent extends Event {
    private final ItemStack equipment;
    private final Map<GridRuleRegistry.Key, GridRuleResult> previous, current;
    public EquipmentGridRuleChangedEvent(ItemStack equipment, Map<GridRuleRegistry.Key, GridRuleResult> previous, Map<GridRuleRegistry.Key, GridRuleResult> current) {
        this.equipment = equipment.copy(); this.previous = Map.copyOf(previous); this.current = Map.copyOf(current);
    }
    public ItemStack equipment() { return equipment.copy(); }
    public Map<GridRuleRegistry.Key, GridRuleResult> previous() { return previous; }
    public Map<GridRuleRegistry.Key, GridRuleResult> current() { return current; }
}
