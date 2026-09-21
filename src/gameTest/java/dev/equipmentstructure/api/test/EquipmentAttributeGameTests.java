package dev.equipmentstructure.api.test;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.BuiltinEquipmentTypes;
import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentHostDefinition;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.attribute.EquipmentAttributeContribution;
import dev.equipmentstructure.api.attribute.EquipmentAttributeRegistry;
import dev.equipmentstructure.api.attribute.EquipmentAttributeResolver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(EquipmentStructureApiMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class EquipmentAttributeGameTests {
    private EquipmentAttributeGameTests() {
    }

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(EquipmentAttributeGameTests.class);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void calculationUsesMinecraftAttributeRules(GameTestHelper helper) {
        ItemAttributeModifiers modifiers = ItemAttributeModifiers.builder()
                .add(Attributes.MOVEMENT_SPEED, modifier("flat", 2,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.MOVEMENT_SPEED, modifier("base", 0.5,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.MOVEMENT_SPEED, modifier("total", 1,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.MAINHAND)
                .build();

        helper.assertTrue(EquipmentAttributeResolver.compute(
                        modifiers, Attributes.MOVEMENT_SPEED, 1, EquipmentSlot.MAINHAND) == 9.0,
                "calculation should use AttributeInstance operation phases");
        helper.assertTrue(EquipmentAttributeResolver.compute(
                        ItemAttributeModifiers.EMPTY, Attributes.MOVEMENT_SPEED, -1,
                        EquipmentSlot.MAINHAND) == 0.0,
                "calculation should use the attribute's vanilla range sanitization");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void duplicateProviderOutputIsNormalized(GameTestHelper helper) {
        ResourceLocation component = id("duplicate_component");
        ResourceLocation slot = id("duplicate_slot");
        EquipmentAttributeContribution contribution = EquipmentAttributeContribution.add(
                Attributes.MOVEMENT_SPEED, id("duplicate_speed"), 2.0D);
        EquipmentAttributeRegistry.register(component, context -> java.util.List.of(contribution, contribution));
        try {
            ItemStack stack = equipment(slot, component);
            double value = EquipmentAttributeResolver.value(
                    stack, Attributes.MOVEMENT_SPEED, 1.0D, EquipmentSlot.MAINHAND);
            helper.assertTrue(value == 3.0D,
                    "duplicate provider output must be applied once, value=" + value);
            helper.assertTrue(EquipmentAttributeResolver.details(stack).size() == 1,
                    "duplicate provider output must have one source detail");
        } finally {
            EquipmentAttributeRegistry.unregister(component);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void exclusiveContributionSuppressesOtherApiContributions(GameTestHelper helper) {
        ResourceLocation first = id("exclusive_stack");
        ResourceLocation second = id("exclusive_mode");
        ResourceLocation firstSlot = id("exclusive_first_slot");
        ResourceLocation secondSlot = id("exclusive_second_slot");
        EquipmentAttributeRegistry.register(first, context -> java.util.List.of(
                EquipmentAttributeContribution.add(
                        Attributes.MOVEMENT_SPEED, id("stack_speed"), 2.0D)));
        EquipmentAttributeRegistry.register(second, context -> java.util.List.of(
                EquipmentAttributeContribution.add(
                                Attributes.MOVEMENT_SPEED, id("mode_speed"), 5.0D)
                        .exclusive(10)));
        try {
            ItemStack stack = new ItemStack(Items.IRON_SWORD);
            EquipmentStructureApi.setStructure(stack, new EquipmentHostDefinition(
                    id("exclusive_host"), BuiltinEquipmentTypes.SWORD,
                    java.util.List.of(
                            EquipmentSlotDefinition.of(firstSlot, id("type")),
                            EquipmentSlotDefinition.of(secondSlot, id("type"))))
                    .createStructure()
                    .withComponents(firstSlot, java.util.List.of(new EquipmentComponentInstance(first, id("type"))))
                    .withComponents(secondSlot, java.util.List.of(new EquipmentComponentInstance(second, id("type")))));
            double value = EquipmentAttributeResolver.value(
                    stack, Attributes.MOVEMENT_SPEED, 1.0D, EquipmentSlot.MAINHAND);
            helper.assertTrue(value == 6.0D,
                    "exclusive contribution must suppress other API contributions, value=" + value);
        } finally {
            EquipmentAttributeRegistry.unregister(first);
            EquipmentAttributeRegistry.unregister(second);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void replacementIsScopedToTheQueriedEquipmentSlot(GameTestHelper helper) {
        ResourceLocation main = id("mainhand_replace");
        ResourceLocation off = id("offhand_replace");
        ResourceLocation mainSlot = id("mainhand_replace_slot");
        ResourceLocation offSlot = id("offhand_replace_slot");
        ResourceLocation key = id("hand_mode");
        EquipmentAttributeRegistry.register(main, context -> java.util.List.of(
                EquipmentAttributeContribution.add(
                                Attributes.MOVEMENT_SPEED, id("main_speed"), 2.0D)
                        .replaceBy(key, 1)
                        .when(value -> value.slot().id().equals(mainSlot))));
        EquipmentAttributeRegistry.register(off, context -> java.util.List.of(
                EquipmentAttributeContribution.add(
                                Attributes.MOVEMENT_SPEED, id("off_speed"), 5.0D,
                                EquipmentSlotGroup.OFFHAND)
                        .replaceBy(key, 2)));
        try {
            ItemStack stack = new ItemStack(Items.IRON_SWORD);
            EquipmentStructureApi.setStructure(stack, new EquipmentHostDefinition(
                    id("replace_host"), BuiltinEquipmentTypes.SWORD,
                    java.util.List.of(
                            EquipmentSlotDefinition.of(mainSlot, id("main_type")),
                            EquipmentSlotDefinition.of(offSlot, id("off_type"))))
                    .createStructure()
                    .withComponents(mainSlot, java.util.List.of(new EquipmentComponentInstance(main, id("main_type"))))
                    .withComponents(offSlot, java.util.List.of(new EquipmentComponentInstance(off, id("off_type")))));
            double mainValue = EquipmentAttributeResolver.value(
                    stack, Attributes.MOVEMENT_SPEED, 1.0D, EquipmentSlot.MAINHAND);
            double offValue = EquipmentAttributeResolver.value(
                    stack, Attributes.MOVEMENT_SPEED, 1.0D, EquipmentSlot.OFFHAND);
            helper.assertTrue(mainValue == 3.0D,
                    "offhand replacement must not suppress mainhand, value=" + mainValue);
            helper.assertTrue(offValue == 6.0D,
                    "offhand replacement should apply to offhand, value=" + offValue);
            helper.assertTrue(EquipmentAttributeResolver.contributions(
                            stack, mainSlot, EquipmentSlot.MAINHAND).size() == 1,
                    "mainhand contribution view should contain the effective winner");
            helper.assertTrue(EquipmentAttributeResolver.contributions(
                            stack, offSlot, EquipmentSlot.OFFHAND).size() == 1,
                    "offhand contribution view should contain the effective winner");

            ItemAttributeModifiers queried = stack.getAttributeModifiers();
            helper.assertTrue(EquipmentAttributeResolver.compute(
                            queried, Attributes.MOVEMENT_SPEED, 1.0D, EquipmentSlot.MAINHAND) == 3.0D,
                    "NeoForge event modifiers should match the mainhand resolver");
            helper.assertTrue(EquipmentAttributeResolver.compute(
                            queried, Attributes.MOVEMENT_SPEED, 1.0D, EquipmentSlot.OFFHAND) == 6.0D,
                    "NeoForge event modifiers should match the offhand resolver");
        } finally {
            EquipmentAttributeRegistry.unregister(main);
            EquipmentAttributeRegistry.unregister(off);
        }
        helper.succeed();
    }

    private static ItemStack equipment(ResourceLocation slot, ResourceLocation component) {
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        EquipmentStructureApi.setStructure(stack, new EquipmentHostDefinition(
                id("attribute_host"), BuiltinEquipmentTypes.SWORD,
                java.util.List.of(EquipmentSlotDefinition.of(slot, id("type"))))
                .createStructure()
                .withComponents(slot, java.util.List.of(new EquipmentComponentInstance(component, id("type")))));
        return stack;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "attribute/" + path);
    }

    private static AttributeModifier modifier(String path, double amount,
                                               AttributeModifier.Operation operation) {
        return new AttributeModifier(ResourceLocation.fromNamespaceAndPath(
                EquipmentStructureApiMod.MOD_ID, "test/" + path), amount, operation);
    }
}
