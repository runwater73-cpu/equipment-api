package dev.equipmentstructure.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Internal server dispatcher. State belongs to each entity, never to a saved ItemStack. */
public final class EquipmentComponentRuntime {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, EquipmentStructureApiMod.MOD_ID);
    // Deliberately neither serialized, copied on respawn, nor synchronized to clients.
    private static final Supplier<AttachmentType<State>> STATE = ATTACHMENTS.register(
            "component_runtime", () -> AttachmentType.builder(State::new).build());

    private EquipmentComponentRuntime() {}

    static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
        NeoForge.EVENT_BUS.register(EquipmentComponentRuntime.class);
    }

    @SubscribeEvent
    public static void entityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity wearer) tick(wearer, EnumSet.allOf(EquipmentSlot.class));
    }

    @SubscribeEvent
    public static void entityLeaves(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity wearer) suspend(wearer);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { suspend(event.getEntity()); }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) { suspend(event.getOriginal()); }

    @SubscribeEvent
    public static void entityJoins(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity wearer && !event.getLevel().isClientSide()) {
            wearer.getExistingData(STATE).ifPresent(state -> state.suspended = false);
        }
    }

    static void tick(LivingEntity wearer, Set<EquipmentSlot> tickLocations) {
        if (wearer.level().isClientSide()) return;
        State state = wearer.getExistingData(STATE).orElse(null);
        if (state != null && (state.running || state.suspended)) return;
        if (state == null) {
            if (!usable(wearer) || EquipmentComponentBehaviorRegistry.isEmpty() || !hasStructuredHost(wearer)) return;
            state = wearer.getData(STATE);
        }
        state.running = true;
        try {
            if (!usable(wearer)) {
                drain(wearer, state);
                return;
            }
            // Reconcile all locations, including calls that tick a single location:
            // removals must run before activations when moving a host between hands.
            Map<Key, Entry> observed = observeAll(wearer);
            for (var old : new ArrayList<>(state.entries.entrySet())) {
                Entry next = observed.get(old.getKey());
                if (next == null || !old.getValue().sameActivation(next)) {
                    deactivate(wearer, state, old.getKey());
                }
            }
            for (var candidate : observed.entrySet()) {
                if (state.suspended || !usable(wearer)) break;
                Key key = candidate.getKey();
                Entry next = observe(wearer, key);
                if (state.suspended) break;
                // Callbacks may have installed a new part; defer that new observation.
                if (next == null || !candidate.getValue().sameSnapshot(next)) continue;
                Entry previous = state.entries.put(key, next);
                if (previous == null) EquipmentComponentBehaviorRegistry.activated(next.context(wearer, key), next.behavior());
            }

            long gameTime = wearer.level().getGameTime();
            Set<EquipmentSlot> eligible = EnumSet.noneOf(EquipmentSlot.class);
            for (EquipmentSlot location : tickLocations) {
                if (!Long.valueOf(gameTime).equals(state.lastTicks.put(location, gameTime))) eligible.add(location);
            }
            for (var scheduled : new ArrayList<>(state.entries.entrySet())) {
                if (state.suspended || !usable(wearer)) break;
                Key key = scheduled.getKey();
                if (!eligible.contains(key.location())) continue;
                Entry latest = observe(wearer, key);
                if (state.suspended) break;
                if (latest == null || !scheduled.getValue().sameSnapshot(latest)) continue;
                state.entries.put(key, latest);
                EquipmentComponentBehaviorRegistry.tick(latest.context(wearer, key), latest.behavior());
            }
            // Bound callback-driven work: prune stale entries now, activate new ones
            // next pass. Never spin until arbitrary user callbacks stop changing state.
            prune(wearer, state);
        } finally {
            try {
                if (state.suspended || !usable(wearer)) drain(wearer, state);
            } finally {
                state.running = false;
            }
        }
    }

    private static Map<Key, Entry> observeAll(LivingEntity wearer) {
        Map<Key, Entry> result = new LinkedHashMap<>();
        for (EquipmentSlot location : EquipmentSlot.values()) {
            ItemStack host = wearer.getItemBySlot(location);
            if (host.isEmpty()) continue;
            var structure = EquipmentStructureApi.structure(host).orElse(null);
            if (structure == null) continue;
            for (var slot : structure.slots()) {
                Key key = new Key(location, slot.id());
                Entry entry = observe(wearer, key);
                if (entry != null) result.put(key, entry);
            }
        }
        return result;
    }

    private static Entry observe(LivingEntity wearer, Key key) {
        if (!usable(wearer)) return null;
        ItemStack host = wearer.getItemBySlot(key.location());
        if (host.isEmpty()) return null;
        var structure = EquipmentStructureApi.structure(host).orElse(null);
        if (structure == null) return null;
        var slot = structure.slot(key.slotId()).orElse(null);
        var part = structure.component(key.slotId()).orElse(null);
        if (slot == null || part == null) return null;
        var behavior = EquipmentComponentBehaviorRegistry.get(part.id()).orElse(null);
        if (behavior == null) return null;
        Entry candidate = new Entry(host, host.copy(), structure, slot, part, behavior);
        if (!EquipmentComponentBehaviorRegistry.active(candidate.context(wearer, key), behavior)) return null;
        // Pure predicates cannot write through the context, but captured live objects
        // still need a conflict check before any side-effecting callback runs.
        if (wearer.getItemBySlot(key.location()) != host || !usable(wearer)
                || EquipmentStructureApi.structure(host).orElse(null) != structure
                || EquipmentComponentBehaviorRegistry.get(part.id()).orElse(null) != behavior) return null;
        return candidate;
    }

    private static void prune(LivingEntity wearer, State state) {
        for (var active : new ArrayList<>(state.entries.entrySet())) {
            Entry latest = state.suspended ? null : observe(wearer, active.getKey());
            if (latest == null || !active.getValue().sameActivation(latest)) deactivate(wearer, state, active.getKey());
            else state.entries.put(active.getKey(), latest);
        }
    }

    private static void suspend(LivingEntity wearer) {
        if (wearer.level().isClientSide()) return;
        var state = wearer.getExistingData(STATE).orElse(null);
        if (state == null) return;
        state.suspended = true;
        if (state.running) return; // The current dispatch drains in finally.
        state.running = true;
        try { drain(wearer, state); }
        finally { state.running = false; }
    }

    private static void drain(LivingEntity wearer, State state) {
        for (Key key : new ArrayList<>(state.entries.keySet())) deactivate(wearer, state, key);
    }

    private static void deactivate(LivingEntity wearer, State state, Key key) {
        Entry old = state.entries.remove(key);
        if (old != null) EquipmentComponentBehaviorRegistry.deactivated(old.context(wearer, key), old.behavior());
    }

    private static boolean usable(LivingEntity wearer) { return wearer.isAlive() && !wearer.isRemoved(); }

    private static boolean hasStructuredHost(LivingEntity wearer) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = wearer.getItemBySlot(slot);
            if (!stack.isEmpty() && EquipmentStructureApi.hasStructure(stack)) return true;
        }
        return false;
    }

    private record Key(EquipmentSlot location, ResourceLocation slotId) {}

    /** No entity/world/context references or serialized state. */
    private static final class State {
        private final Map<Key, Entry> entries = new LinkedHashMap<>();
        private final EnumMap<EquipmentSlot, Long> lastTicks = new EnumMap<>(EquipmentSlot.class);
        private boolean running;
        private boolean suspended;
    }

    private record Entry(ItemStack liveHost, ItemStack snapshot, EquipmentStructure structure,
                         EquipmentSlotDefinition slot, EquipmentComponentInstance component,
                         EquipmentComponentBehavior behavior) {
        private boolean sameActivation(Entry other) {
            return liveHost == other.liveHost && behavior == other.behavior
                    && structure.hostId().equals(other.structure.hostId()) && slot.equals(other.slot)
                    && component.id().equals(other.component.id())
                    && component.componentType().equals(other.component.componentType())
                    && component.interfaceType().equals(other.component.interfaceType());
        }

        private boolean sameSnapshot(Entry other) { return sameActivation(other) && component == other.component; }

        private EquipmentComponentContext context(LivingEntity wearer, Key key) {
            return new EquipmentComponentContext(snapshot, structure, slot, component,
                    Optional.of(wearer), Optional.of(key.location()));
        }
    }
}
