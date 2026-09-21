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
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Real item tags, menu transfers and resource reloads; never included in the runtime jar. */
@GameTestHolder(EquipmentStructureApiMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class EquipmentComponentTagGameTests {
    private static final TagKey<Item> PARTS = TagKey.create(Registries.ITEM, id("parts"));
    private static final TagKey<Item> NESTED = TagKey.create(Registries.ITEM, id("nested"));
    private static final TagKey<Item> MISSING = TagKey.create(Registries.ITEM, id("missing"));
    private static final ResourceLocation TYPE = id("type"), SLOT = id("slot");
    private static final List<EquipmentSlotDefinition> SLOTS = List.of(EquipmentSlotDefinition.of(SLOT, TYPE));

    @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
        event.register(EquipmentComponentTagGameTests.class);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nestedTagMembersShareIdentityAndPreserveCompleteItems(GameTestHelper helper) {
        try (var binding = bind(helper, "roundtrip", 0)) {
            for (Item item : List.of(Items.PAPER, Items.IRON_AXE, Items.AMETHYST_SHARD)) {
                var input = new ItemStack(item);
                input.set(DataComponents.CUSTOM_NAME, Component.literal("Kept tag part"));
                var custom = new CompoundTag(); custom.putInt("author:quality", 7);
                input.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
                if (input.isDamageableItem()) input.setDamageValue(17);
                input.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.UNBREAKING), 2);
                var component = EquipmentComponentRegistry.fromItemStack(input).orElseThrow();
                helper.assertTrue(component.id().equals(binding.part()), "all members use explicit component identity");
                var restored = EquipmentComponentRegistry.createValidatedItemStack(component).orElseThrow();
                helper.assertTrue(ItemStack.matches(input, restored), "source item and all data components survive");
            }
            helper.assertTrue(EquipmentComponentRegistry.fromItemStack(new ItemStack(Items.DIAMOND)).isEmpty(),
                    "unlisted item must not be classified");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void exactAndTagBindingsSharePriorityAndStableIdOrder(GameTestHelper helper) {
        for (boolean reverse : List.of(false, true)) {
            var part = id("order"); registerPart(helper, part);
            var registrations = new ArrayList<EquipmentComponentItemAdapters.Registration>();
            try {
                if (!reverse) registrations.add(EquipmentComponentItemAdapters.registerTag(id("a_tag"), PARTS, part, 5, reader(helper)));
                registrations.add(EquipmentComponentItemAdapters.register(id("z_exact"), Items.AMETHYST_SHARD, part, 5, reader(helper)));
                if (reverse) registrations.add(EquipmentComponentItemAdapters.registerTag(id("a_tag"), PARTS, part, 5, reader(helper)));
                var input = new ItemStack(Items.AMETHYST_SHARD);
                helper.assertTrue(EquipmentComponentInspection.inspect(input).adapter().orElseThrow().equals(id("a_tag")),
                        "stable ID breaks ties regardless of registration order or selector kind");
                try (var exact = EquipmentComponentItemAdapters.register(id("priority_exact"), Items.AMETHYST_SHARD, part, 10, reader(helper))) {
                    helper.assertTrue(EquipmentComponentInspection.inspect(input).adapter().orElseThrow().equals(id("priority_exact")),
                            "higher exact priority wins");
                    try (var tag = EquipmentComponentItemAdapters.registerTag(id("priority_tag"), NESTED, part, 11, reader(helper))) {
                        helper.assertTrue(EquipmentComponentInspection.inspect(input).adapter().orElseThrow().equals(id("priority_tag")),
                                "overlapping tag with higher priority wins");
                    }
                }
            } finally {
                registrations.forEach(EquipmentComponentItemAdapters.Registration::close);
                EquipmentComponentRegistry.unregister(part);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void conditionalAndMissingTagsInvokeOnlyEligibleReaders(GameTestHelper helper) {
        try (var binding = bind(helper, "conditions", 0)) {
            var calls = new AtomicInteger();
            try (var skipped = EquipmentComponentItemAdapters.registerTag(id("missing_reader"), MISSING, binding.part(), 20,
                    stack -> { throw new IllegalStateException("missing tag must not call reader"); });
                 var conditional = EquipmentComponentItemAdapters.registerTag(id("condition_reader"), PARTS, binding.part(), 10, stack -> {
                     helper.assertTrue(stack.getCount() == 1, "count-one defensive input");
                     calls.incrementAndGet(); stack.setCount(0);
                     stack.set(DataComponents.CUSTOM_NAME, Component.literal("only the copy"));
                     return Optional.empty();
                 })) {
                var input = new ItemStack(Items.PAPER, 8);
                helper.assertTrue(EquipmentComponentRegistry.fromItemStack(input).orElseThrow().id().equals(binding.part()),
                        "nonmatching condition falls through to lower tag candidate");
                helper.assertTrue(input.getCount() == 8 && !input.has(DataComponents.CUSTOM_NAME), "source not mutated");
                helper.assertTrue(EquipmentComponentRegistry.fromItemStack(new ItemStack(Items.DIAMOND)).isEmpty()
                        && calls.get() == 1, "unrelated item invokes no tag reader");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void matchedTagFailuresCannotFallBackToAnotherIdentity(GameTestHelper helper) {
        var broken = id("broken");
        EquipmentComponentRegistry.register(GameTestFixtures.component(broken, TYPE, TYPE, true,
                instance -> new ItemStack(Items.PAPER)));
        try (var fallback = bind(helper, "safe_fallback", 0)) {
            try (var binding = EquipmentComponentItemAdapters.registerTag(id("broken_binding"), PARTS, broken, 10,
                    stack -> Optional.of(new CompoundTag()))) {
                var input = new ItemStack(Items.PAPER, 4);
                input.set(DataComponents.CUSTOM_NAME, Component.literal("must survive"));
                helper.assertTrue(EquipmentComponentInspection.inspect(input).status() == EquipmentComponentInspection.Status.ITEM_NOT_RESTORED,
                        "lossy tag conversion must reject rather than fall through");
                helper.assertTrue(input.getCount() == 4 && input.has(DataComponents.CUSTOM_NAME), "source retained");
                helper.assertTrue(EquipmentComponentRegistry.fromItemStack(new ItemStack(Items.IRON_AXE)).isEmpty(),
                        "tag cannot convert one item into another");
            }
            try (var binding = EquipmentComponentItemAdapters.registerTag(id("throwing"), PARTS, broken, 10,
                    stack -> { throw new IllegalStateException("deliberate tag reader failure"); })) {
                helper.assertTrue(EquipmentComponentInspection.inspect(new ItemStack(Items.PAPER)).status()
                        == EquipmentComponentInspection.Status.CALLBACK_FAILED, "throwing tag cannot choose fallback");
            }
            try (var binding = EquipmentComponentItemAdapters.registerTag(id("recursive"), PARTS, broken, 10, stack -> {
                helper.assertTrue(EquipmentComponentRegistry.fromItemStack(stack).isEmpty(), "recursive tag read contained");
                return Optional.empty();
            })) {
                helper.assertTrue(EquipmentComponentRegistry.fromItemStack(new ItemStack(Items.PAPER)).orElseThrow()
                        .id().equals(fallback.part()), "outer conditional read may still continue");
            }
        } finally { EquipmentComponentRegistry.unregister(broken); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registrationLifecycleAndDiagnosticsIncludeTagsWithoutCallbacks(GameTestHelper helper) {
        expectFailure(helper, () -> EquipmentComponentItemAdapters.registerTag(id("unknown"), PARTS, id("unknown"), reader(helper)));
        var noFactory = id("no_factory");
        EquipmentComponentRegistry.register(GameTestFixtures.component(noFactory, TYPE, TYPE));
        try { expectFailure(helper, () -> EquipmentComponentItemAdapters.registerTag(id("no_factory_binding"), PARTS, noFactory, reader(helper))); }
        finally { EquipmentComponentRegistry.unregister(noFactory); }
        var part = id("lifecycle"); registerPart(helper, part);
        var calls = new AtomicInteger();
        try (var old = EquipmentComponentItemAdapters.registerTag(part, MISSING, part, stack -> {
            calls.incrementAndGet(); return Optional.empty();
        })) {
            expectFailure(helper, () -> EquipmentComponentItemAdapters.register(part, Items.PAPER, part, reader(helper)));
            expectFailure(helper, () -> EquipmentComponentItemAdapters.registerTag(part, PARTS, part, reader(helper)));
            var metadata = EquipmentComponentItemAdapters.tagBindings().stream().filter(b -> b.id().equals(part)).findFirst().orElseThrow();
            helper.assertTrue(metadata.tag().equals(MISSING) && metadata.definitionCurrent(), "tag metadata exposes registration");
            var report = EquipmentIntegrationDiagnostics.registrations(helper.getLevel().registryAccess());
            helper.assertTrue(report.issues().stream().anyMatch(issue -> issue.code().equals("adapter_tag_empty")
                    && issue.subject().equals(part.toString())), "missing optional tag is diagnosed");
            EquipmentComponentRegistry.unregister(part);
            report = EquipmentIntegrationDiagnostics.registrations(helper.getLevel().registryAccess());
            helper.assertTrue(report.issues().stream().anyMatch(issue -> issue.code().equals("adapter_definition_changed")
                    && issue.subject().equals(part.toString())), "stale tag definition diagnosed");
            helper.assertTrue(calls.get() == 0, "metadata diagnostics invoke no readers");
            helper.assertTrue(old.unregister() && !old.unregister() && !old.isActive(), "tag handle unregisters once");
            registerPart(helper, part);
            try (var current = EquipmentComponentItemAdapters.register(part, Items.PAPER, part, reader(helper))) {
                helper.assertTrue(!old.unregister() && current.isActive(), "old tag handle cannot remove reused exact binding");
            }
        } finally { EquipmentComponentRegistry.unregister(part); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void tagMenusConserveItemsAndRejectWrongSlotTypes(GameTestHelper helper) {
        try (var binding = bind(helper, "menus", 0)) {
            for (var action : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE, ClickType.SWAP)) {
                var test = menu(helper, ItemStack.EMPTY);
                if (action == ClickType.PICKUP) test.menu().setCarried(new ItemStack(Items.PAPER, 8));
                else test.player().getInventory().setItem(action == ClickType.SWAP ? 0 : 9, new ItemStack(Items.PAPER, 8));
                int target = action == ClickType.QUICK_MOVE ? EquipmentAssemblyMenu.containerSize() : EquipmentAssemblyMenu.PART_SLOT_START;
                test.menu().clicked(target, 0, action, test.player());
                helper.assertTrue(EquipmentStructureApi.component(test.menu().equipmentStack(), SLOT).orElseThrow().id().equals(binding.part()),
                        "tag recognized through " + action);
                if (action == ClickType.PICKUP) {
                    test.player().getInventory().setItem(9, test.menu().getCarried()); test.menu().setCarried(ItemStack.EMPTY);
                }
                helper.assertTrue(test.player().getInventory().countItem(Items.PAPER) == 7, "one item installed");
                test.menu().clickInterface(SLOT, 0, ClickType.QUICK_MOVE.ordinal(), test.player());
                helper.assertTrue(test.player().getInventory().countItem(Items.PAPER) == 8
                        && EquipmentStructureApi.component(test.menu().equipmentStack(), SLOT).isEmpty(), "one item returned");
            }
            var incompatible = new ItemStack(Items.IRON_SWORD);
            EquipmentStructureApi.setStructure(incompatible, new EquipmentHostDefinition(
                    id("wrong_host"), BuiltinEquipmentTypes.SWORD,
                    List.of(EquipmentSlotDefinition.of(SLOT, id("wrong_type")))).createStructure());
            var wrong = menu(helper, incompatible);
            wrong.menu().setCarried(new ItemStack(Items.PAPER, 8));
            wrong.menu().clickInterface(SLOT, 0, ClickType.PICKUP.ordinal(), wrong.player());
            helper.assertTrue(wrong.menu().getCarried().getCount() == 8
                    && EquipmentStructureApi.component(wrong.menu().equipmentStack(), SLOT).isEmpty(), "tag never bypasses slot compatibility");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void changedTagDefinitionsRejectExistingCandidates(GameTestHelper helper) {
        try (var binding = bind(helper, "stale", 0)) {
            EquipmentComponentRegistry.unregister(binding.part());
            EquipmentComponentRegistry.register(GameTestFixtures.component(binding.part(), id("new_type"), TYPE, true,
                    instance -> new ItemStack(Items.PAPER)));
            helper.assertTrue(EquipmentComponentInspection.inspect(new ItemStack(Items.PAPER)).status()
                    == EquipmentComponentInspection.Status.DEFINITION_CHANGED, "stale tag binding cannot reinterpret definition");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void realTagReloadChangesEligibilityWithoutDeletingSavedParts(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var originalPacks = List.copyOf(server.getPackRepository().getSelectedIds());
        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("esa_component_tag_reload_test");
        if (Files.exists(pack)) throw new IllegalStateException("Unexpected existing test pack: " + pack);
        Path tagFile = pack.resolve("data/equipment_structure_api/tags/item/component_tag_test/parts.json");
        try (var binding = bind(helper, "reload", 0)) {
            var first = menu(helper, ItemStack.EMPTY);
            first.menu().setCarried(new ItemStack(Items.PAPER));
            first.menu().clickInterface(SLOT, 0, ClickType.PICKUP.ordinal(), first.player());
            var saved = first.menu().equipmentStack().save(helper.getLevel().registryAccess());
            var installed = EquipmentStructureApi.component(first.menu().equipmentStack(), SLOT).orElseThrow();
            Files.createDirectories(tagFile.getParent());
            Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"component tag test\"}}");
            Files.writeString(tagFile, "{\"replace\":true,\"values\":[\"minecraft:diamond\"]}");
            server.getPackRepository().reload();
            var selected = new ArrayList<>(originalPacks); selected.add("file/esa_component_tag_reload_test");
            server.reloadResources(selected).join();
            helper.assertTrue(EquipmentComponentRegistry.fromItemStack(new ItemStack(Items.PAPER)).isEmpty(), "removed member stops matching");
            helper.assertTrue(EquipmentComponentRegistry.fromItemStack(new ItemStack(Items.DIAMOND)).orElseThrow().id().equals(binding.part()),
                    "new member matches without registering adapters again");
            var reopened = menu(helper, ItemStack.parse(helper.getLevel().registryAccess(), saved).orElseThrow());
            reopened.menu().clickInterface(SLOT, 0, ClickType.PICKUP.ordinal(), reopened.player());
            helper.assertTrue(reopened.menu().getCarried().isEmpty()
                    && EquipmentStructureApi.component(reopened.menu().equipmentStack(), SLOT).orElseThrow().equals(installed),
                    "failed reverse recognition keeps saved data; never deletes installed item");
            Files.writeString(tagFile, "{\"replace\":true,\"values\":[]}");
            server.reloadResources(selected).join();
            helper.assertTrue(EquipmentComponentRegistry.fromItemStack(new ItemStack(Items.DIAMOND)).isEmpty(), "empty tag disables binding");
            Files.delete(tagFile);
            server.reloadResources(selected).join();
            helper.assertTrue(EquipmentComponentRegistry.fromItemStack(new ItemStack(Items.PAPER)).isPresent()
                    && EquipmentComponentRegistry.fromItemStack(new ItemStack(Items.DIAMOND)).isEmpty(), "deleting override restores base members");
            reopened.menu().selectInterface(SLOT);
            reopened.menu().clickInterface(SLOT, 0, ClickType.PICKUP.ordinal(), reopened.player());
            helper.assertTrue(reopened.menu().getCarried().is(Items.PAPER) && reopened.menu().getCarried().getCount() == 1
                    && EquipmentStructureApi.component(reopened.menu().equipmentStack(), SLOT).isEmpty(), "restored membership allows exactly one removal");
        } finally {
            try { server.reloadResources(originalPacks).join(); }
            finally {
                // Only the newly created pack inside this isolated GameTest world is owned here.
                if (Files.exists(pack)) try (var paths = Files.walk(pack)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                }
                server.getPackRepository().reload();
            }
        }
        helper.succeed();
    }

    private static EquipmentComponentItemAdapters.Reader reader(GameTestHelper helper) {
        return stack -> Optional.of((CompoundTag) stack.save(helper.getLevel().registryAccess()));
    }

    private static void registerPart(GameTestHelper helper, ResourceLocation part) {
        EquipmentComponentRegistry.register(GameTestFixtures.component(part, TYPE, TYPE, true,
                instance -> ItemStack.parse(helper.getLevel().registryAccess(), instance.data()).orElseThrow()));
    }

    private static Binding bind(GameTestHelper helper, String name, int priority) {
        var part = id(name); registerPart(helper, part);
        try { return new Binding(part, EquipmentComponentItemAdapters.registerTag(part, PARTS, part, priority, reader(helper))); }
        catch (RuntimeException failure) { EquipmentComponentRegistry.unregister(part); throw failure; }
    }

    private record Binding(ResourceLocation part, EquipmentComponentItemAdapters.Registration registration) implements AutoCloseable {
        @Override public void close() { registration.close(); EquipmentComponentRegistry.unregister(part); }
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

    private static void expectFailure(GameTestHelper helper, Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "invalid registration rejected");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "component_tag_test/" + path);
    }
}
