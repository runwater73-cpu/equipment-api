package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentComponentRegistry;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.attribute.EquipmentAttributeContribution;
import dev.equipmentstructure.api.attribute.EquipmentAttributeResolver;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import dev.equipmentstructure.api.ui.EquipmentStructureUiDefinition;
import dev.equipmentstructure.api.ui.EquipmentStructureUiRegistry;
import dev.equipmentstructure.api.ui.EquipmentComponentDisplayContext;
import dev.equipmentstructure.api.ui.EquipmentComponentDisplayLine;
import dev.equipmentstructure.api.ui.EquipmentComponentDisplayRegistry;
import dev.equipmentstructure.api.ui.EquipmentItemView;
import dev.equipmentstructure.api.ui.EquipmentDisplayRegistry;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable data read for one GUI frame.
 *
 * <p>The screen, panel renderers and layout calculations consume this object
 * only. They do not query the menu, ItemStack, registries or attribute event
 * system while drawing. This keeps the text, wrapping and panel dimensions on
 * one consistent view even when an addon supplies dynamic data.</p>
 */
public record EquipmentAssemblyDisplaySnapshot(
        ItemStack equipment,
        Optional<ResourceLocation> hostId,
        EquipmentStructureUiDefinition uiDefinition,
        List<EquipmentSlotDefinition> definitions,
        Component equipmentName,
        List<EquipmentStatRow> equipmentStats,
        List<EquipmentStatRow> attributeDetails,
        Map<ResourceLocation, ComponentInfoSnapshot> componentInfo,
        Map<ResourceLocation, Component> interfaceNames
) {
    private static final DecimalFormat STAT_NUMBER_FORMAT = new DecimalFormat(
            "0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final DecimalFormat CONTRIBUTION_NUMBER_FORMAT = new DecimalFormat(
            "0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    public EquipmentAssemblyDisplaySnapshot {
        Objects.requireNonNull(equipment, "equipment");
        Objects.requireNonNull(hostId, "hostId");
        Objects.requireNonNull(uiDefinition, "uiDefinition");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(equipmentName, "equipmentName");
        Objects.requireNonNull(equipmentStats, "equipmentStats");
        Objects.requireNonNull(componentInfo, "componentInfo");
        Objects.requireNonNull(interfaceNames, "interfaceNames");
        equipment = equipment.copy();
        definitions = List.copyOf(definitions);
        equipmentStats = List.copyOf(equipmentStats);
        attributeDetails = List.copyOf(attributeDetails);
        componentInfo = Map.copyOf(componentInfo);
        interfaceNames = Map.copyOf(interfaceNames);
    }

    /** Returns a defensive copy so display code cannot mutate the captured frame. */
    @Override
    public ItemStack equipment() {
        return equipment.copy();
    }

    /** Reads all data used by the assembly screen before any pixels are drawn. */
    public static EquipmentAssemblyDisplaySnapshot capture(
            EquipmentAssemblyMenu menu, ResourceLocation selectedInterface) {
        Objects.requireNonNull(menu, "menu");

        // Take one copy and use it for every subsequent query in this frame.
        EquipmentItemView itemView = EquipmentItemView.capture(menu.equipmentStack());
        ItemStack equipment = itemView.stack();
        List<EquipmentSlotDefinition> definitions = itemView.slots();
        Optional<ResourceLocation> hostId = itemView.hostId();
        EquipmentStructureUiDefinition uiDefinition = hostId
                .map(id -> EquipmentStructureUiRegistry.get(id, definitions))
                .orElseGet(EquipmentStructureUiDefinition::defaults);

        EquipmentAttributeResolver.View attributeView =
                EquipmentAttributeResolver.view(equipment, equipmentSlot(equipment));
        List<EquipmentStatRow> stats = new ArrayList<>(readEquipmentStats(equipment, definitions,
                uiDefinition, attributeView));
        EquipmentDisplayRegistry.resolve(itemView).ifPresent(display -> {
            if (display.replaceDefaults()) stats.clear();
            display.rows().forEach(row -> stats.add(new EquipmentStatRow(row.label(), row.value(), row.color())));
        });
        Map<ResourceLocation, ComponentInfoSnapshot> componentInfo =
                readComponentInfo(equipment, definitions, uiDefinition, attributeView);
        List<EquipmentStatRow> details = new ArrayList<>(stats);
        if (net.neoforged.fml.ModList.get().isLoaded("curios"))
            dev.equipmentstructure.api.compat.curios.client.CuriosDetailPresentation.appendAttributes(componentInfo, definitions, details);
        Map<ResourceLocation, Component> interfaceNames = new LinkedHashMap<>();
        for (EquipmentSlotDefinition definition : definitions) {
            interfaceNames.put(definition.id(), uiDefinition.slot(definition.id()).name(definition.id()));
        }
        return new EquipmentAssemblyDisplaySnapshot(equipment, hostId, uiDefinition,
                definitions, equipment.getHoverName(), stats, details, componentInfo, interfaceNames);
    }

    public Optional<ComponentInfoSnapshot> selectedComponent(ResourceLocation selectedInterface) {
        if (selectedInterface == null) return Optional.empty();
        return Optional.ofNullable(componentInfo.get(selectedInterface));
    }

    /** Returns the captured display name for an interface. */
    public Component interfaceName(ResourceLocation interfaceId) {
        return interfaceNames.getOrDefault(interfaceId, Component.literal(interfaceId.toString()));
    }

    public List<Component> interfaceTooltip(ResourceLocation interfaceId) {
        return uiDefinition.slot(interfaceId).tooltip(interfaceId);
    }

    private static List<EquipmentStatRow> readEquipmentStats(
            ItemStack equipment, List<EquipmentSlotDefinition> definitions,
            EquipmentStructureUiDefinition uiDefinition,
            EquipmentAttributeResolver.View attributeView) {
        EquipmentStructureUiDefinition.Texts labels = uiDefinition.texts();
        List<EquipmentStatRow> rows = new ArrayList<>();
        String durability = equipment.getMaxDamage() > 0
                ? (equipment.getMaxDamage() - equipment.getDamageValue()) + "/" + equipment.getMaxDamage()
                : "-";
        rows.add(new EquipmentStatRow(Component.translatable(labels.online()),
                Component.literal(durability), 0xFFFFFFFF));

        // Read the complete vanilla/NeoForge modifier view once. The resolver
        // then calculates each displayed attribute from that same view.
        for (Holder<Attribute> attribute : attributeView.attributes()) {
            double value = attributeView.result(attribute, displayBaseValue(attribute)).finalValue();
            rows.add(new EquipmentStatRow(attributeLabel(attribute, labels),
                    Component.literal(formatStatNumber(value)), 0xFFFFFFFF));
        }

        rows.add(new EquipmentStatRow(Component.translatable(labels.interfaces()),
                Component.literal(Integer.toString(definitions.size())), 0xFFFFFFFF));
        return List.copyOf(rows);
    }

    private static Map<ResourceLocation, ComponentInfoSnapshot> readComponentInfo(
            ItemStack equipment, List<EquipmentSlotDefinition> definitions,
            EquipmentStructureUiDefinition uiDefinition,
            EquipmentAttributeResolver.View attributeView) {
        Map<ResourceLocation, ComponentInfoSnapshot> result = new LinkedHashMap<>();
        EquipmentStructureUiDefinition.Texts labels = uiDefinition.texts();
        for (EquipmentSlotDefinition definition : definitions) {
            Optional<EquipmentComponentInstance> installed =
                    EquipmentStructureApi.component(equipment, definition.id());
            ItemStack componentStack = installed.flatMap(EquipmentComponentRegistry::createItemStack)
                    .orElse(ItemStack.EMPTY).copy();
            Component componentName = componentStack.isEmpty()
                    ? Component.empty() : componentStack.getHoverName();
            List<InfoLine> lines = new ArrayList<>();
            if (installed.isPresent()) {
                EquipmentComponentInstance component = installed.get();
                lines.add(new InfoLine(Component.translatable(labels.installedComponent()), 0xFFB8B8B8));
                boolean nativeCurio = net.neoforged.fml.ModList.get().isLoaded("curios")
                        && dev.equipmentstructure.api.compat.curios.CuriosSlotKey.parse(definition.id()).isPresent();
                if (nativeCurio) {
                    dev.equipmentstructure.api.compat.curios.client.CuriosDetailPresentation.appendComponent(
                            equipment, definition, componentStack, lines);
                } else {
                    lines.add(new InfoLine(labeled(labels.componentType(), component.componentType(),
                            "component_type"), 0xFFAAAAAA));
                    lines.add(new InfoLine(labeled(labels.componentInterface(), definition.interfaceType(),
                            "interface"), 0xFFAAAAAA));

                    List<EquipmentAttributeContribution> contributions =
                            attributeView.contributions(definition.id());
                    if (!contributions.isEmpty()) {
                        String attributes = contributions.stream()
                                .map(EquipmentAssemblyDisplaySnapshot::formatContribution)
                                .reduce((left, right) -> left + ", " + right).orElse("");
                        lines.add(new InfoLine(Component.translatable(labels.componentAttributes())
                                .append(": ").append(attributes), 0xFFAAAAAA));
                    }
                    String stateKey = EquipmentComponentRegistry.get(component.id())
                            .map(value -> value.removable() ? labels.removable() : labels.locked())
                            .orElse(labels.unknownComponent());
                    lines.add(new InfoLine(Component.translatable(stateKey), 0xFF888888));
                }

                // A component author may append or replace the standard rows
                // with data derived from the immutable component context.
                EquipmentComponentDisplayRegistry.resolve(
                        new EquipmentComponentDisplayContext(equipment, definition, component))
                        .ifPresent(display -> {
                            if (display.replaceDefaults()) {
                                lines.clear();
                            }
                            for (EquipmentComponentDisplayLine line : display.lines()) {
                                lines.add(new InfoLine(line.text(), line.color()));
                            }
                        });
            }
            result.put(definition.id(), new ComponentInfoSnapshot(definition, installed,
                    componentStack, componentName, lines));
        }
        return Map.copyOf(result);
    }

    private static Component labeled(String labelKey, ResourceLocation value, String valueKind) {
        return Component.translatable(labelKey).append(": ").append(
                Component.translatableWithFallback(
                        valueKind + "." + value.getNamespace() + "." + value.getPath().replace('/', '.'),
                        value.toString()));
    }

    private static String formatContribution(EquipmentAttributeContribution contribution) {
        String name;
        try {
            name = Component.translatable(contribution.attribute().value().getDescriptionId()).getString();
        } catch (RuntimeException exception) {
            name = contribution.attribute().unwrapKey()
                    .map(key -> key.location().toString()).orElse("attribute");
        }
        String amount;
        synchronized (CONTRIBUTION_NUMBER_FORMAT) {
            amount = CONTRIBUTION_NUMBER_FORMAT.format(contribution.amount());
        }
        return switch (contribution.operation()) {
            case ADD_VALUE -> name + " " + (contribution.amount() >= 0 ? "+" : "") + amount;
            case ADD_MULTIPLIED_BASE, ADD_MULTIPLIED_TOTAL -> name + " "
                    + (contribution.amount() >= 0 ? "+" : "") + amount + "x";
        };
    }

    private static Component attributeLabel(Holder<Attribute> attribute,
                                            EquipmentStructureUiDefinition.Texts labels) {
        if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
            return Component.translatable(labels.attackDamage());
        }
        if (attribute.equals(Attributes.ATTACK_SPEED)) {
            return Component.translatable(labels.attackSpeed());
        }
        return Component.translatable(attribute.value().getDescriptionId());
    }

    private static double displayBaseValue(Holder<Attribute> attribute) {
        if (attribute.equals(Attributes.ATTACK_DAMAGE)) return 1.0D;
        if (attribute.equals(Attributes.ATTACK_SPEED)) return 4.0D;
        return 0.0D;
    }

    private static EquipmentSlot equipmentSlot(ItemStack stack) {
        EquipmentSlot slot = stack.getEquipmentSlot();
        if (slot != null) return slot;
        Equipable equipable = Equipable.get(stack);
        return equipable == null ? EquipmentSlot.MAINHAND : equipable.getEquipmentSlot();
    }

    private static String formatStatNumber(double value) {
        synchronized (STAT_NUMBER_FORMAT) {
            return STAT_NUMBER_FORMAT.format(value);
        }
    }

    public record EquipmentStatRow(Component label, Component value, int color) {
        public EquipmentStatRow {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(value, "value");
        }
    }

    public record InfoLine(Component text, int color) {
        public InfoLine {
            Objects.requireNonNull(text, "text");
        }
    }

    public record ComponentInfoSnapshot(
            EquipmentSlotDefinition definition,
            Optional<EquipmentComponentInstance> installed,
            ItemStack itemStack,
            Component itemName,
            List<InfoLine> lines
    ) {
        public ComponentInfoSnapshot {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(installed, "installed");
            Objects.requireNonNull(itemStack, "itemStack");
            Objects.requireNonNull(itemName, "itemName");
            Objects.requireNonNull(lines, "lines");
            itemStack = itemStack.copy();
            lines = List.copyOf(lines);
        }

        @Override
        public ItemStack itemStack() {
            return itemStack.copy();
        }
    }
}
