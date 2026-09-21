package dev.equipmentstructure.api.attribute;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.internal.ExtensionGuard;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Collections;

/**
 * Shared attribute calculation and event injection implementation.
 *
 * <p>It never mutates the ItemStack. Vanilla modifiers remain authoritative;
 * component modifiers are appended in stable structure order and can target
 * any vanilla or third-party attribute.</p>
 */
public final class EquipmentAttributeResolver {
    private static final ExtensionGuard<ResourceLocation> CONDITION_GUARD =
            new ExtensionGuard<>("Attribute condition");
    /** Reuses one provider resolution while NeoForge builds its modifier view. */
    private static final ThreadLocal<ActiveResolution> ACTIVE_RESOLUTION = new ThreadLocal<>();

    private EquipmentAttributeResolver() {
    }

    /** True when the item can no longer provide combat attributes. */
    public static boolean isBroken(ItemStack stack) {
        return stack.isEmpty() || (stack.getMaxDamage() > 0
                && stack.getDamageValue() >= stack.getMaxDamage());
    }

    /** Returns all component contributions in deterministic slot order. */
    public static List<EquipmentAttributeContribution> contributions(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (isBroken(stack)) return List.of();
        return resolved(stack).stream().map(ResolvedContribution::contribution).toList();
    }

    /** Returns active contributions together with their concrete source. */
    public static List<EquipmentAttributeDetail> details(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (isBroken(stack)) return List.of();
        return resolved(stack).stream().map(EquipmentAttributeResolver::detail).toList();
    }

    /** Returns active contributions from the component installed in one interface. */
    public static List<EquipmentAttributeContribution> contributions(
            ItemStack stack, ResourceLocation slotId) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(slotId, "slotId");
        if (isBroken(stack)) return List.of();
        return resolved(stack).stream()
                .filter(value -> value.context().slot().id().equals(slotId))
                .map(ResolvedContribution::contribution)
                .toList();
    }

    /**
     * Returns the contributions from one interface that survive stacking rules
     * for a concrete vanilla equipment slot.
     *
     * <p>Use this overload when rendering a hand/armor-specific view. The
     * overload without {@link EquipmentSlot} intentionally exposes every
     * condition-active contribution because one interface can target more than
     * one vanilla slot.</p>
     */
    public static List<EquipmentAttributeContribution> contributions(
            ItemStack stack, ResourceLocation slotId, EquipmentSlot equipmentSlot) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(equipmentSlot, "equipmentSlot");
        if (isBroken(stack)) return List.of();
        List<ResolvedContribution> active = activeForSlot(resolved(stack), equipmentSlot);
        return active.stream()
                .filter(value -> value.context().slot().id().equals(slotId))
                .map(ResolvedContribution::contribution)
                .toList();
    }

    /** Returns active contributions from one interface with their source. */
    public static List<EquipmentAttributeDetail> details(
            ItemStack stack, ResourceLocation slotId) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(slotId, "slotId");
        if (isBroken(stack)) return List.of();
        return resolved(stack).stream()
                .filter(value -> value.context().slot().id().equals(slotId))
                .map(EquipmentAttributeResolver::detail)
                .toList();
    }

    /** Returns source-aware contributions after resolving stacking for one vanilla slot. */
    public static List<EquipmentAttributeDetail> details(
            ItemStack stack, ResourceLocation slotId, EquipmentSlot equipmentSlot) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(equipmentSlot, "equipmentSlot");
        if (isBroken(stack)) return List.of();
        List<ResolvedContribution> active = activeForSlot(resolved(stack), equipmentSlot);
        return active.stream()
                .filter(value -> value.context().slot().id().equals(slotId))
                .map(EquipmentAttributeResolver::detail)
                .toList();
    }

    /** Adds all component modifiers to a queried vanilla item modifier event. */
    public static void addTo(ItemStack stack, net.neoforged.neoforge.event.ItemAttributeModifierEvent event) {
        if (isBroken(stack)) return;
        List<ResolvedContribution> resolved = resolvedForEvent(stack);
        Set<String> seen = new LinkedHashSet<>();
        // ItemAttributeModifierEvent has no queried slot. Expand contributions
        // to concrete slots so replacement and exclusive rules are resolved in
        // the same scope as the eventual AttributeInstance query.
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            for (ResolvedContribution value : activeForSlot(resolved, slot)) {
                EquipmentAttributeContext context = value.context();
                EquipmentAttributeContribution contribution = value.contribution();
                ResourceLocation scopedId = scopedModifierId(context, contribution, slot);
                String key = attributeKey(contribution.attribute()) + "|" + scopedId;
                if (!seen.add(key)) continue;
                event.addModifier(contribution.attribute(), new AttributeModifier(
                        scopedId, contribution.amount(), contribution.operation()),
                        EquipmentSlotGroup.bySlot(slot));
            }
        }
    }

    /**
     * Modifier IDs are scoped to component + interface. Two copies of one
     * component installed in different interfaces must both contribute, while
     * duplicate provider output in one interface remains safely de-duplicated.
     */
    private static ResourceLocation scopedModifierId(EquipmentAttributeContext context,
                                                     EquipmentAttributeContribution contribution) {
        return scopedModifierId(context, contribution, null);
    }

    private static ResourceLocation scopedModifierId(EquipmentAttributeContext context,
                                                     EquipmentAttributeContribution contribution,
                                                     EquipmentSlot slot) {
        String source = context.component().id() + "|" + context.slot().id()
                + "|" + contribution.modifierId()
                + (slot == null ? "" : "|" + slot.getSerializedName());
        String hash = UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        return ResourceLocation.fromNamespaceAndPath("equipment_structure_api", "component/" + hash);
    }

    /**
     * Computes a final value from the item's unmodified base modifiers. This
     * deliberately avoids ItemStack#getAttributeModifiers(), because that
     * NeoForge query already fires the event that injects these contributions.
     */
    public static double value(ItemStack stack, Holder<Attribute> attribute,
                               double baseValue, EquipmentSlot slot) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(slot, "slot");
        return evaluate(stack, attribute, baseValue, slot).finalValue();
    }

    /** Computes one attribute from an already queried modifier set. */
    public static double compute(ItemAttributeModifiers modifiers, Holder<Attribute> attribute,
                                 double baseValue, EquipmentSlot slot) {
        Objects.requireNonNull(modifiers, "modifiers");
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(slot, "slot");
        return calculate(modifiers.modifiers().stream()
                        .map(entry -> new CalculationEntry(entry.attribute(), entry.modifier(),
                                entry.slot(), null))
                        .toList(), attribute, baseValue, slot).finalValue();
    }

    /**
     * Captures the complete attribute view once for a GUI frame or tooltip.
     * Calling {@link View#result(Holder, double)} never invokes a provider again.
     */
    public static View view(ItemStack stack, EquipmentSlot slot) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(slot, "slot");
        List<ResolvedContribution> resolved = resolved(stack);
        return new View(baseModifiers(stack, resolved), resolved, slot);
    }

    /** Immutable calculation context backed by one resolved equipment read. */
    public static final class View {
        private final ItemAttributeModifiers baseModifiers;
        private final List<ResolvedContribution> resolved;
        private final EquipmentSlot slot;

        private View(ItemAttributeModifiers baseModifiers,
                     List<ResolvedContribution> resolved,
                     EquipmentSlot slot) {
            this.baseModifiers = baseModifiers;
            this.resolved = List.copyOf(resolved);
            this.slot = slot;
        }

        /** Attributes declared by the item or introduced by active components. */
        public Set<Holder<Attribute>> attributes() {
            LinkedHashSet<Holder<Attribute>> values = new LinkedHashSet<>();
            for (ItemAttributeModifiers.Entry entry : baseModifiers.modifiers()) {
                if (entry.slot().test(slot) && Math.abs(entry.modifier().amount()) > 1.0E-9D) {
                    values.add(entry.attribute());
                }
            }
            for (ResolvedContribution value : resolved) {
                EquipmentAttributeContribution contribution = value.contribution();
                if (activeForSlot(resolved, slot).contains(value)
                        && Math.abs(contribution.amount()) > 1.0E-9D) {
                    values.add(contribution.attribute());
                }
            }
            return Collections.unmodifiableSet(values);
        }

        public EquipmentAttributeValue result(Holder<Attribute> attribute, double baseValue) {
            Objects.requireNonNull(attribute, "attribute");
            if (!Double.isFinite(baseValue)) {
                throw new IllegalArgumentException("Attribute base value must be finite");
            }
            return EquipmentAttributeResolver.result(resolved, baseModifiers, attribute,
                    baseValue, slot);
        }

        /** Returns the active component contributions for one interface. */
        public List<EquipmentAttributeContribution> contributions(ResourceLocation slotId) {
            Objects.requireNonNull(slotId, "slotId");
            return resolved.stream()
                    .filter(value -> value.context().slot().id().equals(slotId))
                    .filter(value -> activeForSlot(resolved, slot).contains(value))
                    .map(ResolvedContribution::contribution)
                    .toList();
        }

        /** Returns source-aware contributions from one interface. */
        public List<EquipmentAttributeDetail> details(ResourceLocation slotId) {
            Objects.requireNonNull(slotId, "slotId");
            return resolved.stream()
                    .filter(value -> value.context().slot().id().equals(slotId))
                    .filter(value -> activeForSlot(resolved, slot).contains(value))
                    .map(EquipmentAttributeResolver::detail)
                    .toList();
        }
    }

    /** Convenience overload for the main-hand equipment view. */
    public static double mainHandValue(ItemStack stack, Holder<Attribute> attribute,
                                       double baseValue) {
        return value(stack, attribute, baseValue, EquipmentSlot.MAINHAND);
    }

    /** Resolves several attributes against one immutable equipment snapshot. */
    public static EquipmentAttributeSnapshot snapshot(ItemStack stack,
                                                      Map<Holder<Attribute>, Double> baseValues,
                                                      EquipmentSlot slot) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(baseValues, "baseValues");
        Objects.requireNonNull(slot, "slot");
        List<ResolvedContribution> activeResolved = resolved(stack);
        ItemAttributeModifiers baseModifiers = baseModifiers(stack, activeResolved);
        Map<Holder<Attribute>, EquipmentAttributeValue> values = new LinkedHashMap<>();
        for (Map.Entry<Holder<Attribute>, Double> entry : baseValues.entrySet()) {
            Holder<Attribute> attribute = Objects.requireNonNull(entry.getKey(), "attribute");
            double baseValue = Objects.requireNonNull(entry.getValue(), "base value");
            if (!Double.isFinite(baseValue)) {
                throw new IllegalArgumentException("Attribute base value must be finite");
            }
            values.put(attribute, result(activeResolved, baseModifiers, attribute, baseValue, slot));
        }
        return new EquipmentAttributeSnapshot(values);
    }

    /** Full value and contribution breakdown for GUI and diagnostics. */
    public static EquipmentAttributeValue result(ItemStack stack, Holder<Attribute> attribute,
                                                  double baseValue, EquipmentSlot slot) {
        List<ResolvedContribution> activeResolved = resolved(stack);
        ItemAttributeModifiers baseModifiers = baseModifiers(stack, activeResolved);
        return result(activeResolved, baseModifiers, attribute, baseValue, slot);
    }

    private static EquipmentAttributeValue result(List<ResolvedContribution> activeResolved,
                                                  ItemAttributeModifiers baseModifiers,
                                                  Holder<Attribute> attribute,
                                                  double baseValue,
                                                  EquipmentSlot slot) {
        List<CalculationEntry> entries = new ArrayList<>();
        for (ItemAttributeModifiers.Entry entry : baseModifiers.modifiers()) {
            entries.add(new CalculationEntry(entry.attribute(), entry.modifier(), entry.slot(), null));
        }
        List<ResolvedContribution> active = activeFor(activeResolved, attribute, slot);
        for (ResolvedContribution resolved : active) {
            EquipmentAttributeContribution contribution = resolved.contribution();
            entries.add(new CalculationEntry(contribution.attribute(),
                    new AttributeModifier(scopedModifierId(resolved.context(), contribution, slot), contribution.amount(),
                            contribution.operation()), contribution.slotGroup(), contribution));
        }
        return calculate(entries, attribute, baseValue, slot);
    }

    private static EquipmentAttributeValue evaluate(ItemStack stack, Holder<Attribute> attribute,
                                                     double baseValue, EquipmentSlot slot) {
        List<ResolvedContribution> activeResolved = resolved(stack);
        ItemAttributeModifiers baseModifiers = baseModifiers(stack, activeResolved);
        List<CalculationEntry> entries = new ArrayList<>();
        for (ItemAttributeModifiers.Entry entry : baseModifiers.modifiers()) {
            entries.add(new CalculationEntry(entry.attribute(), entry.modifier(), entry.slot(), null));
        }
        List<ResolvedContribution> active = activeFor(activeResolved, attribute, slot);
        for (ResolvedContribution resolved : active) {
            EquipmentAttributeContribution contribution = resolved.contribution();
            entries.add(new CalculationEntry(contribution.attribute(),
                    new AttributeModifier(scopedModifierId(resolved.context(), contribution, slot), contribution.amount(),
                            contribution.operation()), contribution.slotGroup(), contribution));
        }
        return calculate(entries, attribute, baseValue, slot);
    }

    /** Uses Minecraft's attribute implementation so operation order and range sanitization stay authoritative. */
    private static EquipmentAttributeValue calculate(List<CalculationEntry> entries,
                                                     Holder<Attribute> attribute,
                                                     double baseValue, EquipmentSlot slot) {
        AttributeInstance instance = new AttributeInstance(attribute, ignored -> {});
        instance.setBaseValue(baseValue);
        List<EquipmentAttributeContribution> used = new ArrayList<>();
        for (CalculationEntry entry : entries) {
            if (!entry.attribute().equals(attribute) || !entry.slot().test(slot)) {
                continue;
            }
            instance.addTransientModifier(entry.modifier());
            if (entry.contribution() != null) used.add(entry.contribution());
        }
        return new EquipmentAttributeValue(baseValue, instance.getValue(), used);
    }

    private static List<ResolvedContribution> activeFor(List<ResolvedContribution> resolved,
                                                         Holder<Attribute> attribute,
                                                         EquipmentSlot slot) {
        return activeForSlot(resolved, slot).stream()
                .filter(value -> value.contribution().attribute().equals(attribute))
                .toList();
    }

    /** Resolves stacking rules only among contributions applicable to one slot. */
    private static List<ResolvedContribution> activeForSlot(
            List<ResolvedContribution> resolved, EquipmentSlot slot) {
        List<ResolvedContribution> applicable = resolved.stream()
                .filter(value -> value.contribution().slotGroup().test(slot))
                .toList();
        Map<String, ResolvedContribution> replacements = new LinkedHashMap<>();
        Map<String, ResolvedContribution> exclusives = new LinkedHashMap<>();
        for (ResolvedContribution value : applicable) {
            EquipmentAttributeContribution contribution = value.contribution();
            String attributeKey = attributeKey(contribution.attribute());
            if (contribution.stacking() == EquipmentAttributeStacking.REPLACE) {
                replacements.merge(attributeKey + "|" + contribution.stackingKey(), value,
                        EquipmentAttributeResolver::higherPriority);
            } else if (contribution.stacking() == EquipmentAttributeStacking.EXCLUSIVE) {
                exclusives.merge(attributeKey, value, EquipmentAttributeResolver::higherPriority);
            }
        }
        Set<ResolvedContribution> replacementWinners = new LinkedHashSet<>(replacements.values());
        List<ResolvedContribution> result = new ArrayList<>();
        for (ResolvedContribution value : applicable) {
            EquipmentAttributeContribution contribution = value.contribution();
            String attributeKey = attributeKey(contribution.attribute());
            // EXCLUSIVE is a complete API contribution mode for one attribute;
            // vanilla and third-party item modifiers are outside this list and
            // remain untouched.
            if (exclusives.containsKey(attributeKey)) {
                if (exclusives.get(attributeKey).equals(value)) result.add(value);
            } else if (contribution.stacking() == EquipmentAttributeStacking.STACK
                    || replacementWinners.contains(value)) {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Returns the complete NeoForge modifier view, excluding only this API's
     * own component entries. Other mods' event listeners remain included.
     */
    private static ItemAttributeModifiers baseModifiers(ItemStack stack,
                                                        List<ResolvedContribution> resolved) {
        ActiveResolution previous = ACTIVE_RESOLUTION.get();
        ACTIVE_RESOLUTION.set(new ActiveResolution(stack, resolved));
        ItemAttributeModifiers modifiers;
        try {
            modifiers = stack.getAttributeModifiers();
        } finally {
            if (previous == null) {
                ACTIVE_RESOLUTION.remove();
            } else {
                ACTIVE_RESOLUTION.set(previous);
            }
        }
        Set<ResourceLocation> ownIds = new java.util.HashSet<>();
        for (ResolvedContribution value : resolved) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (value.contribution().slotGroup().test(slot)) {
                    ownIds.add(scopedModifierId(value.context(), value.contribution(), slot));
                }
            }
        }
        if (ownIds.isEmpty()) return modifiers;
        List<ItemAttributeModifiers.Entry> filtered = modifiers.modifiers().stream()
                .filter(entry -> !ownIds.contains(entry.modifier().id()))
                .toList();
        return new ItemAttributeModifiers(filtered, modifiers.showInTooltip());
    }

    private static List<ResolvedContribution> resolvedForEvent(ItemStack stack) {
        ActiveResolution active = ACTIVE_RESOLUTION.get();
        return active != null && active.stack() == stack ? active.resolved() : resolved(stack);
    }

    private record CalculationEntry(Holder<Attribute> attribute,
                                    AttributeModifier modifier,
                                    net.minecraft.world.entity.EquipmentSlotGroup slot,
                                    EquipmentAttributeContribution contribution) {
    }

    private record ResolvedContribution(EquipmentAttributeContext context,
                                        EquipmentAttributeContribution contribution) {}

    private static EquipmentAttributeDetail detail(ResolvedContribution resolved) {
        return new EquipmentAttributeDetail(resolved.context(), resolved.contribution());
    }

    private record ActiveResolution(ItemStack stack, List<ResolvedContribution> resolved) {}

    private static List<ResolvedContribution> resolved(ItemStack stack) {
        EquipmentStructure structure = EquipmentStructureApi.structure(stack).orElse(null);
        if (structure == null || isBroken(stack)) return List.of();
        List<ResolvedContribution> all = new ArrayList<>();
        for (EquipmentSlotDefinition slot : structure.slots()) {
            EquipmentComponentInstance component = structure.component(slot.id()).orElse(null);
            if (component == null) continue;
            EquipmentAttributeContext context = new EquipmentAttributeContext(stack, slot, component);
            for (EquipmentAttributeContribution contribution : EquipmentAttributeRegistry.resolve(context)) {
                boolean enabled = CONDITION_GUARD.call(component.id(),
                        () -> contribution.condition().test(context), false);
                if (enabled) {
                    all.add(new ResolvedContribution(context, contribution));
                }
            }
        }
        // Normalize duplicate provider output before every consumer sees it.
        // Event injection and previews therefore cannot disagree about a
        // repeated modifier ID from one component/interface.
        LinkedHashMap<String, ResolvedContribution> unique = new LinkedHashMap<>();
        for (ResolvedContribution value : all) {
            String key = attributeKey(value.contribution().attribute()) + "|"
                    + scopedModifierId(value.context(), value.contribution());
            unique.putIfAbsent(key, value);
        }
        return List.copyOf(unique.values());
    }

    private static String attributeKey(Holder<Attribute> attribute) {
        return attribute.unwrapKey().map(key -> key.location().toString()).orElse("unknown");
    }

    private static ResolvedContribution higherPriority(ResolvedContribution left,
                                                        ResolvedContribution right) {
        int priority = Integer.compare(left.contribution().priority(), right.contribution().priority());
        if (priority != 0) return priority > 0 ? left : right;
        // A stable tie-breaker keeps results deterministic when two providers
        // declare the same priority and stacking key.
        String leftKey = left.context().slot().id() + "|" + left.context().component().id()
                + "|" + left.contribution().modifierId();
        String rightKey = right.context().slot().id() + "|" + right.context().component().id()
                + "|" + right.contribution().modifierId();
        return leftKey.compareTo(rightKey) <= 0 ? left : right;
    }
}
