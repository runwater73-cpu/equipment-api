package dev.equipmentstructure.api.test;

import com.mojang.brigadier.CommandDispatcher;
import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.attribute.EquipmentAttributeRegistry;
import dev.equipmentstructure.api.command.EquipmentStructureCommands;
import dev.equipmentstructure.api.diagnostic.EquipmentDiagnosticReport;
import dev.equipmentstructure.api.diagnostic.EquipmentIntegrationDiagnostics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
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
import java.util.function.Function;

@GameTestHolder(EquipmentStructureApiMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class EquipmentDiagnosticGameTests {
    private static final ResourceLocation TYPE = id("type"), SLOT = id("slot");
    private static Item failingNative;
    private static Item lossyNative;

    @EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
    public static final class Fixtures {
        @SubscribeEvent public static void items(net.neoforged.neoforge.registries.RegisterEvent event) {
            event.register(net.minecraft.core.registries.Registries.ITEM, registry -> {
                failingNative = new NativePart(stack -> { stack.setCount(0); throw new IllegalStateException("deliberate diagnostic failure"); });
                lossyNative = new NativePart(stack -> new EquipmentComponentInstance(id("saved_data"), TYPE, TYPE));
                registry.register(id("native_throw"), failingNative);
                registry.register(id("native_lossy"), lossyNative);
            });
        }
    }

    @SubscribeEvent public static void register(RegisterGameTestsEvent event) { event.register(EquipmentDiagnosticGameTests.class); }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void metadataScanDoesNotInvokeFactoriesProvidersOrBehaviors(GameTestHelper helper) {
        var part = id("metadata");
        var orphan = id("orphan");
        AtomicInteger calls = new AtomicInteger();
        var definition = GameTestFixtures.component(part, TYPE, TYPE, true,
                instance -> { calls.incrementAndGet(); return new ItemStack(Items.FLINT); });
        EquipmentComponentRegistry.register(definition);
        EquipmentAttributeRegistry.register(orphan, context -> { calls.incrementAndGet(); return List.of(); });
        EquipmentComponentBehaviorRegistry.register(orphan, new EquipmentComponentBehavior() {
            @Override public boolean canInstall(EquipmentComponentContext context) { calls.incrementAndGet(); return false; }
        });
        var hostProvider = EquipmentHostProviders.register(stack -> { calls.incrementAndGet(); return Optional.empty(); });
        try (var adapter = EquipmentComponentItemAdapters.register(id("metadata_adapter"), Items.FLINT, part,
                stack -> { calls.incrementAndGet(); return Optional.of(new CompoundTag()); })) {
            var captured = EquipmentComponentRegistry.definitions();
            EquipmentComponentRegistry.unregister(part);
            var report = EquipmentIntegrationDiagnostics.registrations(helper.getLevel().registryAccess());
            helper.assertTrue(has(report, "adapter_definition_changed", id("metadata_adapter")), "stale binding reported");
            helper.assertTrue(has(report, "attribute_component_unknown", orphan), "orphan attributes reported");
            helper.assertTrue(has(report, "behavior_component_unknown", orphan), "orphan behavior reported");
            helper.assertTrue(captured.get(part).equals(definition), "metadata snapshots survive later unregister");
            helper.assertTrue(calls.get() == 0, "metadata scan must execute no extension callbacks");
        } finally {
            EquipmentComponentRegistry.unregister(part);
            EquipmentAttributeRegistry.unregister(orphan);
            EquipmentComponentBehaviorRegistry.unregister(orphan);
            hostProvider.unregister();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void factoryAndFixedHostDiagnosticsRespectOptionalContracts(GameTestHelper helper) {
        var removable = id("missing_factory");
        var locked = id("locked");
        EquipmentComponentRegistry.register(GameTestFixtures.component(removable, TYPE, TYPE));
        EquipmentComponentRegistry.register(GameTestFixtures.component(locked, TYPE, TYPE, false));
        var binding = EquipmentHostProviders.register(Items.BRICK, id("missing_host"));
        try {
            var report = EquipmentIntegrationDiagnostics.registrations(helper.getLevel().registryAccess());
            helper.assertTrue(has(report, "component_factory_missing", removable), "removable factory gap is actionable");
            helper.assertTrue(!has(report, "component_factory_missing", locked), "non-removable programmatic component is valid");
            helper.assertTrue(has(report, "host_binding_missing", ResourceLocation.parse("minecraft:brick")), "missing fixed host is found");
        } finally {
            binding.unregister();
            EquipmentComponentRegistry.unregister(removable);
            EquipmentComponentRegistry.unregister(locked);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void externalProbeMatchesRuntimeOrderAndPreservesOriginalData(GameTestHelper helper) {
        var part = id("external");
        EquipmentComponentRegistry.register(GameTestFixtures.component(part, TYPE, TYPE, true, instance -> {
            var restored = new ItemStack(Items.QUARTZ);
            restored.set(DataComponents.CUSTOM_NAME, Component.literal(instance.data().getString("name")));
            return restored;
        }));
        try (var adapter = EquipmentComponentItemAdapters.register(id("external_adapter"), Items.QUARTZ, part, stack -> {
            var data = new CompoundTag();
            data.putString("name", stack.getHoverName().getString());
            stack.setCount(0);
            return Optional.of(data);
        })) {
            var held = new ItemStack(Items.QUARTZ, 7);
            held.set(DataComponents.CUSTOM_NAME, Component.literal("kept"));
            var before = held.copy();
            var probe = EquipmentComponentInspection.inspect(held);
            helper.assertTrue(probe.status() == EquipmentComponentInspection.Status.MATCH, "round-trip probe recognizes adapter");
            helper.assertTrue(probe.component().equals(EquipmentComponentRegistry.fromItemStack(held)), "probe matches runtime identity and data");
            var report = EquipmentIntegrationDiagnostics.held(held, helper.getLevel().registryAccess());
            helper.assertTrue(report.count(EquipmentDiagnosticReport.Severity.ERROR) == 0, "lossless adapter passes held check");
            helper.assertTrue(ItemStack.matches(held, before), "mutating reader changes only its count-one copy");
        } finally { EquipmentComponentRegistry.unregister(part); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rejectedHighPriorityAdapterDoesNotFallThrough(GameTestHelper helper) {
        var part = id("rejected");
        EquipmentComponentRegistry.register(GameTestFixtures.component(part, TYPE, TYPE, true, instance -> new ItemStack(Items.STICK)));
        AtomicInteger later = new AtomicInteger();
        try (var bad = EquipmentComponentItemAdapters.register(id("reject_adapter"), Items.CLAY_BALL, part, 20,
                stack -> Optional.of(new CompoundTag()));
             var low = EquipmentComponentItemAdapters.register(id("fallback_adapter"), Items.CLAY_BALL, part, 0,
                     stack -> { later.incrementAndGet(); return Optional.empty(); })) {
            var input = new ItemStack(Items.CLAY_BALL);
            var result = EquipmentComponentInspection.inspect(input);
            helper.assertTrue(result.status() == EquipmentComponentInspection.Status.ITEM_NOT_RESTORED, "lossy restore is identified");
            helper.assertTrue(EquipmentComponentRegistry.fromItemStack(input).isEmpty() && later.get() == 0,
                    "neither probe nor runtime reinterpret rejected input using another binding");
            var report = EquipmentIntegrationDiagnostics.held(input, helper.getLevel().registryAccess());
            helper.assertTrue(has(report, "item_item_not_restored", ResourceLocation.parse("minecraft:clay_ball")), "report names failed adapter stage");
        } finally { EquipmentComponentRegistry.unregister(part); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void callbackExceptionsAndNativeReaderMutationsStayContained(GameTestHelper helper) {
        var held = new ItemStack(failingNative, 4);
        helper.assertTrue(EquipmentComponentInspection.inspect(held).status() == EquipmentComponentInspection.Status.CALLBACK_FAILED,
                "native exception becomes a finding");
        helper.assertTrue(held.getCount() == 4, "native failure cannot consume held stack");
        var part = id("throws");
        EquipmentComponentRegistry.register(GameTestFixtures.component(part, TYPE, TYPE, true, instance -> new ItemStack(Items.STRING)));
        try (var adapter = EquipmentComponentItemAdapters.register(id("throws_adapter"), Items.STRING, part,
                stack -> { throw new IllegalArgumentException("deliberate adapter failure"); })) {
            helper.assertTrue(EquipmentComponentInspection.inspect(new ItemStack(Items.STRING)).status()
                    == EquipmentComponentInspection.Status.CALLBACK_FAILED, "external exception is contained too");
        } finally { EquipmentComponentRegistry.unregister(part); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void actualInstalledDataDetectsLossyFactoryWithoutRunningBehavior(GameTestHelper helper) {
        var part = id("saved_data");
        EquipmentComponentRegistry.register(GameTestFixtures.component(part, TYPE, TYPE, true, instance -> new ItemStack(lossyNative)));
        var data = new CompoundTag();
        data.putInt("charge", 17);
        var instance = new EquipmentComponentInstance(part, TYPE, TYPE, data);
        var equipment = equipment(instance);
        var before = equipment.copy();
        AtomicInteger hooks = new AtomicInteger();
        EquipmentComponentBehaviorRegistry.register(part, new EquipmentComponentBehavior() {
            @Override public boolean canRemove(EquipmentComponentContext context) { hooks.incrementAndGet(); return false; }
            @Override public void onRemoved(EquipmentComponentContext context) { hooks.incrementAndGet(); }
        });
        try {
            var report = EquipmentIntegrationDiagnostics.held(equipment, helper.getLevel().registryAccess());
            helper.assertTrue(report.issues().stream().anyMatch(issue -> issue.code().equals("component_data_lossy")
                    && issue.reference().equals(part.toString())), "actual saved charge loss reported");
            helper.assertTrue(hooks.get() == 0 && ItemStack.matches(before, equipment), "probe does not remove or mutate installed component");
        } finally { EquipmentComponentRegistry.unregister(part); EquipmentComponentBehaviorRegistry.unregister(part); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void legacyIdentityAndDefinitionChangesAreNotSilentlyMigrated(GameTestHelper helper) {
        var part = id("legacy");
        var equipment = equipment(new EquipmentComponentInstance(part, TYPE));
        var snapshot = EquipmentStructureApi.structure(equipment).orElseThrow();
        var unknown = EquipmentIntegrationDiagnostics.held(equipment, helper.getLevel().registryAccess());
        helper.assertTrue(unknown.issues().stream().anyMatch(issue -> issue.code().equals("component_unregistered")), "legacy identity gets warning");
        EquipmentComponentRegistry.register(GameTestFixtures.component(part, id("new_type"), id("new_type"), false));
        try {
            var changed = EquipmentIntegrationDiagnostics.held(equipment, helper.getLevel().registryAccess());
            helper.assertTrue(changed.issues().stream().anyMatch(issue -> issue.code().equals("installed_incompatible")), "current policy mismatch is detected");
            helper.assertTrue(EquipmentStructureApi.structure(equipment).orElseThrow() == snapshot, "diagnostics do not migrate saved structure");
        } finally { EquipmentComponentRegistry.unregister(part); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void developerCommandsRegisterParseAndRespectPermissions(GameTestHelper helper) throws Exception {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        EquipmentStructureCommands.register(dispatcher);
        var source = helper.getLevel().getServer().createCommandSourceStack().withPermission(2).withSuppressedOutput();
        helper.assertTrue(dispatcher.execute("equipment_structure_api diagnose registrations 999", source) == 1,
                "registered paged command executes against real registries");
        var denied = dispatcher.parse("equipment_structure_api diagnose", source.withPermission(0));
        helper.assertTrue(denied.getReader().canRead(), "non-operator cannot reach diagnostics branch");
        var fake = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
        var old = fake.getMainHandItem();
        try {
            var held = equipment(new EquipmentComponentInstance(id("command_legacy"), TYPE));
            fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
            var before = held.copy();
            helper.assertTrue(dispatcher.execute("equipment_structure_api diagnose held", fake.createCommandSourceStack()
                    .withPermission(2).withSuppressedOutput()) == 1, "held command runs for a server player");
            helper.assertTrue(ItemStack.matches(held, before), "command preserves held item");
        } finally { fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, old); }
        helper.succeed();
    }

    private static boolean has(EquipmentDiagnosticReport report, String code, ResourceLocation subject) {
        return report.issues().stream().anyMatch(issue -> issue.code().equals(code) && issue.subject().equals(subject.toString()));
    }
    private static ItemStack equipment(EquipmentComponentInstance instance) {
        var stack = new ItemStack(Items.IRON_SWORD);
        EquipmentStructureApi.setStructure(stack, new EquipmentHostDefinition(
                id("host"), BuiltinEquipmentTypes.SWORD, List.of(
                EquipmentSlotDefinition.of(SLOT, TYPE))).createStructure().withComponent(SLOT, instance));
        return stack;
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "diagnostic_test/" + path); }
    private static final class NativePart extends Item implements EquipmentComponentItem {
        private final Function<ItemStack, EquipmentComponentInstance> reader;
        private NativePart(Function<ItemStack, EquipmentComponentInstance> reader) { super(new Item.Properties()); this.reader = reader; }
        @Override public EquipmentComponentInstance createComponent(ItemStack stack) { return reader.apply(stack); }
    }
}
