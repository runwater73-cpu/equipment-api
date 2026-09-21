package dev.equipmentstructure.api.test;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.diagnostic.EquipmentIntegrationDiagnostics;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@GameTestHolder(EquipmentStructureApiMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class EquipmentComponentValidationGameTests {
    private static final ResourceLocation TYPE = id("type"), SLOT = id("slot");
    private static final List<EquipmentSlotDefinition> SLOTS = List.of(EquipmentSlotDefinition.of(SLOT, TYPE));
    private static final Part[] PARTS = new Part[10];

    @EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
    public static final class Fixtures {
        @SubscribeEvent public static void items(net.neoforged.neoforge.registries.RegisterEvent event) {
            event.register(Registries.ITEM, registry -> {
                for (int i = 0; i < PARTS.length; i++) {
                    PARTS[i] = new Part(i);
                    registry.register(PARTS[i].id, PARTS[i]);
                }
            });
        }
    }

    @SubscribeEvent public static void register(RegisterGameTestsEvent event) { event.register(EquipmentComponentValidationGameTests.class); }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeItemSurvivesMenuSaveLoadAndRemovalWithAllData(GameTestHelper helper) {
        Part part = PARTS[0];
        try (var ignored = bind(helper, part)) {
            ItemStack input = decorated(helper, part);
            var first = menu(helper, ItemStack.EMPTY);
            first.menu.setCarried(input.copy());
            click(first, ClickType.PICKUP);
            helper.assertTrue(first.menu.getCarried().isEmpty(), "one input installed");
            var saved = first.menu.equipmentStack().save(helper.getLevel().registryAccess());
            first.menu.removed(first.player);
            var reopened = menu(helper, ItemStack.parse(helper.getLevel().registryAccess(), saved).orElseThrow());
            click(reopened, ClickType.PICKUP);
            helper.assertTrue(ItemStack.matches(input, reopened.menu.getCarried()), "name, enchantment, damage, custom data and removed defaults survive");
            helper.assertTrue(EquipmentStructureApi.component(reopened.menu.equipmentStack(), SLOT).isEmpty(), "component removed once");
            helper.assertTrue(first.player.getInventory().countItem(part) == 0, "closing never duplicates installed input");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void unregisteredNativeIsRejectedAndDiagnosedWithoutConsumption(GameTestHelper helper) {
        Part part = PARTS[1];
        part.reader = stack -> new EquipmentComponentInstance(part.id, TYPE, TYPE);
        ItemStack input = new ItemStack(part, 4);
        var report = EquipmentIntegrationDiagnostics.held(input, helper.getLevel().registryAccess());
        helper.assertTrue(report.issues().stream().anyMatch(issue -> issue.code().equals("item_component_unregistered")), "explicit missing-registration diagnostic");
        rejectBoth(helper, input);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeIdentityCannotImpersonateDifferentRegisteredTypes(GameTestHelper helper) {
        Part part = PARTS[2];
        try (var ignored = bind(helper, part)) {
            part.reader = stack -> new EquipmentComponentInstance(part.id, id("wrong_type"), TYPE);
            helper.assertTrue(EquipmentComponentInspection.inspect(new ItemStack(part)).status()
                    == EquipmentComponentInspection.Status.IDENTITY_MISMATCH, "component type must match definition");
            part.reader = stack -> new EquipmentComponentInstance(part.id, TYPE, id("wrong_interface"));
            rejectBoth(helper, new ItemStack(part, 4));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeFactoryCannotDiscardNameEnchantmentOrDamage(GameTestHelper helper) {
        Part part = PARTS[3];
        try (var ignored = bind(helper, part)) {
            EquipmentComponentRegistry.unregister(part.id);
            EquipmentComponentRegistry.register(GameTestFixtures.component(part.id, TYPE, TYPE, true,
                    instance -> new ItemStack(part)));
            ItemStack input = decorated(helper, part);
            helper.assertTrue(EquipmentComponentInspection.inspect(input).status()
                    == EquipmentComponentInspection.Status.ITEM_NOT_RESTORED, "lossy input rejected by same diagnostic path");
            rejectBoth(helper, input);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void absentOrThrowingNativeFactoryLeavesInputUntouched(GameTestHelper helper) {
        Part part = PARTS[4];
        part.reader = stack -> new EquipmentComponentInstance(part.id, TYPE, TYPE);
        EquipmentComponentRegistry.register(GameTestFixtures.component(part.id, TYPE, TYPE));
        try {
            rejectBoth(helper, new ItemStack(part, 3));
            EquipmentComponentRegistry.unregister(part.id);
            EquipmentComponentRegistry.register(GameTestFixtures.component(part.id, TYPE, TYPE, true,
                    instance -> { throw new IllegalStateException("deliberate native factory failure"); }));
            rejectBoth(helper, new ItemStack(part, 3));
        } finally { EquipmentComponentRegistry.unregister(part.id); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void recursiveNativeReaderAndFactoryAreContained(GameTestHelper helper) {
        Part part = PARTS[5];
        try (var ignored = bind(helper, part)) {
            part.reader = stack -> EquipmentComponentRegistry.fromItemStack(stack).orElseThrow();
            rejectBoth(helper, new ItemStack(part, 3));
            part.reader = stack -> new EquipmentComponentInstance(part.id, TYPE, TYPE);
            EquipmentComponentRegistry.unregister(part.id);
            EquipmentComponentRegistry.register(GameTestFixtures.component(part.id, TYPE, TYPE, true,
                    instance -> EquipmentComponentRegistry.createValidatedItemStack(instance).orElseThrow()));
            rejectBoth(helper, new ItemStack(part, 3));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void failedExternalOverrideNeverFallsBackToValidNativeReader(GameTestHelper helper) {
        Part part = PARTS[6];
        try (var ignored = bind(helper, part);
             var adapter = EquipmentComponentItemAdapters.register(id("bad_override"), part, part.id,
                     stack -> Optional.of(new CompoundTag()))) {
            rejectBoth(helper, new ItemStack(part, 2));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void changedFactoryCannotExportStaleMirrorOrLoseInstalledData(GameTestHelper helper) {
        Part part = PARTS[7];
        try (var ignored = bind(helper, part)) {
            var test = menu(helper, ItemStack.EMPTY);
            test.menu.setCarried(decorated(helper, part));
            click(test, ClickType.PICKUP);
            var before = test.menu.equipmentStack().copy();
            EquipmentComponentRegistry.unregister(part.id);
            EquipmentComponentRegistry.register(GameTestFixtures.component(part.id, TYPE, TYPE, true,
                    instance -> new ItemStack(part)));
            for (var type : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE, ClickType.SWAP, ClickType.THROW)) click(test, type);
            helper.assertTrue(ItemStack.matches(before, test.menu.equipmentStack()), "invalid reverse conversion cannot remove saved data");
            helper.assertTrue(test.menu.getCarried().isEmpty() && test.player.getInventory().countItem(part) == 0, "no stale projection transferred");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeDefinitionChangeDuringFactoryIsRejected(GameTestHelper helper) {
        Part part = PARTS[8];
        part.reader = stack -> new EquipmentComponentInstance(part.id, TYPE, TYPE);
        EquipmentComponentRegistry.register(GameTestFixtures.component(part.id, TYPE, TYPE, true, instance -> {
            EquipmentComponentRegistry.unregister(part.id);
            return new ItemStack(part);
        }));
        try {
            helper.assertTrue(EquipmentComponentInspection.inspect(new ItemStack(part)).status()
                    == EquipmentComponentInspection.Status.DEFINITION_CHANGED, "snapshot change is detected after callback");
            rejectBoth(helper, new ItemStack(part, 2));
        } finally { EquipmentComponentRegistry.unregister(part.id); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void updatedComponentStateMustSurviveReverseConversion(GameTestHelper helper) {
        Part part = PARTS[9];
        try (var ignored = bind(helper, part)) {
            var component = EquipmentComponentRegistry.fromItemStack(decorated(helper, part)).orElseThrow();
            var data = component.data();
            data.getCompound("components").getCompound("minecraft:custom_data").putInt("charge", 42);
            var updated = new EquipmentComponentInstance(part.id, TYPE, TYPE, data);
            var restored = EquipmentComponentRegistry.createValidatedItemStack(updated).orElseThrow();
            helper.assertTrue(restored.get(DataComponents.CUSTOM_DATA).copyTag().getInt("charge") == 42, "current state restored");
            data.putInt("lost_by_factory", 17);
            helper.assertTrue(EquipmentComponentRegistry.createValidatedItemStack(
                    new EquipmentComponentInstance(part.id, TYPE, TYPE, data)).isEmpty(), "unrepresented instance data blocks transfer");
        }
        helper.succeed();
    }

    private static Binding bind(GameTestHelper helper, Part part) {
        part.reader = stack -> new EquipmentComponentInstance(part.id, TYPE, TYPE,
                (CompoundTag) stack.save(helper.getLevel().registryAccess()));
        EquipmentComponentRegistry.register(GameTestFixtures.component(part.id, TYPE, TYPE, true,
                instance -> ItemStack.parse(helper.getLevel().registryAccess(), instance.data()).orElseThrow()));
        return new Binding(part);
    }

    private record Binding(Part part) implements AutoCloseable {
        @Override public void close() { EquipmentComponentRegistry.unregister(part.id); part.reader = null; }
    }

    private static ItemStack decorated(GameTestHelper helper, Item item) {
        var stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Native part name"));
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        stack.set(DataComponents.MAX_DAMAGE, 100);
        stack.setDamageValue(17);
        stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.UNBREAKING), 2);
        stack.remove(DataComponents.REPAIR_COST);
        var data = new CompoundTag(); data.putInt("charge", 7);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    private static void rejectBoth(GameTestHelper helper, ItemStack input) {
        var test = menu(helper, ItemStack.EMPTY);
        test.menu.setCarried(input.copy());
        click(test, ClickType.PICKUP);
        helper.assertTrue(ItemStack.matches(input, test.menu.getCarried()), "rejected cursor input retained exactly");
        test.menu.setCarried(ItemStack.EMPTY);
        test.player.getInventory().setItem(9, input.copy());
        test.menu.clicked(EquipmentAssemblyMenu.containerSize(), 0, ClickType.QUICK_MOVE, test.player);
        int retained = test.player.getInventory().items.stream()
                .filter(stack -> ItemStack.isSameItemSameComponents(input, stack)).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(retained == input.getCount(), "shift may move input within inventory but must retain all data and quantity");
        helper.assertTrue(EquipmentStructureApi.component(test.menu.equipmentStack(), SLOT).isEmpty(), "no installed data on failure");
    }

    private static TestMenu menu(GameTestHelper helper, ItemStack equipment) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        if (equipment.isEmpty()) {
            equipment = new ItemStack(Items.IRON_SWORD);
            EquipmentStructureApi.setStructure(equipment, new EquipmentHostDefinition(
                    id("host"), BuiltinEquipmentTypes.SWORD, SLOTS).createStructure());
        }
        var inventory = new SimpleContainer(EquipmentAssemblyMenu.containerSize()); inventory.setItem(0, equipment);
        var menu = new EquipmentAssemblyMenu(1, player.getInventory(), inventory, SLOTS); menu.selectInterface(SLOT);
        return new TestMenu(player, menu);
    }
    private record TestMenu(Player player, EquipmentAssemblyMenu menu) {}
    private static void click(TestMenu test, ClickType type) { test.menu.clickInterface(SLOT, 0, type.ordinal(), test.player); }

    private static final class Part extends Item implements EquipmentComponentItem {
        private final ResourceLocation id;
        private Function<ItemStack, EquipmentComponentInstance> reader;
        private Part(int index) { super(new Properties()); id = id("part_" + index); }
        @Override public EquipmentComponentInstance createComponent(ItemStack stack) { return reader.apply(stack); }
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "validation_test/" + path); }
}
