package dev.equipmentstructure.api.attribute;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.network.ComponentAttributesPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@GameTestHolder(EquipmentStructureApiMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class EquipmentAttributeDataGameTests {
    private static final ResourceLocation SLOT = id("slot");
    private static final ResourceLocation INTERFACE = id("socket");

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) { event.register(EquipmentAttributeDataGameTests.class); }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void absentOptionalModSkipsJsonAndPreservesJava(GameTestHelper helper) {
        var part = id("conditional");
        EquipmentAttributeRegistry.register(part, EquipmentAttributeContribution.add(Attributes.ARMOR, id("fallback"), 2));
        try {
            helper.assertTrue(!EquipmentAttributeData.current().definitions().containsKey(part), "false NeoForge condition skips missing mod attribute");
            helper.assertTrue(EquipmentAttributeResolver.value(equipment(part), Attributes.ARMOR, 0, EquipmentSlot.MAINHAND) == 2,
                    "skipped resource does not suppress Java fallback");
        } finally { EquipmentAttributeRegistry.unregister(part); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void startupDatapackOverridesJavaAndUsesSharedResolver(GameTestHelper helper) {
        var part = id("fixture");
        EquipmentAttributeRegistry.register(part, EquipmentAttributeContribution.add(Attributes.ARMOR, id("java"), 20));
        try {
            var stack = equipment(part);
            helper.assertTrue(EquipmentAttributeResolver.value(stack, Attributes.ARMOR, 0, EquipmentSlot.CHEST) == 3,
                    "JSON replaces Java provider and applies to armor");
            helper.assertTrue(EquipmentAttributeResolver.details(stack, SLOT, EquipmentSlot.CHEST).size() == 1,
                    "same source detail is exposed to UI");
            helper.assertTrue(EquipmentAttributeResolver.compute(stack.getAttributeModifiers(), Attributes.ARMOR,
                    0, EquipmentSlot.CHEST) == 3, "NeoForge event agrees with preview");
            stack.setDamageValue(stack.getMaxDamage());
            helper.assertTrue(EquipmentAttributeResolver.details(stack).isEmpty(), "broken item disables JSON contributions");
        } finally { EquipmentAttributeRegistry.unregister(part); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void explicitEmptyDefinitionSuppressesProvider(GameTestHelper helper) {
        var part = id("disabled");
        EquipmentAttributeRegistry.register(part, EquipmentAttributeContribution.add(Attributes.ARMOR, id("java"), 20));
        try {
            helper.assertTrue(EquipmentAttributeResolver.details(equipment(part)).isEmpty(), "empty JSON is deliberate suppression");
        } finally { EquipmentAttributeRegistry.unregister(part); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void hostSlotAndInterfaceFiltersAreConjunctive(GameTestHelper helper) {
        var definition = parse("""
                {"modifiers":[{"attribute":"minecraft:generic.armor","id":"example:armor","amount":2,
                 "operation":"add_value","equipment_slot":"chest","priority":5,"stacking":"replace",
                 "stacking_key":"example:armor_mode","host_id":"equipment_structure_api:attribute_json/host",
                 "slot_id":"equipment_structure_api:attribute_json/slot","interface_type":"equipment_structure_api:attribute_json/socket"}]}
                """);
        var stack = equipment(id("fixture"));
        var structure = EquipmentStructureApi.structure(stack).orElseThrow();
        var context = new EquipmentAttributeContext(stack, structure.slots().getFirst(), structure.component(SLOT).orElseThrow());
        var contribution = definition.contributions(helper.getLevel().registryAccess()).getFirst();
        helper.assertTrue(contribution.condition().test(context), "matching host, point and type pass");
        var wrongSlot = EquipmentSlotDefinition.of(id("other"), INTERFACE);
        helper.assertTrue(!contribution.condition().test(new EquipmentAttributeContext(stack, wrongSlot, context.component())), "wrong point fails");
        var wrongType = EquipmentSlotDefinition.of(SLOT, id("other"));
        helper.assertTrue(!contribution.condition().test(new EquipmentAttributeContext(stack, wrongType, context.component())), "wrong interface fails");
        EquipmentStructureApi.setStructure(stack, new EquipmentHostDefinition(
                id("other"), BuiltinEquipmentTypes.SWORD, structure.slots()).createStructure());
        helper.assertTrue(!contribution.condition().test(new EquipmentAttributeContext(stack, context.slot(), context.component())), "wrong host fails");
        helper.assertTrue(contribution.stacking() == EquipmentAttributeStacking.REPLACE && contribution.priority() == 5,
                "static definition preserves stacking metadata");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void networkRoundTripAndClientSnapshotStaySeparate(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var snapshot = EquipmentAttributeData.serverSnapshot(server);
        var buffer = Unpooled.buffer();
        try {
            ComponentAttributesPayload.STREAM_CODEC.encode(buffer, new ComponentAttributesPayload(snapshot.definitions()));
            var decoded = ComponentAttributesPayload.STREAM_CODEC.decode(buffer);
            helper.assertTrue(decoded.definitions().equals(snapshot.definitions()), "full snapshot packet round trip");
            EquipmentAttributeData.receive(decoded.definitions(), helper.getLevel().registryAccess());
            helper.assertTrue(clientDefinitionCount() == decoded.definitions().size(), "client thread sees received data");
            EquipmentAttributeData.receive(Map.of(), helper.getLevel().registryAccess());
            helper.assertTrue(clientDefinitionCount() == 0, "empty sync removes previous server definitions");
            helper.assertTrue(EquipmentAttributeData.current() == snapshot, "server snapshot is unaffected by client replacement");
            EquipmentAttributeData.receive(decoded.definitions(), helper.getLevel().registryAccess());
            EquipmentAttributeData.clearClient();
            helper.assertTrue(clientDefinitionCount() == 0, "disconnect clears client data");
        } finally { buffer.release(); EquipmentAttributeData.clearClient(); }
        helper.succeed();
    }

    private static int clientDefinitionCount() throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger(-1);
        var worker = new Thread(new ThreadGroup("attribute-json-client-test"),
                () -> count.set(EquipmentAttributeData.current().definitions().size()));
        worker.start();
        worker.join(5000);
        if (worker.isAlive()) throw new IllegalStateException("client-side snapshot probe timed out");
        return count.get();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void unknownAttributeRejectsWholeSnapshot(GameTestHelper helper) {
        var definition = parse(json("example:missing_attribute", "bonus", 3));
        boolean rejected = false;
        try { EquipmentAttributeData.Snapshot.compile(Map.of(id("missing"), definition), helper.getLevel().registryAccess()); }
        catch (IllegalArgumentException expected) { rejected = expected.getMessage().contains("attribute_json/missing"); }
        helper.assertTrue(rejected, "unknown attribute rejects definition with component diagnostic");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void realReloadUpdatesEquippedEntitiesRejectsBadDataAndRestoresJava(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var part = id("reload_part");
        var originalPacks = List.copyOf(server.getPackRepository().getSelectedIds());
        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("esa_attribute_reload_test");
        if (Files.exists(pack)) throw new IllegalStateException("Unexpected existing test pack: " + pack);
        Path definition = pack.resolve("data/equipment_structure_api/" + EquipmentAttributeReloadListener.DIRECTORY + "/attribute_json/reload_part.json");
        var wearer = new ArmorStand(helper.getLevel(), 0, 0, 0);
        wearer.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)));
        wearer.setNoGravity(true);
        var external = new AttributeModifier(id("unrelated_buff"), 4, AttributeModifier.Operation.ADD_VALUE);
        EquipmentAttributeRegistry.register(part, EquipmentAttributeContribution.add(Attributes.ARMOR, id("java_fallback"), 1,
                EquipmentSlotGroup.ANY));
        try {
            helper.getLevel().addFreshEntity(wearer);
            wearer.getAttribute(Attributes.ARMOR).addTransientModifier(external);
            wearer.setItemSlot(EquipmentSlot.MAINHAND, equipment(part));
            // Use the actual vanilla equipment detection before performing any reload.
            wearer.tick();
            helper.assertTrue(wearer.getAttributeValue(Attributes.ARMOR) == 5, "initial vanilla equip applies Java value");
            Files.createDirectories(definition.getParent());
            Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"attribute reload test\"}}");
            Files.writeString(definition, json("minecraft:generic.armor", "first", 3));
            server.getPackRepository().reload();
            var selected = new ArrayList<>(originalPacks);
            selected.add("file/esa_attribute_reload_test");
            server.reloadResources(selected).join();
            helper.assertTrue(wearer.getAttributeValue(Attributes.ARMOR) == 7, "reload changes worn item without re-equipping and preserves unrelated buff");
            Files.writeString(definition, json("minecraft:generic.max_health", "changed_id", 6));
            server.reloadResources(selected).join();
            helper.assertTrue(wearer.getAttributeValue(Attributes.ARMOR) == 4 && wearer.getMaxHealth() == 26,
                    "changed attribute and modifier ID remove previous contribution");
            var committed = EquipmentAttributeData.current();
            Files.writeString(definition, json("example:unknown", "broken", 30));
            boolean rejected = false;
            try { server.reloadResources(selected).join(); }
            catch (java.util.concurrent.CompletionException expected) { rejected = true; }
            helper.assertTrue(rejected && EquipmentAttributeData.current() == committed && wearer.getMaxHealth() == 26,
                    "failed reload retains complete committed resources and worn values");
            Files.writeString(definition, "{\"modifiers\":[]}");
            server.reloadResources(selected).join();
            helper.assertTrue(wearer.getMaxHealth() == 20 && wearer.getAttributeValue(Attributes.ARMOR) == 4,
                    "empty definition disables Java and removes old JSON contribution");
            Files.delete(definition);
            server.reloadResources(selected).join();
            helper.assertTrue(wearer.getAttributeValue(Attributes.ARMOR) == 5, "deleted definition restores Java fallback");
            wearer.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            wearer.tick();
            helper.assertTrue(wearer.getAttributeValue(Attributes.ARMOR) == 4, "unequip after reload leaves no component modifier");
        } finally {
            wearer.discard();
            EquipmentAttributeRegistry.unregister(part);
            try { server.reloadResources(originalPacks).join(); }
            finally {
                // Only this newly created, isolated GameTest pack is owned by the test.
                if (Files.exists(pack)) {
                    try (var paths = Files.walk(pack)) {
                        for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
                    }
                }
                server.getPackRepository().reload();
            }
        }
        helper.succeed();
    }

    private static String json(String attribute, String modifier, double amount) {
        return "{\"modifiers\":[{\"attribute\":\"" + attribute + "\",\"id\":\"example:" + modifier
                + "\",\"amount\":" + amount + ",\"operation\":\"add_value\",\"equipment_slot\":\"any\"}]}";
    }

    private static EquipmentAttributeDefinition parse(String json) {
        return EquipmentAttributeDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static ItemStack equipment(ResourceLocation part) {
        var stack = new ItemStack(Items.IRON_SWORD);
        EquipmentStructureApi.setStructure(stack, new EquipmentHostDefinition(
                id("host"), BuiltinEquipmentTypes.SWORD,
                List.of(EquipmentSlotDefinition.of(SLOT, INTERFACE))).createStructure()
                .withComponents(SLOT, List.of(new EquipmentComponentInstance(part, INTERFACE))));
        return stack;
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "attribute_json/" + path); }
}
