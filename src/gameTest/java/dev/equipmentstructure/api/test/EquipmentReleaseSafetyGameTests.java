package dev.equipmentstructure.api.test;

import dev.equipmentstructure.api.EquipmentHostDefinition;
import dev.equipmentstructure.api.EquipmentHostProviders;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.BuiltinEquipmentTypes;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import dev.equipmentstructure.api.ui.EquipmentDisplay;
import dev.equipmentstructure.api.ui.EquipmentDisplayRegistry;
import dev.equipmentstructure.api.ui.EquipmentDisplayRow;
import dev.equipmentstructure.api.ui.EquipmentItemView;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder(EquipmentStructureApiMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class EquipmentReleaseSafetyGameTests {
    private static final ResourceLocation HOST = id("release_test_host");
    @SubscribeEvent public static void register(RegisterGameTestsEvent event) { event.register(EquipmentReleaseSafetyGameTests.class); }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hostReadersCannotMutateOriginalOrLaterReaders(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        var mutating = EquipmentHostProviders.register(copy -> { copy.setCount(0); throw new IllegalStateException("deliberate reader failure"); }, 100);
        var fallback = EquipmentHostProviders.register(copy -> copy.getCount() == 1 ? Optional.of(HOST) : Optional.empty());
        try {
            helper.assertTrue(EquipmentHostProviders.resolve(stack).orElseThrow().equals(HOST), "next reader receives original data");
            helper.assertTrue(EquipmentHostProviders.resolve(stack, helper.getLevel().registryAccess()).orElseThrow().equals(HOST), "registry overload is isolated");
            helper.assertTrue(stack.getCount() == 1, "source unchanged");
        } finally { mutating.unregister(); fallback.unregister(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void oldHandleDoesNotDeleteSameHostReregistration(GameTestHelper helper) {
        var old = EquipmentHostProviders.register(Items.PAPER, HOST);
        var current = EquipmentHostProviders.register(Items.PAPER, HOST);
        try {
            old.unregister();
            helper.assertTrue(EquipmentHostProviders.resolve(new ItemStack(Items.PAPER)).orElseThrow().equals(HOST), "new entry survives old handle");
        } finally { old.unregister(); current.unregister(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void recursiveHostReaderFallsBack(GameTestHelper helper) {
        AtomicInteger calls = new AtomicInteger();
        var recursive = EquipmentHostProviders.register(copy -> { calls.incrementAndGet(); return EquipmentHostProviders.resolve(copy); }, 100);
        var fallback = EquipmentHostProviders.registerFallback(copy -> Optional.of(HOST));
        try {
            helper.assertTrue(EquipmentHostProviders.resolve(new ItemStack(Items.IRON_SWORD)).orElseThrow().equals(HOST), "recursive resolution falls back");
            helper.assertTrue(calls.get() == 1, "reader does not recurse");
        } finally { recursive.unregister(); fallback.unregister(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void overLimitEquipmentStaysRecoverable(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var container = new SimpleContainer(EquipmentAssemblyMenu.containerSize());
        ItemStack equipment = new ItemStack(Items.IRON_SWORD);
        EquipmentStructureApi.setStructure(equipment, new EquipmentHostDefinition(
                HOST, BuiltinEquipmentTypes.SWORD, List.of(
                EquipmentSlotDefinition.of(id("first"), id("part")))).createStructure());
        container.setItem(0, equipment);
        var menu = new EquipmentAssemblyMenu(1, player.getInventory(), container, EquipmentStructureApi.slots(equipment));
        menu.selectInterface(id("first"));
        var many = java.util.stream.IntStream.range(0, 1000).mapToObj(index ->
                EquipmentSlotDefinition.of(id("slot_" + index), id("part"))).toList();
        EquipmentStructureApi.setStructure(equipment, new EquipmentHostDefinition(
                HOST, BuiltinEquipmentTypes.SWORD, many).createStructure());
        menu.broadcastChanges();
        helper.assertTrue(menu.selectedInterface().isEmpty() && !menu.getSlot(1).isActive(), "over-limit expansion deactivates staging");
        helper.assertTrue(!menu.getSlot(0).mayPlace(equipment), "over-limit replacement is rejected");
        helper.assertTrue(EquipmentStructureApi.slots(menu.equipmentStack()).size() == 1000, "no truncation of saved interfaces");
        menu.removed(player);
        helper.assertTrue(player.getInventory().countItem(Items.IRON_SWORD) == 1, "equipment safely returned");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void equipmentInformationUsesAnIsolatedSnapshot(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        EquipmentStructureApi.setStructure(stack, new EquipmentHostDefinition(
                HOST, BuiltinEquipmentTypes.SWORD, List.of(
                EquipmentSlotDefinition.of(id("display_slot"), id("part")))).createStructure());
        EquipmentDisplayRegistry.register(HOST, view -> {
            view.stack().setCount(0);
            return EquipmentDisplay.append(new EquipmentDisplayRow(Component.literal("Energy"), Component.literal("42")));
        });
        try {
            var display = EquipmentDisplayRegistry.resolve(EquipmentItemView.capture(stack)).orElseThrow();
            helper.assertTrue(display.rows().getFirst().value().getString().equals("42"), "custom row returned");
            helper.assertTrue(stack.getCount() == 1, "display cannot mutate equipment");
        } finally { EquipmentDisplayRegistry.unregister(HOST); }
        helper.succeed();
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, path); }
}
