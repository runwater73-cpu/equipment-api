package dev.equipmentstructure.api.attribute;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.Objects;

/**
 * One attribute modifier contributed by an installed equipment component.
 *
 * <p>The modifier id is the component author's stable contribution key. The
 * resolver scopes it by component and interface before sending it to NeoForge,
 * so the same component can safely be installed more than once.
 * The record deliberately mirrors vanilla's three operations so content mods
 * do not need a second arithmetic convention.</p>
 */
public record EquipmentAttributeContribution(
        Holder<Attribute> attribute,
        ResourceLocation modifierId,
        double amount,
        AttributeModifier.Operation operation,
        EquipmentSlotGroup slotGroup,
        int priority,
        ResourceLocation stackingKey,
        EquipmentAttributeStacking stacking,
        EquipmentAttributeCondition condition
) {
    /** Creates a contribution with ordinary additive stacking. */
    public EquipmentAttributeContribution(
            Holder<Attribute> attribute,
            ResourceLocation modifierId,
            double amount,
            AttributeModifier.Operation operation,
            EquipmentSlotGroup slotGroup
    ) {
        this(attribute, modifierId, amount, operation, slotGroup, 0, modifierId,
                EquipmentAttributeStacking.STACK, EquipmentAttributeCondition.ALWAYS);
    }

    public EquipmentAttributeContribution {
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(modifierId, "modifierId");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(slotGroup, "slotGroup");
        Objects.requireNonNull(stackingKey, "stackingKey");
        Objects.requireNonNull(stacking, "stacking");
        Objects.requireNonNull(condition, "condition");
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("Attribute contribution amount must be finite");
        }
    }

    /** Creates a copy with a higher/lower conflict priority. */
    public EquipmentAttributeContribution priority(int value) {
        return new EquipmentAttributeContribution(attribute, modifierId, amount, operation,
                slotGroup, value, stackingKey, stacking, condition);
    }

    /** Contributions with the same key can replace one another by priority. */
    public EquipmentAttributeContribution replaceBy(ResourceLocation key, int value) {
        return new EquipmentAttributeContribution(attribute, modifierId, amount, operation,
                slotGroup, value, key, EquipmentAttributeStacking.REPLACE, condition);
    }

    /** Marks this attribute as an exclusive mode for the target attribute. */
    public EquipmentAttributeContribution exclusive(int value) {
        return new EquipmentAttributeContribution(attribute, modifierId, amount, operation,
                slotGroup, value, stackingKey, EquipmentAttributeStacking.EXCLUSIVE, condition);
    }

    /** Enables this contribution only while the supplied context predicate is true. */
    public EquipmentAttributeContribution when(EquipmentAttributeCondition predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return new EquipmentAttributeContribution(attribute, modifierId, amount, operation,
                slotGroup, priority, stackingKey, stacking, condition.and(predicate));
    }

    /** Restricts this contribution to one concrete installation point. */
    public EquipmentAttributeContribution onlyInSlot(ResourceLocation slotId) {
        Objects.requireNonNull(slotId, "slotId");
        return when(context -> context.slot().id().equals(slotId));
    }
    /** Gates this contribution through the same named spatial rule used by behaviors and layout previews. */
    public EquipmentAttributeContribution whenGridRule(ResourceLocation ruleId) {
        return when(context -> context.equipmentStructure().map(structure -> dev.equipmentstructure.api.grid.synergy.GridRuleRegistry
                .result(structure, ruleId, context.slot().id()).active()).orElse(false));
    }

    /** Restricts this contribution to an interface category shared by slots. */
    public EquipmentAttributeContribution onlyInInterface(ResourceLocation interfaceType) {
        Objects.requireNonNull(interfaceType, "interfaceType");
        return when(context -> context.slot().interfaceType().equals(interfaceType));
    }

    public AttributeModifier modifier() {
        return new AttributeModifier(modifierId, amount, operation);
    }

    public static EquipmentAttributeContribution add(
            Holder<Attribute> attribute,
            ResourceLocation modifierId,
            double amount,
            EquipmentSlotGroup slotGroup
    ) {
        return new EquipmentAttributeContribution(attribute, modifierId, amount,
                AttributeModifier.Operation.ADD_VALUE, slotGroup);
    }

    /** Main-hand convenience form for weapon and tool components. */
    public static EquipmentAttributeContribution add(
            Holder<Attribute> attribute, ResourceLocation modifierId, double amount
    ) {
        return add(attribute, modifierId, amount, EquipmentSlotGroup.MAINHAND);
    }

    public static EquipmentAttributeContribution multiplyBase(
            Holder<Attribute> attribute,
            ResourceLocation modifierId,
            double amount,
            EquipmentSlotGroup slotGroup
    ) {
        return new EquipmentAttributeContribution(attribute, modifierId, amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE, slotGroup);
    }

    public static EquipmentAttributeContribution multiplyBase(
            Holder<Attribute> attribute, ResourceLocation modifierId, double amount
    ) {
        return multiplyBase(attribute, modifierId, amount, EquipmentSlotGroup.MAINHAND);
    }

    public static EquipmentAttributeContribution multiplyTotal(
            Holder<Attribute> attribute,
            ResourceLocation modifierId,
            double amount,
            EquipmentSlotGroup slotGroup
    ) {
        return new EquipmentAttributeContribution(attribute, modifierId, amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, slotGroup);
    }

    public static EquipmentAttributeContribution multiplyTotal(
            Holder<Attribute> attribute, ResourceLocation modifierId, double amount
    ) {
        return multiplyTotal(attribute, modifierId, amount, EquipmentSlotGroup.MAINHAND);
    }
}
