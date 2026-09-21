package dev.equipmentstructure.api.test;

import dev.equipmentstructure.api.EquipmentComponentDefinition;
import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentComponentItem;
import dev.equipmentstructure.api.EquipmentComponentItemAdapters;
import dev.equipmentstructure.api.EquipmentComponentRegistry;
import dev.equipmentstructure.api.BuiltinEquipmentTypes;
import dev.equipmentstructure.api.EquipmentHostDefinition;
import dev.equipmentstructure.api.EquipmentHostProviders;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.event.EquipmentStructureInstallEvent;
import dev.equipmentstructure.api.event.EquipmentStructureRemoveEvent;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Existing vanilla items intentionally do not implement EquipmentComponentItem. Development-only. */
@GameTestHolder(EquipmentStructureApiMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class EquipmentComponentItemAdapterGameTests {
    private static final ResourceLocation TYPE = id("type");
    private static final ResourceLocation SLOT = id("primary");
    private static final ResourceLocation SECOND = id("secondary");
    private static final List<EquipmentSlotDefinition> SLOTS = List.of(
            EquipmentSlotDefinition.of(SLOT, TYPE), EquipmentSlotDefinition.of(SECOND, TYPE));

    private EquipmentComponentItemAdapterGameTests() {}

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(EquipmentComponentItemAdapterGameTests.class);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bindingsRequireConcreteDefinitionAndReverseFactory(GameTestHelper helper) {
        expectFailure(helper, () -> EquipmentComponentItemAdapters.register(id("unknown_adapter"), Items.PAPER,
                id("unregistered"), stack -> Optional.of(new CompoundTag())));
        ResourceLocation part = id("without_factory");
        EquipmentComponentRegistry.register(GameTestFixtures.component(part, TYPE, TYPE));
        try {
            expectFailure(helper, () -> EquipmentComponentItemAdapters.register(id("no_factory_adapter"), Items.PAPER,
                    part, stack -> Optional.of(new CompoundTag())));
        } finally {
            EquipmentComponentRegistry.unregister(part);
        }
        helper.assertTrue(EquipmentComponentRegistry.fromItemStack(new ItemStack(Items.PAPER)).isEmpty(),
                "failed registration must not claim the input item");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void existingItemRoundTripPreservesNameDamageAndCustomData(GameTestHelper helper) {
        helper.assertTrue(!(Items.IRON_AXE instanceof EquipmentComponentItem), "fixture must be an ordinary item");
        try (var binding = bind("round_trip", Items.IRON_AXE, 0,
                stack -> Optional.of(saveFields(stack)), data -> restoreFields(Items.IRON_AXE, data))) {
            ItemStack source = decoratedAxe();
            var component = EquipmentComponentRegistry.fromItemStack(source).orElseThrow();
            ItemStack restored = EquipmentComponentRegistry.createItemStack(component).orElseThrow();
            helper.assertTrue(component.id().equals(binding.part()), "adapter must use its concrete registered identity");
            helper.assertTrue(ItemStack.isSameItemSameComponents(source, restored), "all input data must survive");
            helper.assertTrue(restored.getCount() == 1, "factory output must represent one physical component");
            restored.setDamageValue(100);
            helper.assertTrue(component.data().getInt("damage") == 17, "returned item cannot mutate installed data");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void priorityAndStableIdsDoNotDependOnRegistrationOrder(GameTestHelper helper) {
        for (boolean reverse : List.of(false, true)) {
            String first = reverse ? "order_a" : "order_z";
            String second = reverse ? "order_z" : "order_a";
            try (var a = plain(first, Items.PAPER, 5); var z = plain(second, Items.PAPER, 5)) {
                var result = EquipmentComponentRegistry.fromItemStack(new ItemStack(Items.PAPER)).orElseThrow();
                helper.assertTrue(result.id().equals(id("order_a")), "equal priorities use stable adapter IDs");
                try (var high = plain("order_high", Items.PAPER, 10)) {
                    helper.assertTrue(EquipmentComponentRegistry.fromItemStack(new ItemStack(Items.PAPER))
                            .orElseThrow().id().equals(high.part()), "higher priority must win");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void conditionalReadersAreIsolatedAndUnrelatedItemsAreNotScanned(GameTestHelper helper) {
        AtomicInteger calls = new AtomicInteger();
        try (var fallback = plain("copy_fallback", Items.PAPER, 0);
             var mutator = bind("copy_probe", Items.PAPER, 10, stack -> {
                 helper.assertTrue(stack.getCount() == 1, "reader must see exactly one item");
                 calls.incrementAndGet();
                 stack.set(DataComponents.CUSTOM_NAME, Component.literal("changed copy"));
                 stack.setCount(0);
                 return Optional.empty();
             }, data -> new ItemStack(Items.PAPER))) {
            ItemStack input = new ItemStack(Items.PAPER, 8);
            helper.assertTrue(EquipmentComponentRegistry.fromItemStack(input).orElseThrow().id().equals(fallback.part()),
                    "nonmatching reader must leave the next reader's input unchanged");
            helper.assertTrue(input.getCount() == 8 && !input.has(DataComponents.CUSTOM_NAME), "original must be untouched");
            EquipmentComponentRegistry.fromItemStack(new ItemStack(Items.COBBLESTONE));
            helper.assertTrue(calls.get() == 1, "unrelated items must not invoke this adapter");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lossyAndThrowingAdaptersRejectWithoutFallingThrough(GameTestHelper helper) {
        try (var fallback = plain("failure_fallback", Items.PAPER, 0)) {
            try (var lossy = plain("lossy", Items.PAPER, 10)) {
                ItemStack named = new ItemStack(Items.PAPER, 8);
                named.set(DataComponents.CUSTOM_NAME, Component.literal("must survive"));
                helper.assertTrue(EquipmentComponentRegistry.fromItemStack(named).isEmpty(), "lossy factory must reject input");
                helper.assertTrue(named.getCount() == 8 && named.has(DataComponents.CUSTOM_NAME), "input data retained");
            }
            AtomicInteger calls = new AtomicInteger();
            try (var broken = bind("throwing", Items.PAPER, 10, stack -> {
                calls.incrementAndGet();
                stack.setCount(0);
                throw new IllegalStateException("deliberate adapter failure");
            }, data -> new ItemStack(Items.PAPER))) {
                ItemStack source = new ItemStack(Items.PAPER, 8);
                helper.assertTrue(EquipmentComponentRegistry.fromItemStack(source).isEmpty(), "failure must not select fallback");
                helper.assertTrue(source.getCount() == 8 && calls.get() == 1, "failure preserves real stack");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void duplicateIdsAndStaleHandlesCannotReplaceBindings(GameTestHelper helper) {
        try (var binding = plain("lifecycle", Items.PAPER, 0)) {
            expectFailure(helper, () -> EquipmentComponentItemAdapters.register(binding.part(), Items.PAPER,
                    binding.part(), stack -> Optional.of(new CompoundTag())));
            var old = binding.registration();
            helper.assertTrue(old.unregister() && !old.unregister() && !old.isActive(), "unregister is idempotent");
            try (var current = EquipmentComponentItemAdapters.register(binding.part(), Items.PAPER, binding.part(),
                    stack -> Optional.of(new CompoundTag()))) {
                helper.assertTrue(!old.unregister() && current.isActive(), "old handle cannot remove new binding");
            }
            helper.assertTrue(EquipmentComponentRegistry.createItemStack(
                    new EquipmentComponentInstance(binding.part(), TYPE, TYPE)).isPresent(),
                    "unregistering input bindings must preserve reverse factories for saved components");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void changedDefinitionsAndWrongReverseItemsAreRejected(GameTestHelper helper) {
        try (var wrong = bind("wrong_return", Items.PAPER, 0, stack -> Optional.of(new CompoundTag()),
                data -> new ItemStack(Items.DIAMOND))) {
            helper.assertTrue(EquipmentComponentRegistry.fromItemStack(new ItemStack(Items.PAPER)).isEmpty(),
                    "reverse factory must restore the source item, not a different item");
        }
        try (var changed = plain("changed_definition", Items.PAPER, 0)) {
            EquipmentComponentRegistry.unregister(changed.part());
            EquipmentComponentRegistry.register(GameTestFixtures.component(changed.part(), id("different_type"), TYPE,
                    true, instance -> new ItemStack(Items.PAPER)));
            helper.assertTrue(EquipmentComponentRegistry.fromItemStack(new ItemStack(Items.PAPER)).isEmpty(),
                    "stale binding cannot reinterpret new registered identity");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void recursiveReadersAndFactoriesAreContained(GameTestHelper helper) {
        try (var recursive = bind("recursive_read", Items.PAPER, 0, stack -> {
            helper.assertTrue(EquipmentComponentRegistry.fromItemStack(stack).isEmpty(), "nested read must stop");
            return Optional.empty();
        }, data -> new ItemStack(Items.PAPER))) {
            helper.assertTrue(EquipmentComponentRegistry.fromItemStack(new ItemStack(Items.PAPER)).isEmpty(), "no recursion escape");
        }
        ResourceLocation recursiveId = id("recursive_factory");
        EquipmentComponentRegistry.register(GameTestFixtures.component(recursiveId, TYPE, TYPE, true,
                instance -> EquipmentComponentRegistry.createItemStack(instance).orElse(ItemStack.EMPTY)));
        try {
            helper.assertTrue(EquipmentComponentRegistry.createItemStack(
                    new EquipmentComponentInstance(recursiveId, TYPE, TYPE)).isEmpty(), "factory recursion must stop");
        } finally {
            EquipmentComponentRegistry.unregister(recursiveId);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void normalShiftAndHotbarClicksUseTheSameExternalBinding(GameTestHelper helper) {
        try (var binding = plain("menu_clicks", Items.PAPER, 0)) {
            for (ClickType action : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE, ClickType.SWAP)) {
                var test = menu(helper, ItemStack.EMPTY);
                if (action == ClickType.PICKUP) test.menu().setCarried(new ItemStack(Items.PAPER, 8));
                else test.player().getInventory().setItem(action == ClickType.SWAP ? 0 : 9, new ItemStack(Items.PAPER, 8));
                int target = action == ClickType.QUICK_MOVE ? EquipmentAssemblyMenu.containerSize() : EquipmentAssemblyMenu.PART_SLOT_START;
                test.menu().clicked(target, 0, action, test.player());
                helper.assertTrue(EquipmentStructureApi.component(test.menu().equipmentStack(), SLOT)
                        .orElseThrow().id().equals(binding.part()), "external component must install through " + action);
                int remaining = action == ClickType.PICKUP ? test.menu().getCarried().getCount()
                        : test.player().getInventory().countItem(Items.PAPER);
                helper.assertTrue(remaining == 7, "each action consumes exactly one item");
                if (action == ClickType.PICKUP) {
                    test.player().getInventory().setItem(9, test.menu().getCarried());
                    test.menu().setCarried(ItemStack.EMPTY);
                }
                test.menu().selectInterface(SECOND);
                test.menu().selectInterface(SLOT);
                test.menu().clicked(EquipmentAssemblyMenu.PART_SLOT_START, 0, ClickType.QUICK_MOVE, test.player());
                helper.assertTrue(test.player().getInventory().countItem(Items.PAPER) == 8, "inverse factory restores exact count");
                helper.assertTrue(EquipmentStructureApi.component(test.menu().equipmentStack(), SLOT).isEmpty(), "removed from structure");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void dualRoleItemsCanBeEquipmentOrComponentsWithoutAmbiguousShiftClick(GameTestHelper helper) {
        var hostBinding = EquipmentHostProviders.register(Items.PAPER,
                ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "datapack_host"));
        try (var binding = plain("dual_role", Items.PAPER, 0)) {
            var occupied = menu(helper, ItemStack.EMPTY);
            occupied.player().getInventory().setItem(9, new ItemStack(Items.PAPER, 8));
            occupied.menu().clicked(EquipmentAssemblyMenu.containerSize(), 0, ClickType.QUICK_MOVE, occupied.player());
            helper.assertTrue(EquipmentStructureApi.component(occupied.menu().equipmentStack(), SLOT)
                    .orElseThrow().id().equals(binding.part()), "occupied center gives explicit component binding priority");
            helper.assertTrue(occupied.player().getInventory().countItem(Items.PAPER) == 7, "one component consumed");
            helper.assertTrue(!EquipmentStructureApi.hasStructure(occupied.player().getInventory().getItem(9)),
                    "remaining component items must not be initialized as equipment");

            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            var empty = new EquipmentAssemblyMenu(1, player.getInventory(),
                    new SimpleContainer(EquipmentAssemblyMenu.containerSize()), List.of());
            player.getInventory().setItem(9, new ItemStack(Items.PAPER));
            empty.clicked(EquipmentAssemblyMenu.containerSize(), 0, ClickType.QUICK_MOVE, player);
            helper.assertTrue(empty.equipmentStack().is(Items.PAPER)
                    && EquipmentStructureApi.hasStructure(empty.equipmentStack()), "empty center accepts item as equipment");
            helper.assertTrue(player.getInventory().getItem(9).isEmpty(), "equipment moved once");
        } finally {
            hostBinding.unregister();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void savedEquipmentAndReopenedMenuRestoreOriginalItemData(GameTestHelper helper) {
        try (var binding = bind("persisted", Items.IRON_AXE, 0,
                stack -> Optional.of(saveFields(stack)), data -> restoreFields(Items.IRON_AXE, data))) {
            ItemStack original = decoratedAxe();
            var first = menu(helper, ItemStack.EMPTY);
            first.menu().setCarried(original.copy());
            first.menu().clickInterface(SLOT, 0, ClickType.PICKUP.ordinal(), first.player());
            ItemStack loaded = ItemStack.parse(helper.getLevel().registryAccess(),
                    first.menu().equipmentStack().save(helper.getLevel().registryAccess())).orElseThrow();
            first.menu().removed(first.player());
            helper.assertTrue(first.player().getInventory().countItem(Items.IRON_AXE) == 0, "closing must not return installed mirror");
            var reopened = menu(helper, loaded);
            for (int i = 0; i < 40; i++) {
                reopened.menu().selectInterface(SECOND);
                reopened.menu().selectInterface(SLOT);
            }
            reopened.menu().clickInterface(SLOT, 0, ClickType.PICKUP.ordinal(), reopened.player());
            helper.assertTrue(ItemStack.isSameItemSameComponents(original, reopened.menu().getCarried()),
                    "save/load and selection changes must preserve custom name, damage and custom data");
            helper.assertTrue(reopened.menu().getCarried().getCount() == 1, "only one component returned");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cancelledExternalInstallAndRemovalPreserveItems(GameTestHelper helper) {
        try (var binding = plain("cancelled", Items.PAPER, 0)) {
            Consumer<EquipmentStructureInstallEvent> installListener = event -> {
                if (event.component().id().equals(binding.part())) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(EquipmentStructureInstallEvent.class, installListener);
            var rejected = menu(helper, ItemStack.EMPTY);
            try {
                rejected.menu().setCarried(new ItemStack(Items.PAPER, 8));
                rejected.menu().clickInterface(SLOT, 0, ClickType.PICKUP.ordinal(), rejected.player());
                rejected.menu().removed(rejected.player());
                helper.assertTrue(rejected.menu().getCarried().getCount()
                        + rejected.player().getInventory().countItem(Items.PAPER) == 8, "cancelled install conserves input");
                helper.assertTrue(EquipmentStructureApi.component(rejected.menu().equipmentStack(), SLOT).isEmpty(), "no install");
            } finally {
                NeoForge.EVENT_BUS.unregister(installListener);
            }
            var installed = menu(helper, ItemStack.EMPTY);
            installed.menu().setCarried(new ItemStack(Items.PAPER));
            installed.menu().clickInterface(SLOT, 0, ClickType.PICKUP.ordinal(), installed.player());
            Consumer<EquipmentStructureRemoveEvent> removeListener = event -> {
                if (event.component().id().equals(binding.part())) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(EquipmentStructureRemoveEvent.class, removeListener);
            try {
                installed.menu().clickInterface(SLOT, 0, ClickType.PICKUP.ordinal(), installed.player());
                helper.assertTrue(installed.menu().getCarried().isEmpty(), "cancelled remove cannot yield an item");
                helper.assertTrue(EquipmentStructureApi.component(installed.menu().equipmentStack(), SLOT).isPresent(), "data retained");
            } finally {
                NeoForge.EVENT_BUS.unregister(removeListener);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lossyAdapterCannotConsumeCursorOrQuickMovedInput(GameTestHelper helper) {
        try (var binding = plain("menu_lossy", Items.PAPER, 0)) {
            var test = menu(helper, ItemStack.EMPTY);
            ItemStack named = new ItemStack(Items.PAPER, 8);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("custom"));
            test.menu().setCarried(named.copy());
            test.menu().clickInterface(SLOT, 0, ClickType.PICKUP.ordinal(), test.player());
            helper.assertTrue(test.menu().getCarried().getCount() == 8, "lossy cursor input retained");
            test.menu().setCarried(ItemStack.EMPTY);
            test.player().getInventory().setItem(9, named.copy());
            test.menu().clicked(EquipmentAssemblyMenu.containerSize(), 0, ClickType.QUICK_MOVE, test.player());
            helper.assertTrue(test.player().getInventory().countItem(Items.PAPER) == 8, "lossy shifted input retained");
            helper.assertTrue(EquipmentStructureApi.component(test.menu().equipmentStack(), SLOT).isEmpty(), "lossy data never installed");
        }
        helper.succeed();
    }

    private static Binding plain(String name, Item item, int priority) {
        return bind(name, item, priority, stack -> Optional.of(new CompoundTag()), data -> new ItemStack(item));
    }

    private static Binding bind(String name, Item item, int priority, EquipmentComponentItemAdapters.Reader reader,
                                java.util.function.Function<CompoundTag, ItemStack> restore) {
        ResourceLocation part = id(name);
        EquipmentComponentRegistry.register(GameTestFixtures.component(part, TYPE, TYPE, true,
                instance -> restore.apply(instance.data())));
        try {
            return new Binding(part, EquipmentComponentItemAdapters.register(part, item, part, priority, reader));
        } catch (RuntimeException exception) {
            EquipmentComponentRegistry.unregister(part);
            throw exception;
        }
    }

    private record Binding(ResourceLocation part, EquipmentComponentItemAdapters.Registration registration) implements AutoCloseable {
        @Override public void close() { registration.close(); EquipmentComponentRegistry.unregister(part); }
    }

    private static ItemStack decoratedAxe() {
        ItemStack result = new ItemStack(Items.IRON_AXE);
        result.setDamageValue(17);
        result.set(DataComponents.CUSTOM_NAME, Component.literal("adapter test axe"));
        CompoundTag payload = new CompoundTag();
        payload.putInt("charge", 42);
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(payload));
        return result;
    }

    // Fixture codec deliberately handles only its declared fields. Extra unsupported fields are
    // rejected by the adapter's round-trip check, rather than silently discarded.
    private static CompoundTag saveFields(ItemStack stack) {
        CompoundTag data = new CompoundTag();
        data.putInt("damage", stack.getDamageValue());
        if (stack.has(DataComponents.CUSTOM_NAME)) data.putString("name", stack.getHoverName().getString());
        if (stack.has(DataComponents.CUSTOM_DATA)) data.put("payload", stack.get(DataComponents.CUSTOM_DATA).copyTag());
        return data;
    }

    private static ItemStack restoreFields(Item item, CompoundTag data) {
        ItemStack result = new ItemStack(item);
        result.setDamageValue(data.getInt("damage"));
        if (data.contains("name")) result.set(DataComponents.CUSTOM_NAME, Component.literal(data.getString("name")));
        if (data.contains("payload")) result.set(DataComponents.CUSTOM_DATA, CustomData.of(data.getCompound("payload")));
        return result;
    }

    private static TestMenu menu(GameTestHelper helper, ItemStack equipment) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        if (equipment.isEmpty()) {
            equipment = new ItemStack(Items.IRON_SWORD);
            EquipmentStructureApi.setStructure(equipment, new EquipmentHostDefinition(
                    id("host"), BuiltinEquipmentTypes.SWORD, SLOTS).createStructure());
        }
        SimpleContainer inventory = new SimpleContainer(EquipmentAssemblyMenu.containerSize());
        inventory.setItem(0, equipment);
        var menu = new EquipmentAssemblyMenu(1, player.getInventory(), inventory, SLOTS);
        menu.selectInterface(SLOT);
        return new TestMenu(player, menu);
    }

    private record TestMenu(Player player, EquipmentAssemblyMenu menu) {}

    private static void expectFailure(GameTestHelper helper, Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "operation must reject invalid registration");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "item_adapter_test/" + path);
    }
}
