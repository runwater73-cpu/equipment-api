package dev.equipmentstructure.api.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentComponentRegistry;
import dev.equipmentstructure.api.BuiltinEquipmentTypes;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.appearance.AppearanceCatalog;
import dev.equipmentstructure.api.appearance.AppearanceDefinitions;
import dev.equipmentstructure.api.appearance.AppearancePlan;
import dev.equipmentstructure.api.appearance.AppearanceResolver;
import dev.equipmentstructure.api.appearance.AppearanceResourceDecoder;
import dev.equipmentstructure.api.appearance.AppearanceSupport;
import dev.equipmentstructure.api.appearance.AppearanceTransform;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@GameTestHolder(EquipmentStructureApiMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class EquipmentAppearanceConnectionGameTests {
    private static final ResourceLocation HOST = id("chain"), ROOT = id("root"), CHILD = id("child"), TYPE = id("type");

    private EquipmentAppearanceConnectionGameTests() {}

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) { event.register(EquipmentAppearanceConnectionGameTests.class); }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void v2ResourcesResolveOnDedicatedServerAndPreserveSavedParts(GameTestHelper helper) {
        var stack = item();
        var before = stack.copy();
        var catalog = catalog();
        var plan = AppearanceResolver.resolve(EquipmentStructureApi.structure(stack).orElseThrow(), catalog, support());
        helper.assertTrue(plan.placements().size() == 2, "both connected parts should resolve server-side without render classes");
        helper.assertTrue(Math.abs(childY(plan) + 2) < 1e-8, "short parent's child should start at -2");
        var restored = ItemStack.parse(helper.getLevel().registryAccess(), stack.save(helper.getLevel().registryAccess())).orElseThrow();
        helper.assertTrue(ItemStack.isSameItemSameComponents(before, stack), "resolution must not mutate data");
        helper.assertTrue(ItemStack.isSameItemSameComponents(stack, restored), "saved data must round trip");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void reinstallingParentMovesChildAndRemovalOnlyChangesVisualEligibility(GameTestHelper helper) {
        ResourceLocation longPart = id("long");
        EquipmentComponentRegistry.register(GameTestFixtures.component(longPart, TYPE, TYPE));
        var stack = item();
        var catalog = catalog();
        try {
            var removed = EquipmentStructureApi.remove(stack, ROOT).orElseThrow();
            helper.assertTrue(removed.data().getInt("quality") == 42, "removal must preserve author data");
            var missing = AppearanceResolver.resolve(EquipmentStructureApi.structure(stack).orElseThrow(), catalog, support());
            helper.assertTrue(missing.usesOriginalAppearance(), "missing parent should hide only connected overlay");
            helper.assertTrue(EquipmentStructureApi.component(stack, CHILD).isPresent(), "child remains logically installed");
            helper.assertTrue(EquipmentStructureApi.install(stack, ROOT, new EquipmentComponentInstance(longPart, TYPE, removed.data()))
                    == EquipmentStructureApi.InstallResult.INSTALLED, "long parent should install through unchanged API");
            var next = AppearanceResolver.resolve(EquipmentStructureApi.structure(stack).orElseThrow(), catalog, support());
            helper.assertTrue(next.placements().size() == 2 && Math.abs(childY(next) - 2) < 1e-8, "child must move to +2");
            helper.assertTrue(EquipmentStructureApi.component(stack, ROOT).orElseThrow().data().getInt("quality") == 42, "quality preserved");
        } finally {
            EquipmentComponentRegistry.unregister(longPart);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cyclicResourcesNeverDeleteInstalledComponents(GameTestHelper helper) {
        var stack = item();
        var before = stack.copy();
        var original = catalog();
        var host = original.hosts().get(HOST);
        var ports = new HashMap<>(host.ports());
        ports.put(id("base"), AppearanceDefinitions.Port.onComponent(ROOT, id("tip"), AppearanceTransform.IDENTITY));
        var changed = new AppearanceDefinitions.Host(HOST, ports, host.bindings(), host.replaceableElements());
        var catalog = new AppearanceCatalog(3, Map.of(HOST, changed), original.components());
        var plan = AppearanceResolver.resolve(EquipmentStructureApi.structure(stack).orElseThrow(), catalog, support());
        helper.assertTrue(plan.usesOriginalAppearance() && plan.hiddenOriginalElements().isEmpty(), "cycle should retain original model");
        helper.assertTrue(plan.outcomes().stream().allMatch(value -> value.reason() == AppearancePlan.Reason.CYCLIC_CONNECTION), "cycle diagnosis");
        helper.assertTrue(ItemStack.isSameItemSameComponents(before, stack), "cyclic visuals must preserve parts");
        helper.succeed();
    }

    private static ItemStack item() {
        var data = new CompoundTag();
        data.putInt("quality", 42);
        var structure = new EquipmentStructure(HOST, BuiltinEquipmentTypes.SWORD,
                List.of(EquipmentSlotDefinition.of(ROOT, TYPE),
                EquipmentSlotDefinition.of(CHILD, TYPE)), Map.of(
                ROOT, List.of(new EquipmentComponentInstance(id("short"), TYPE, data)),
                CHILD, List.of(new EquipmentComponentInstance(id("end"), TYPE))));
        var stack = new ItemStack(Items.IRON_SWORD);
        stack.setDamageValue(13);
        EquipmentStructureApi.setStructure(stack, structure);
        return stack;
    }

    private static AppearanceCatalog catalog() {
        return AppearanceResourceDecoder.decodeCatalog(3, Map.of(HOST, json("host/chain")), Map.of(
                id("short"), json("component/short"), id("long"), json("component/long"), id("end"), json("component/end"))).getOrThrow();
    }

    private static AppearanceSupport support() {
        return new AppearanceSupport(3, Set.of(id("short_asset"), id("long_asset"), id("end_asset")), Set.of(), false);
    }

    private static JsonElement json(String path) {
        try (var reader = new InputStreamReader(Objects.requireNonNull(EquipmentAppearanceConnectionGameTests.class.getResourceAsStream(
                "/assets/esa_connection/equipment_structure_api/appearance/" + path + ".json")), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static double childY(AppearancePlan plan) {
        return plan.placements().stream().filter(value -> value.slotId().equals(CHILD)).findFirst().orElseThrow().transform().position().y();
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("esa_connection", path); }
}
