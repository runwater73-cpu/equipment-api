package dev.equipmentstructure.api.test;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentComponentRegistry;
import dev.equipmentstructure.api.EquipmentHostDefinition;
import dev.equipmentstructure.api.EquipmentHostProviders;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.BuiltinEquipmentTypes;
import dev.equipmentstructure.api.ui.EquipmentItemView;
import dev.equipmentstructure.api.ui.EquipmentTooltip;
import dev.equipmentstructure.api.ui.EquipmentTooltipRegistry;
import dev.equipmentstructure.api.ui.EquipmentTooltips;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
public final class EquipmentTooltipGameTests {
    private static final ResourceLocation HOST = id("tooltip_test_host");
    private static final ResourceLocation SOCKET = id("tooltip_test_socket");
    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) { event.register(EquipmentTooltipGameTests.class); }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hoverDoesNotInitializeOrResolveProviders(GameTestHelper helper) {
        AtomicInteger queries = new AtomicInteger();
        var registration = EquipmentHostProviders.register(stack -> { queries.incrementAndGet(); return Optional.of(HOST); });
        try {
            var stack = new ItemStack(Items.IRON_SWORD);
            helper.assertTrue(EquipmentTooltips.lines(stack, false, Optional.empty()).isEmpty(), "uninitialized equipment has no fabricated structure");
            helper.assertTrue(queries.get() == 0 && !EquipmentStructureApi.hasStructure(stack), "hover is read-only and skips host providers");
        } finally { registration.unregister(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void summaryTracksInstalledDataAndConfiguredKey(GameTestHelper helper) {
        ResourceLocation partId = id("tooltip_part");
        EquipmentComponentRegistry.register(GameTestFixtures.component(partId, SOCKET, SOCKET));
        var stack = equipment();
        try {
            var before = EquipmentItemView.capture(stack);
            helper.assertTrue(before.installedCount() == 0, "initial view should be empty");
            EquipmentStructureApi.install(stack, SOCKET, new EquipmentComponentInstance(partId, SOCKET));
            helper.assertTrue(before.installedCount() == 0 && EquipmentItemView.capture(stack).installedCount() == 1, "snapshot remains stable while new reads update");
            var lines = EquipmentTooltips.lines(stack, false, Optional.of(Component.literal("H")));
            helper.assertTrue(lines.size() == 2 && lines.get(1).getString().contains("H"), "hint should use supplied rebound key");
            helper.assertTrue(EquipmentTooltips.lines(stack, false, Optional.empty()).size() == 1, "unbound key has no shortcut hint");
        } finally {
            EquipmentComponentRegistry.unregister(partId);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void authorsCanAppendReplaceOrHideOnlyApiSection(GameTestHelper helper) {
        var stack = equipment();
        var original = Component.literal("Other mod tooltip");
        for (int mode = 0; mode < 3; mode++) {
            final int current = mode;
            EquipmentTooltipRegistry.registerHost(HOST, context -> switch (current) {
                case 0 -> EquipmentTooltip.append(Component.literal("Custom"));
                case 1 -> EquipmentTooltip.replace(Component.literal("Custom"));
                default -> EquipmentTooltip.hidden();
            });
            try {
                var combined = new java.util.ArrayList<Component>(List.of(original));
                combined.addAll(EquipmentTooltips.lines(stack, false, Optional.empty()));
                helper.assertTrue(combined.getFirst().equals(original), "API cannot remove existing tooltip content");
                helper.assertTrue(combined.size() == 3 - mode, "append/replace/hide should affect only API section");
            } finally { EquipmentTooltipRegistry.unregisterHost(HOST); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void providerMutationAndExceptionDoNotAffectEquipment(GameTestHelper helper) {
        var stack = equipment();
        var before = stack.copy();
        EquipmentTooltipRegistry.registerHost(HOST, context -> {
            context.item().stack().setCount(0);
            throw new IllegalStateException("deliberate tooltip provider failure");
        });
        try {
            helper.assertTrue(EquipmentTooltips.lines(stack, false, Optional.empty()).size() == 1, "failed provider retains safe defaults");
            helper.assertTrue(ItemStack.matches(stack, before), "provider cannot mutate original stack");
        } finally { EquipmentTooltipRegistry.unregisterHost(HOST); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void looseComponentHasAuthorContentWithoutInventedHost(GameTestHelper helper) {
        ResourceLocation partId = id("menu_test_part");
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(partId), 8);
        EquipmentTooltipRegistry.registerComponent(partId, context -> {
            helper.assertTrue(context.item().hostId().isEmpty(), "loose part must have no invented host");
            helper.assertTrue(context.looseComponent().orElseThrow().id().equals(partId), "provider receives actual component");
            helper.assertTrue(context.expanded(), "expanded flag reaches author");
            return EquipmentTooltip.append(Component.literal("Part detail"));
        });
        try {
            var lines = EquipmentTooltips.lines(stack, true, Optional.of(Component.literal("G")));
            helper.assertTrue(lines.size() == 1 && lines.getFirst().getString().equals("Part detail"), "loose part uses author detail only");
            helper.assertTrue(stack.getCount() == 8, "hover preserves the original stack");
        } finally { EquipmentTooltipRegistry.unregisterComponent(partId); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void recursiveProviderIsBounded(GameTestHelper helper) {
        AtomicInteger calls = new AtomicInteger();
        EquipmentTooltipRegistry.registerHost(HOST, context -> {
            calls.incrementAndGet();
            EquipmentTooltips.lines(context.item().stack(), false, Optional.empty());
            return EquipmentTooltip.hidden();
        });
        try {
            EquipmentTooltips.lines(equipment(), false, Optional.empty());
            helper.assertTrue(calls.get() == 1, "recursive queries must not reenter provider");
        } finally { EquipmentTooltipRegistry.unregisterHost(HOST); }
        helper.succeed();
    }

    private static ItemStack equipment() {
        var stack = new ItemStack(Items.IRON_SWORD);
        EquipmentStructureApi.setStructure(stack, new EquipmentHostDefinition(
                HOST, BuiltinEquipmentTypes.SWORD,
                List.of(EquipmentSlotDefinition.of(SOCKET, SOCKET))).createStructure());
        return stack;
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, path); }
}
