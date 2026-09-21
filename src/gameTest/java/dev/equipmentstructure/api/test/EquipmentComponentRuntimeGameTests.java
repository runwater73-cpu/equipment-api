package dev.equipmentstructure.api.test;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.appearance.AppearancePose;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder(EquipmentStructureApiMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class EquipmentComponentRuntimeGameTests {
    private static final ResourceLocation SLOT = id("primary");
    private static final ResourceLocation SECOND = id("secondary");
    private static final ResourceLocation TYPE = id("type");
    private static final List<EquipmentSlotDefinition> SLOTS = List.of(
            EquipmentSlotDefinition.of(SLOT, TYPE), EquipmentSlotDefinition.of(SECOND, TYPE));

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) { event.register(EquipmentComponentRuntimeGameTests.class); }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void automaticDispatchAndLegacyCallsShareOneTick(GameTestHelper helper) {
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        List<String> events = new ArrayList<>();
        try (var part = part("dedup", recorder(events))) {
            wearer.setItemSlot(EquipmentSlot.MAINHAND, equipment(part));
            pulse(wearer);
            EquipmentStructureApi.tickComponents(wearer.getMainHandItem(), wearer, EquipmentSlot.MAINHAND);
            pulse(wearer);
            helper.assertTrue(events.equals(List.of("start:mainhand", "tick:mainhand")), "auto and explicit dispatch share ledger");
            wearer.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            pulse(wearer);
            pulse(wearer);
            helper.assertTrue(events.equals(List.of("start:mainhand", "tick:mainhand", "stop:mainhand")), "unequip stops once even within same tick");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void inventoryItemsStayInactiveAndLocationsAreIndependent(GameTestHelper helper) {
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        List<String> events = new ArrayList<>();
        try (var part = part("locations", recorder(events))) {
            wearer.getInventory().setItem(12, equipment(part));
            pulse(wearer);
            helper.assertTrue(events.isEmpty(), "backpack item is not active");
            for (EquipmentSlot slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) wearer.setItemSlot(slot, equipment(part));
            pulse(wearer);
            helper.assertTrue(events.stream().filter(e -> e.startsWith("start:")).count() == 6
                    && events.stream().filter(e -> e.startsWith("tick:")).count() == 6, "six independent player equipment locations");
            wearer.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            pulse(wearer);
            helper.assertTrue(events.stream().filter(e -> e.startsWith("stop:")).toList().equals(List.of("stop:head")), "only removed slot deactivates");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void movingSameHostStopsOldLocationBeforeStartingNewOne(GameTestHelper helper) {
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        List<String> events = new ArrayList<>();
        try (var part = part("move", recorder(events))) {
            wearer.setItemSlot(EquipmentSlot.OFFHAND, equipment(part));
            pulse(wearer);
            ItemStack moving = wearer.getOffhandItem();
            events.clear();
            wearer.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            wearer.setItemSlot(EquipmentSlot.MAINHAND, moving);
            EquipmentStructureApi.tickComponents(wearer.getMainHandItem(), wearer, EquipmentSlot.MAINHAND);
            helper.assertTrue(events.size() >= 2 && events.subList(0, 2).equals(List.of("stop:offhand", "start:mainhand")),
                    "all stops precede starts across locations");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void identicalHostReplacementReactivatesButDataAndPoseChangesDoNot(GameTestHelper helper) {
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        List<String> events = new ArrayList<>();
        try (var part = part("changes", recorder(events))) {
            wearer.setItemSlot(EquipmentSlot.MAINHAND, equipment(part));
            pulse(wearer);
            events.clear();
            ItemStack live = wearer.getMainHandItem();
            var instance = EquipmentStructureApi.component(live, SLOT).orElseThrow();
            EquipmentStructureApi.updateComponentData(live, SLOT, instance, data -> { data.putInt("charge", 5); return data; });
            // Saved data can change without being confused with a different item.
            pulse(wearer);
            EquipmentStructureApi.setAppearancePose(live, SLOT, AppearancePose.IDENTITY);
            live.setDamageValue(3);
            pulse(wearer);
            helper.assertTrue(events.isEmpty(), "data, pose and durability do not cause lifecycle churn");
            wearer.setItemSlot(EquipmentSlot.MAINHAND, live.copy());
            pulse(wearer);
            helper.assertTrue(events.equals(List.of("stop:mainhand", "start:mainhand")), "equal but distinct host reactivates without double tick");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void activationPredicateTransitionsAndFailuresAreSafe(GameTestHelper helper) {
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        AtomicBoolean active = new AtomicBoolean(false);
        AtomicBoolean faulty = new AtomicBoolean(false);
        List<String> events = new ArrayList<>();
        try (var part = part("predicate", new EquipmentComponentBehavior() {
            @Override public boolean isActive(EquipmentComponentContext ctx) {
                if (faulty.get()) throw new IllegalStateException("expected activation failure");
                ctx.equipment().shrink(1);
                return active.get() && ctx.equipmentSlot().orElseThrow() == EquipmentSlot.HEAD;
            }
            @Override public void onActivated(EquipmentComponentContext ctx) { events.add("start"); }
            @Override public void onDeactivated(EquipmentComponentContext ctx) { events.add("stop"); }
            @Override public void tick(EquipmentComponentContext ctx) { events.add("tick"); }
        })) {
            wearer.setItemSlot(EquipmentSlot.HEAD, equipment(part));
            wearer.setItemSlot(EquipmentSlot.MAINHAND, equipment(part));
            pulse(wearer);
            helper.assertTrue(events.isEmpty(), "inactive behavior does nothing");
            active.set(true);
            pulse(wearer);
            helper.assertTrue(events.equals(List.of("start")), "only allowed location activates, tick remains deduplicated");
            faulty.set(true);
            pulse(wearer);
            pulse(wearer);
            helper.assertTrue(events.equals(List.of("start", "stop")), "predicate failure deactivates once");
            helper.assertTrue(wearer.getItemBySlot(EquipmentSlot.HEAD).getCount() == 1, "predicate receives host copy");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void unregisterAndReplacementUseOriginalBehaviorForCleanup(GameTestHelper helper) {
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        List<String> events = new ArrayList<>();
        try (var part = part("unregister", new EquipmentComponentBehavior() {
            @Override public void onActivated(EquipmentComponentContext ctx) { events.add("old_start"); }
            @Override public void onDeactivated(EquipmentComponentContext ctx) { events.add("old_stop"); }
        })) {
            wearer.setItemSlot(EquipmentSlot.HEAD, equipment(part));
            pulse(wearer);
            EquipmentComponentBehaviorRegistry.unregister(part.id());
            EquipmentComponentBehaviorRegistry.register(part.id(), new EquipmentComponentBehavior() {
                @Override public void onActivated(EquipmentComponentContext ctx) { events.add("new_start"); }
                @Override public void onDeactivated(EquipmentComponentContext ctx) { events.add("new_stop"); }
            });
            pulse(wearer);
            EquipmentComponentBehaviorRegistry.unregister(part.id());
            pulse(wearer);
            pulse(wearer);
            helper.assertTrue(events.equals(List.of("old_start", "old_stop", "new_start", "new_stop")), "cleanup uses captured original registration");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void logoutLeaveRejoinAndDeathCleanUpExactlyOnce(GameTestHelper helper) {
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        List<String> events = new ArrayList<>();
        try (var part = part("departure", recorder(events))) {
            wearer.setItemSlot(EquipmentSlot.HEAD, equipment(part));
            pulse(wearer);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(wearer));
            NeoForge.EVENT_BUS.post(new EntityLeaveLevelEvent(wearer, helper.getLevel()));
            pulse(wearer);
            helper.assertTrue(events.equals(List.of("start:head", "tick:head", "stop:head")), "logout/leave never double clean or restart");
            NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(wearer, helper.getLevel()));
            pulse(wearer);
            helper.assertTrue(events.getLast().equals("start:head") && events.size() == 4, "same entity may reactivate after dimension rejoin");
            wearer.setHealth(0);
            pulse(wearer);
            pulse(wearer);
            helper.assertTrue(events.getLast().equals("stop:head") && events.size() == 5, "dead wearer cleans once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void deathCloneStartsWithFreshRuntimeAndRetainsSavedEquipment(GameTestHelper helper) {
        var old = helper.makeMockPlayer(GameType.SURVIVAL);
        var respawned = helper.makeMockPlayer(GameType.SURVIVAL);
        List<String> events = new ArrayList<>();
        try (var part = part("clone", recorder(events))) {
            old.setItemSlot(EquipmentSlot.HEAD, equipment(part));
            pulse(old);
            ItemStack saved = ItemStack.parse(helper.getLevel().registryAccess(), old.getItemBySlot(EquipmentSlot.HEAD)
                    .save(helper.getLevel().registryAccess())).orElseThrow();
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned, old, true));
            respawned.setItemSlot(EquipmentSlot.HEAD, saved);
            pulse(respawned);
            helper.assertTrue(events.equals(List.of("start:head", "tick:head", "stop:head", "start:head", "tick:head")),
                    "respawn lifecycle has a fresh ledger");
            helper.assertTrue(EquipmentStructureApi.component(saved, SLOT).isPresent(), "lifecycle never deletes installed part");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void activationCanUnequipHostWithoutTickingOrRecursing(GameTestHelper helper) {
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        List<String> events = new ArrayList<>();
        try (var part = part("mutation", new EquipmentComponentBehavior() {
            @Override public void onActivated(EquipmentComponentContext ctx) {
                events.add("start");
                pulse(wearer);
                wearer.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            }
            @Override public void onDeactivated(EquipmentComponentContext ctx) {
                events.add("stop");
                helper.assertTrue(EquipmentStructureApi.component(ctx.equipment(), SLOT).isPresent(), "cleanup has old snapshot");
                pulse(wearer);
                throw new IllegalStateException("expected cleanup error");
            }
            @Override public void tick(EquipmentComponentContext ctx) { events.add("wrong_tick"); }
        })) {
            wearer.setItemSlot(EquipmentSlot.HEAD, equipment(part));
            pulse(wearer);
            pulse(wearer);
            helper.assertTrue(events.equals(List.of("start", "stop")), "bounded reentrancy and no stale tick");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void wornPartRemovalStopsBehaviorButDoesNotCountAsUnequippingHost(GameTestHelper helper) {
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        List<String> events = new ArrayList<>();
        AtomicInteger assemblyRemoved = new AtomicInteger();
        try (var part = part("remove", new EquipmentComponentBehavior() {
            @Override public void onActivated(EquipmentComponentContext ctx) { events.add("start:" + ctx.slot().id().getPath()); }
            @Override public void onDeactivated(EquipmentComponentContext ctx) { events.add("stop:" + ctx.slot().id().getPath()); }
            @Override public void onRemoved(EquipmentComponentContext ctx) { assemblyRemoved.incrementAndGet(); }
        })) {
            ItemStack stack = equipment(part);
            EquipmentStructureApi.install(stack, SECOND, part.instance());
            wearer.setItemSlot(EquipmentSlot.HEAD, stack);
            pulse(wearer);
            events.clear();
            EquipmentStructureApi.remove(stack, SLOT);
            pulse(wearer);
            helper.assertTrue(events.equals(List.of("stop:" + SLOT.getPath())) && assemblyRemoved.get() == 1, "only removed part stops");
            wearer.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            pulse(wearer);
            helper.assertTrue(events.getLast().equals("stop:" + SECOND.getPath()) && assemblyRemoved.get() == 1,
                    "unequip is not assembly removal");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void realEntityTicksAutomaticallyAndPersistsChargeAcrossTicks(GameTestHelper helper) {
        var wearer = helper.spawn(EntityType.ARMOR_STAND, 1, 2, 1);
        AtomicInteger started = new AtomicInteger();
        AtomicInteger stopped = new AtomicInteger();
        AtomicInteger ticked = new AtomicInteger();
        var part = part("real_tick", new EquipmentComponentBehavior() {
            @Override public void onActivated(EquipmentComponentContext ctx) { started.incrementAndGet(); }
            @Override public void onDeactivated(EquipmentComponentContext ctx) { stopped.incrementAndGet(); }
            @Override public void tick(EquipmentComponentContext ctx) {
                ticked.incrementAndGet();
                var live = ctx.wearer().orElseThrow().getItemBySlot(ctx.equipmentSlot().orElseThrow());
                EquipmentStructureApi.updateComponentData(live, ctx.slot().id(), ctx.component(), data -> {
                    data.putInt("charge", data.getInt("charge") + 1); return data;
                });
                // Simulate an older compatibility mod still calling the explicit entry.
                EquipmentStructureApi.tickComponents(live, wearer, EquipmentSlot.HEAD);
            }
        });
        wearer.setItemSlot(EquipmentSlot.HEAD, equipment(part));
        helper.runAfterDelay(6, () -> {
            try {
                helper.assertTrue(started.get() == 1 && ticked.get() >= 4 && stopped.get() == 0,
                        "registered behavior is automatically driven every real entity tick");
                helper.assertTrue(EquipmentStructureApi.component(wearer.getItemBySlot(EquipmentSlot.HEAD), SLOT)
                        .orElseThrow().data().getInt("charge") == ticked.get(), "one persisted data update per effect tick");
                wearer.discard();
                helper.assertTrue(stopped.get() == 1, "real entity departure cleans immediately");
                helper.succeed();
            } finally {
                wearer.discard();
                part.close();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void animalBodyEquipmentUsesItsOwnLocation(GameTestHelper helper) {
        var horse = helper.spawn(EntityType.HORSE, 1, 2, 1);
        List<String> events = new ArrayList<>();
        try (var part = part("body", recorder(events))) {
            horse.setItemSlot(EquipmentSlot.BODY, equipment(part));
            pulse(horse);
            helper.assertTrue(events.equals(List.of("start:body", "tick:body")), "animal body equipment is supported");
            horse.setItemSlot(EquipmentSlot.BODY, ItemStack.EMPTY);
            pulse(horse);
            helper.assertTrue(events.getLast().equals("stop:body"), "animal unequip cleans up");
        } finally { horse.discard(); }
        helper.succeed();
    }

    private static EquipmentComponentBehavior recorder(List<String> events) {
        return new EquipmentComponentBehavior() {
            @Override public void onActivated(EquipmentComponentContext ctx) { events.add("start:" + ctx.equipmentSlot().orElseThrow().getName()); }
            @Override public void onDeactivated(EquipmentComponentContext ctx) { events.add("stop:" + ctx.equipmentSlot().orElseThrow().getName()); }
            @Override public void tick(EquipmentComponentContext ctx) { events.add("tick:" + ctx.equipmentSlot().orElseThrow().getName()); }
        };
    }

    private static void pulse(LivingEntity wearer) { NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(wearer)); }

    private static ItemStack equipment(Part part) {
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        EquipmentStructureApi.setStructure(stack, new EquipmentStructure(
                id("host"), BuiltinEquipmentTypes.SWORD, SLOTS, Map.of(SLOT, List.of(part.instance()))));
        return stack;
    }

    private static Part part(String name, EquipmentComponentBehavior behavior) {
        var definition = GameTestFixtures.component(id(name), TYPE, TYPE);
        EquipmentComponentRegistry.register(definition);
        EquipmentComponentBehaviorRegistry.register(definition.id(), behavior);
        return new Part(definition.id(), definition.createInstance());
    }

    private record Part(ResourceLocation id, EquipmentComponentInstance instance) implements AutoCloseable {
        @Override public void close() {
            EquipmentComponentBehaviorRegistry.unregister(id);
            EquipmentComponentRegistry.unregister(id);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "runtime_test/" + path);
    }
}
