package dev.equipmentstructure.api.test;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.BuiltinEquipmentTypes;
import dev.equipmentstructure.api.EquipmentComponentRegistry;
import dev.equipmentstructure.api.EquipmentHostDefinition;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.EquipmentStructureRegistries;
import dev.equipmentstructure.api.event.EquipmentStructureExtensionEvent;
import dev.equipmentstructure.api.event.EquipmentStructureInstallEvent;
import dev.equipmentstructure.api.event.EquipmentStructureRemoveEvent;
import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.EquipmentHostProviders;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import dev.equipmentstructure.api.appearance.AppearanceCatalog;
import dev.equipmentstructure.api.appearance.AppearanceDefinitions;
import dev.equipmentstructure.api.appearance.AppearancePlan;
import dev.equipmentstructure.api.appearance.AppearanceResolver;
import dev.equipmentstructure.api.appearance.AppearanceSupport;
import dev.equipmentstructure.api.appearance.AppearanceTransform;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 在真实 NeoForge 游戏环境中验证 API 基础契约。 */
@GameTestHolder(EquipmentStructureApiMod.MOD_ID)
@PrefixGameTestTemplate(false)
@net.neoforged.fml.common.EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class EquipmentStructureGameTests {

    private static final ResourceLocation HOST = id("game_test_host");
    private static final ResourceLocation BLADE_SLOT = id("blade");
    private static final ResourceLocation HANDLE_SLOT = id("handle");
    private static final ResourceLocation BLADE_TYPE = id("blade");
    private static final ResourceLocation HANDLE_TYPE = id("handle");
    private static final ResourceLocation DATAPACK_HOST = id("datapack_host");
    private static final ResourceLocation DATAPACK_SLOT = id("datapack_slot");
    private static final ResourceLocation CANCELLED_COMPONENT = id("cancelled_component");
    private static final ResourceLocation CANCELLED_SLOT = id("cancelled_slot");
    private static final ResourceLocation CANCELLED_EXTENSION_SLOT = id("cancelled_extension");
    private static boolean mutationListenersRegistered;

    private EquipmentStructureGameTests() {
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(EquipmentStructureGameTests.class);
        registerComponent(id("iron_blade"), BLADE_TYPE, BLADE_TYPE);
        registerComponent(id("iron_handle"), HANDLE_TYPE, HANDLE_TYPE);
        registerComponent(id("second_blade"), BLADE_TYPE, BLADE_TYPE);
        registerComponent(id("handle"), HANDLE_TYPE, HANDLE_TYPE);
        registerComponent(id("first_part"), BLADE_TYPE, BLADE_TYPE);
        registerComponent(id("second_part"), BLADE_TYPE, BLADE_TYPE);
        registerComponent(CANCELLED_COMPONENT, HANDLE_TYPE, HANDLE_TYPE);
        registerComponent(id("datapack_component"), id("datapack_component"), id("datapack_interface"));
        if (!mutationListenersRegistered) {
            mutationListenersRegistered = true;
            NeoForge.EVENT_BUS.addListener(EquipmentStructureInstallEvent.class, mutation -> {
                if (mutation.component().id().equals(CANCELLED_COMPONENT)) {
                    mutation.setCanceled(true);
                }
            });
            NeoForge.EVENT_BUS.addListener(EquipmentStructureRemoveEvent.class, mutation -> {
                if (mutation.component().id().equals(CANCELLED_COMPONENT)) {
                    mutation.setCanceled(true);
                }
            });
            NeoForge.EVENT_BUS.addListener(EquipmentStructureExtensionEvent.class, mutation -> {
                if (mutation.proposed().slot(CANCELLED_EXTENSION_SLOT).isPresent()) {
                    mutation.setCanceled(true);
                }
            });
        }
    }

    @GameTest(templateNamespace = EquipmentStructureApiMod.MOD_ID, template = "empty", timeoutTicks = 20)
    public static void structureComponentRoundTrip(GameTestHelper helper) {
        EquipmentHostDefinition definition = new EquipmentHostDefinition(
                HOST,
                BuiltinEquipmentTypes.SWORD,
                List.of(
                        EquipmentSlotDefinition.of(BLADE_SLOT, BLADE_TYPE),
                        EquipmentSlotDefinition.of(HANDLE_SLOT, HANDLE_TYPE)
                )
        );
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        EquipmentStructureApi.setStructure(stack, definition.createStructure());
        helper.assertTrue(EquipmentStructureApi.validate(stack).valid(),
                "a structure with empty slots must be valid");

        EquipmentStructureApi.InstallResult bladeResult = EquipmentStructureApi.install(
                stack,
                BLADE_SLOT,
                new EquipmentComponentInstance(id("iron_blade"), BLADE_TYPE)
        );
        EquipmentStructureApi.InstallResult handleResult = EquipmentStructureApi.install(
                stack,
                HANDLE_SLOT,
                new EquipmentComponentInstance(id("iron_handle"), HANDLE_TYPE)
        );

        helper.assertTrue(bladeResult == EquipmentStructureApi.InstallResult.INSTALLED,
                "blade should install");
        helper.assertTrue(handleResult == EquipmentStructureApi.InstallResult.INSTALLED,
                "handle should install");
        helper.assertTrue(EquipmentStructureApi.validate(stack).valid(),
                "installed components should preserve structural validity");
        helper.succeed();
    }

    @GameTest(templateNamespace = EquipmentStructureApiMod.MOD_ID, template = "empty", timeoutTicks = 20)
    public static void datapackRegistryInitialization(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess()
                .registry(EquipmentStructureRegistries.HOST_DEFINITION)
                .orElseThrow();
        helper.assertTrue(registry.getOptional(DATAPACK_HOST).isPresent(),
                "datapack host definition should be loaded");

        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        helper.assertTrue(EquipmentStructureApi.initialize(stack, helper.getLevel().registryAccess(), DATAPACK_HOST),
                "datapack host should initialize an item");
        helper.assertTrue(EquipmentStructureApi.slots(stack).stream()
                        .anyMatch(slot -> slot.id().equals(DATAPACK_SLOT)),
                "initialized structure should contain the datapack slot");
        helper.succeed();
    }

    @GameTest(templateNamespace = EquipmentStructureApiMod.MOD_ID, template = "empty", timeoutTicks = 20)
    public static void dataMapBindingInitializesAnItem(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.PAPER);
        helper.assertTrue(EquipmentStructureApi.initializeFromProvider(
                        stack, helper.getLevel().registryAccess()),
                "item data map binding should resolve its host template");
        helper.assertTrue(EquipmentStructureApi.structure(stack)
                        .map(structure -> structure.hostId().equals(DATAPACK_HOST))
                        .orElse(false),
                "data map binding should initialize the mapped host");
        helper.succeed();
    }

    @GameTest(templateNamespace = EquipmentStructureApiMod.MOD_ID, template = "empty", timeoutTicks = 20)
    public static void cancellableMutationsDoNotWrite(GameTestHelper helper) {
        EquipmentHostDefinition definition = new EquipmentHostDefinition(
                id("cancellation_host"),
                BuiltinEquipmentTypes.SWORD,
                List.of(EquipmentSlotDefinition.of(CANCELLED_SLOT, HANDLE_TYPE))
        );
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        EquipmentStructureApi.setStructure(stack, definition.createStructure());

        EquipmentStructureApi.InstallResult install = EquipmentStructureApi.install(
                stack,
                CANCELLED_SLOT,
                new EquipmentComponentInstance(CANCELLED_COMPONENT, HANDLE_TYPE)
        );
        helper.assertTrue(install == EquipmentStructureApi.InstallResult.CANCELED,
                "install event should be cancellable");
        helper.assertTrue(EquipmentStructureApi.components(stack, CANCELLED_SLOT).isEmpty(),
                "cancelled install must not write a component");

        EquipmentStructureApi.ExtensionResult extension = EquipmentStructureApi.extend(stack,
                structure -> structure.addSlot(EquipmentSlotDefinition.of(
                        CANCELLED_EXTENSION_SLOT, id("extension_type"))));
        helper.assertTrue(extension == EquipmentStructureApi.ExtensionResult.CANCELED,
                "extension event should be cancellable");
        helper.assertTrue(EquipmentStructureApi.slots(stack).stream()
                        .noneMatch(slot -> slot.id().equals(CANCELLED_EXTENSION_SLOT)),
                "cancelled extension must not write a slot");

        ItemStack removeStack = new ItemStack(Items.IRON_SWORD);
        EquipmentStructureApi.setStructure(removeStack, definition.createStructure().withComponents(
                CANCELLED_SLOT,
                List.of(new EquipmentComponentInstance(CANCELLED_COMPONENT, HANDLE_TYPE))
        ));
        helper.assertTrue(EquipmentStructureApi.remove(removeStack, CANCELLED_SLOT).isEmpty(),
                "remove event should be cancellable");
        helper.assertTrue(EquipmentStructureApi.components(removeStack, CANCELLED_SLOT).size() == 1,
                "cancelled remove must keep the component");
        helper.succeed();
    }

    @GameTest(templateNamespace = EquipmentStructureApiMod.MOD_ID, template = "empty", timeoutTicks = 20)
    public static void extensionCannotLowerStructureVersion(GameTestHelper helper) {
        EquipmentHostDefinition definition = new EquipmentHostDefinition(
                id("version_guard_host"),
                BuiltinEquipmentTypes.SWORD,
                List.of(EquipmentSlotDefinition.of(id("core"), id("core"))),
                3
        );
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        EquipmentStructureApi.setStructure(stack, definition.createStructure());

        boolean rejected = false;
        try {
            EquipmentStructureApi.extend(stack, structure -> new dev.equipmentstructure.api.EquipmentStructure(
                    structure.hostId(), structure.equipmentType(), structure.slots(), java.util.Map.of(), 2));
        } catch (IllegalArgumentException exception) {
            rejected = exception.getMessage().contains("lower the version");
        }
        helper.assertTrue(rejected, "an extension must not lower the structure version");
        helper.assertTrue(EquipmentStructureApi.structure(stack).orElseThrow().version() == 3,
                "a rejected extension must leave the original structure intact");
        helper.succeed();
    }

    @GameTest(templateNamespace = EquipmentStructureApiMod.MOD_ID, template = "empty", timeoutTicks = 20)
    public static void installCheckReportsOccupancyAndCompatibility(GameTestHelper helper) {
        EquipmentHostDefinition definition = new EquipmentHostDefinition(
                id("install_check_host"),
                BuiltinEquipmentTypes.SWORD,
                List.of(EquipmentSlotDefinition.of(BLADE_SLOT, BLADE_TYPE))
        );
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        EquipmentStructureApi.setStructure(stack, definition.createStructure());

        EquipmentStructureApi.InstallCheck available = EquipmentStructureApi.checkInstall(
                stack, BLADE_SLOT, new EquipmentComponentInstance(id("iron_blade"), BLADE_TYPE)
        );
        helper.assertTrue(available.isAllowed(), "compatible component should be available");
        helper.assertTrue(available.slot().isPresent(), "known slot should be included in the check");
        helper.assertTrue(EquipmentStructureApi.components(stack, BLADE_SLOT).isEmpty(),
                "preflight check must not mutate the stack");

        EquipmentStructureApi.install(
                stack, BLADE_SLOT, new EquipmentComponentInstance(id("iron_blade"), BLADE_TYPE)
        );
        EquipmentStructureApi.InstallCheck full = EquipmentStructureApi.checkInstall(
                stack, BLADE_SLOT, new EquipmentComponentInstance(id("second_blade"), BLADE_TYPE)
        );
        helper.assertTrue(full.status() == EquipmentStructureApi.InstallCheckStatus.SLOT_FULL,
                "filled slot should report SLOT_FULL");

        EquipmentStructureApi.InstallCheck incompatible = EquipmentStructureApi.checkInstall(
                stack, BLADE_SLOT, new EquipmentComponentInstance(id("handle"), HANDLE_TYPE)
        );
        helper.assertTrue(incompatible.status() == EquipmentStructureApi.InstallCheckStatus.INCOMPATIBLE_TYPE,
                "wrong component type should be reported");
        helper.succeed();
    }

    @GameTest(templateNamespace = EquipmentStructureApiMod.MOD_ID, template = "empty", timeoutTicks = 20)
    public static void providerInitializesAnItemFromDatapackTemplate(GameTestHelper helper) {
        var registration = EquipmentHostProviders.register(Items.IRON_SWORD, DATAPACK_HOST);
        try {
            ItemStack stack = new ItemStack(Items.IRON_SWORD);
            helper.assertTrue(EquipmentStructureApi.initializeFromProvider(
                    stack, helper.getLevel().registryAccess()),
                    "provider should initialize the mapped item");
            helper.assertTrue(EquipmentStructureApi.structure(stack)
                            .map(structure -> structure.hostId().equals(DATAPACK_HOST))
                            .orElse(false),
                    "provider should write the selected host template");

            EquipmentStructureApi.InstallCheck check = EquipmentStructureApi.checkInstall(
                    stack,
                    DATAPACK_SLOT,
                    new EquipmentComponentInstance(id("datapack_component"),
                            id("datapack_component"),
                            id("datapack_interface"))
            );
            helper.assertTrue(check.isAllowed(),
                    "component matching the datapack slot should be accepted");
        } finally {
            helper.assertTrue(registration.unregister(), "provider registration should be removable");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = EquipmentStructureApiMod.MOD_ID, template = "empty", timeoutTicks = 20)
    public static void unconfiguredAppearancePreservesOriginalEquipment(GameTestHelper helper) {
        ItemStack stack = appearanceTestEquipment();
        ItemStack before = stack.copy();
        var structure = EquipmentStructureApi.structure(stack).orElseThrow();
        var plan = AppearanceResolver.resolve(structure, new AppearanceCatalog(0, Map.of(), Map.of()),
                new AppearanceSupport(0, Set.of(), Set.of(), false));
        helper.assertTrue(plan.usesOriginalAppearance() && plan.hiddenOriginalElements().isEmpty(),
                "missing appearance must retain original equipment, not use a part icon");
        helper.assertTrue(plan.outcomes().getFirst().status() == AppearancePlan.Status.UNCONFIGURED,
                "missing appearance is normal, not a broken resource");
        helper.assertTrue(ItemStack.isSameItemSameComponents(before, stack) && before.getCount() == stack.getCount(),
                "appearance resolution must not mutate equipment or installed parts");
        helper.succeed();
    }

    @GameTest(templateNamespace = EquipmentStructureApiMod.MOD_ID, template = "empty", timeoutTicks = 20)
    public static void appearancePlanIsServerSafeAndDoesNotChangeInstallation(GameTestHelper helper) {
        ItemStack stack = appearanceTestEquipment();
        ItemStack before = stack.copy();
        var structure = EquipmentStructureApi.structure(stack).orElseThrow();
        var portId = id("appearance_port");
        var asset = id("prepared_test_asset");
        var element = id("original_element");
        var binding = new AppearanceDefinitions.Binding(portId,
                new AppearanceDefinitions.Joint(id("seam_owner"), AppearanceDefinitions.ANCHOR_ONLY, AppearanceTransform.IDENTITY), Set.of(element));
        var host = new AppearanceDefinitions.Host(id("host_visual"), Map.of(portId, new AppearanceDefinitions.Port(AppearanceTransform.IDENTITY)),
                Map.of(BLADE_SLOT, binding), Set.of(element));
        var part = new AppearanceDefinitions.Part(id("part_visual"), asset, AppearanceTransform.IDENTITY, Set.of());
        var catalog = new AppearanceCatalog(2, Map.of(structure.hostId(), host),
                Map.of(structure.component(BLADE_SLOT).orElseThrow().id(), part));
        var plan = AppearanceResolver.resolve(structure, catalog, new AppearanceSupport(2, Set.of(asset), Set.of(), true));
        helper.assertTrue(plan.placements().size() == 1 && plan.hiddenOriginalElements().equals(Set.of(element)),
                "pure plan should resolve without loading client rendering classes");
        var stale = AppearanceResolver.resolve(structure, catalog, new AppearanceSupport(1, Set.of(asset), Set.of(), true));
        helper.assertTrue(stale.usesOriginalAppearance() && stale.hiddenOriginalElements().isEmpty(),
                "stale resource generation must not remove original appearance");
        helper.assertTrue(ItemStack.isSameItemSameComponents(before, stack) && before.getCount() == stack.getCount(),
                "successful or stale appearance plans must preserve saved data");
        helper.succeed();
    }

    private static ItemStack appearanceTestEquipment() {
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        stack.setDamageValue(13);
        var data = new net.minecraft.nbt.CompoundTag();
        data.putString("author_state", "unchanged");
        var structure = new EquipmentHostDefinition(id("appearance_isolation_host"), BuiltinEquipmentTypes.SWORD,
                List.of(EquipmentSlotDefinition.of(BLADE_SLOT, BLADE_TYPE))).createStructure()
                .withComponents(BLADE_SLOT, List.of(new EquipmentComponentInstance(id("appearance_part"), BLADE_TYPE, data)));
        EquipmentStructureApi.setStructure(stack, structure);
        return stack;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, path);
    }

    @GameTest(templateNamespace = EquipmentStructureApiMod.MOD_ID, template = "empty", timeoutTicks = 20)
    public static void oneInterfaceHoldsOneComponentAndCanBeReused(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        EquipmentStructureApi.setStructure(stack, new EquipmentHostDefinition(
                id("single_host"), BuiltinEquipmentTypes.SWORD,
                List.of(EquipmentSlotDefinition.of(BLADE_SLOT, BLADE_TYPE))).createStructure());
        EquipmentComponentInstance first = new EquipmentComponentInstance(id("first_part"), BLADE_TYPE);
        EquipmentComponentInstance second = new EquipmentComponentInstance(id("second_part"), BLADE_TYPE);
        helper.assertTrue(EquipmentStructureApi.install(stack, BLADE_SLOT, first)
                == EquipmentStructureApi.InstallResult.INSTALLED, "first component should install");
        var beforeRejectedInstall = EquipmentStructureApi.structure(stack);
        helper.assertTrue(EquipmentStructureApi.install(stack, BLADE_SLOT, second)
                == EquipmentStructureApi.InstallResult.SLOT_FULL, "second component should be rejected");
        helper.assertTrue(EquipmentStructureApi.structure(stack).equals(beforeRejectedInstall),
                "rejected install must not overwrite the existing component");
        helper.assertTrue(EquipmentStructureApi.remove(stack, BLADE_SLOT).orElseThrow().equals(first),
                "remove by interface ID must return the original component");
        helper.assertTrue(EquipmentStructureApi.component(stack, BLADE_SLOT).isEmpty(), "interface should be empty");
        helper.assertTrue(EquipmentStructureApi.remove(stack, BLADE_SLOT).isEmpty(),
                "removing an empty interface should return empty");
        helper.assertTrue(EquipmentStructureApi.install(stack, BLADE_SLOT, second)
                == EquipmentStructureApi.InstallResult.INSTALLED, "empty interface should accept a new component");
        helper.succeed();
    }

    @GameTest(templateNamespace = EquipmentStructureApiMod.MOD_ID, template = "empty", timeoutTicks = 20)
    public static void menuProtocolSendsOneDefinitionPerInterface(GameTestHelper helper) {
        var definitions = java.util.stream.IntStream.range(0, 40).mapToObj(index ->
                EquipmentSlotDefinition.of(id("wire_slot_" + index), BLADE_TYPE)).toList();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            EquipmentAssemblyMenu.writeDefinitions(buffer, definitions);
            helper.assertTrue(buffer.readVarInt() == 40, "protocol must preserve all 40 interface definitions");
            for (var definition : definitions) {
                helper.assertTrue(buffer.readResourceLocation().equals(definition.id()), "interface ID mismatch");
                helper.assertTrue(buffer.readResourceLocation().equals(definition.interfaceType()), "interface type mismatch");
                helper.assertTrue(buffer.readResourceLocation().equals(definition.componentType()), "component type mismatch");
            }
            helper.assertTrue(buffer.readableBytes() == 0, "protocol must contain exactly three IDs per slot");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = EquipmentStructureApiMod.MOD_ID, template = "empty", timeoutTicks = 20)
    public static void registeredPermanentComponentCannotBeRemoved(GameTestHelper helper) {
        var componentId = id("permanent_component_test");
        var definition = GameTestFixtures.component(
                componentId, BLADE_TYPE, BLADE_TYPE, false);
        dev.equipmentstructure.api.EquipmentComponentRegistry.register(definition);
        try {
            ItemStack stack = new ItemStack(Items.IRON_SWORD);
            helper.assertTrue(EquipmentStructureApi.checkRemove(stack, BLADE_SLOT).status()
                    == EquipmentStructureApi.RemoveCheckStatus.NO_STRUCTURE, "no-structure preview mismatch");
            EquipmentStructureApi.setStructure(stack, new EquipmentHostDefinition(
                    id("permanent_host"), BuiltinEquipmentTypes.SWORD,
                    List.of(EquipmentSlotDefinition.of(BLADE_SLOT, BLADE_TYPE))).createStructure());
            helper.assertTrue(EquipmentStructureApi.checkRemove(stack, id("unknown_slot")).status()
                    == EquipmentStructureApi.RemoveCheckStatus.UNKNOWN_SLOT, "unknown-interface preview mismatch");
            helper.assertTrue(EquipmentStructureApi.checkRemove(stack, BLADE_SLOT).status()
                    == EquipmentStructureApi.RemoveCheckStatus.EMPTY_SLOT, "empty-interface preview mismatch");
            helper.assertTrue(EquipmentStructureApi.install(stack, BLADE_SLOT, definition.createInstance())
                    == EquipmentStructureApi.InstallResult.INSTALLED, "permanent component should install");
            var before = EquipmentStructureApi.structure(stack);
            helper.assertTrue(EquipmentStructureApi.checkRemove(stack, BLADE_SLOT).status()
                    == EquipmentStructureApi.RemoveCheckStatus.NOT_REMOVABLE, "permanent-component preview mismatch");
            helper.assertTrue(EquipmentStructureApi.structure(stack).equals(before), "preview must be read-only");
            helper.assertTrue(EquipmentStructureApi.remove(stack, BLADE_SLOT).isEmpty(),
                    "permanent component removal must be rejected");
            helper.assertTrue(EquipmentStructureApi.component(stack, BLADE_SLOT).isPresent(),
                    "rejected removal must preserve component");
            dev.equipmentstructure.api.EquipmentComponentRegistry.unregister(componentId);
            helper.assertTrue(EquipmentStructureApi.checkRemove(stack, BLADE_SLOT).isAllowed(),
                    "unknown legacy component should remain recoverable");
            helper.assertTrue(EquipmentStructureApi.remove(stack, BLADE_SLOT).isPresent(),
                    "unknown legacy component should be removable");
        } finally {
            dev.equipmentstructure.api.EquipmentComponentRegistry.unregister(componentId);
        }
        helper.succeed();
    }

    private static void registerComponent(ResourceLocation componentId, ResourceLocation componentType,
                                          ResourceLocation interfaceType) {
        if (!EquipmentComponentRegistry.isRegistered(componentId)) {
            EquipmentComponentRegistry.register(GameTestFixtures.component(
                    componentId, componentType, interfaceType));
        }
    }
}
