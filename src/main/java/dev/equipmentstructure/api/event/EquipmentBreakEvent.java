package dev.equipmentstructure.api.event;

import dev.equipmentstructure.api.EquipmentStructure;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import java.util.HashMap;
import java.util.Map;

/** Server-side durability-break policy. Manual removal/binding restrictions do not apply to destruction. */
public final class EquipmentBreakEvent extends Event implements ICancellableEvent {
    public enum Action { RETURN, DESTROY }
    private final ItemStack equipment;
    private final EquipmentStructure structure;
    private final LivingEntity wearer;
    private final Map<ResourceLocation, Action> actions = new HashMap<>();
    public EquipmentBreakEvent(ItemStack equipment, EquipmentStructure structure, LivingEntity wearer) {
        this.equipment = equipment; this.structure = structure; this.wearer = wearer;
    }
    public ItemStack equipment() { return equipment; }
    public EquipmentStructure structure() { return structure; }
    /** May be null for programmatic durability damage without an owning entity. */
    public LivingEntity wearer() { return wearer; }
    public Action action(ResourceLocation slot) { return actions.getOrDefault(slot, Action.RETURN); }
    public void setAction(ResourceLocation slot, Action action) {
        if (!structure.components().containsKey(slot)) throw new IllegalArgumentException("No installed component in " + slot);
        actions.put(slot, java.util.Objects.requireNonNull(action));
    }
}
