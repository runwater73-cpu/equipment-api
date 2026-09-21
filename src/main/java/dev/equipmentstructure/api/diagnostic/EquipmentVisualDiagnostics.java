package dev.equipmentstructure.api.diagnostic;

import dev.equipmentstructure.api.EquipmentComponentDefinition;
import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.EquipmentHostDefinition;
import dev.equipmentstructure.api.ui.EquipmentStructureUiDefinition;
import dev.equipmentstructure.api.appearance.*;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static dev.equipmentstructure.api.diagnostic.EquipmentDiagnosticReport.Severity.*;

/** Pure checks over client-provided metadata; safe to use in tests without a renderer or client classes. */
public final class EquipmentVisualDiagnostics {
    private EquipmentVisualDiagnostics() {}

    public static EquipmentDiagnosticReport catalog(AppearanceCatalog catalog,
                                                    Map<ResourceLocation, EquipmentComponentDefinition> components,
                                                    Set<ResourceLocation> assets, Predicate<String> translated) {
        return catalog(catalog, components, assets, translated, Map.of());
    }

    public static EquipmentDiagnosticReport catalog(AppearanceCatalog catalog,
                                                    Map<ResourceLocation, EquipmentComponentDefinition> components,
                                                    Set<ResourceLocation> assets, Predicate<String> translated,
                                                    Map<ResourceLocation, EquipmentStructureUiDefinition> displays) {
        List<EquipmentDiagnosticReport.Issue> issues = new ArrayList<>();
        catalog.components().forEach((id, appearance) -> {
            if (!components.containsKey(id)) issues.add(new EquipmentDiagnosticReport.Issue(
                    WARNING, "appearance_component_unknown", id.toString(), ""));
            if (!assets.contains(appearance.asset())) issues.add(new EquipmentDiagnosticReport.Issue(
                    WARNING, "appearance_asset_unprepared", id.toString(), appearance.asset().toString()));
        });
        catalog.hosts().values().forEach(host -> host.bindings().keySet().forEach(slot -> {
            var display = displays.getOrDefault(host.id(), EquipmentStructureUiDefinition.defaults()).slot(slot);
            var key = display.nameKey(slot);
            if (!translated.test(key)) issues.add(new EquipmentDiagnosticReport.Issue(INFO, "translation_missing", slot.toString(), key));
        }));
        components.values().forEach(definition -> {
            translation(issues, "component_type", definition.componentType(), translated);
            translation(issues, "interface", definition.interfaceType(), translated);
        });
        return new EquipmentDiagnosticReport("client_resources", issues);
    }

    public static EquipmentDiagnosticReport slotDisplays(Map<ResourceLocation, EquipmentStructureUiDefinition> displays,
                                                         Map<ResourceLocation, EquipmentHostDefinition> hosts,
                                                         Predicate<String> translated, Predicate<ResourceLocation> spriteExists) {
        List<EquipmentDiagnosticReport.Issue> issues = new ArrayList<>();
        displays.forEach((host, ui) -> ui.slots().forEach((slot, display) -> {
            String subject = host + "/" + slot;
            var template = hosts.get(host);
            if (template != null && template.slots().stream().noneMatch(value -> value.id().equals(slot))) {
                issues.add(new EquipmentDiagnosticReport.Issue(WARNING, "slot_display_unused", subject, ""));
            }
            var keys = new ArrayList<>(display.descriptionKeys());
            keys.add(display.nameKey(slot));
            keys.stream().filter(key -> !translated.test(key)).forEach(key -> issues.add(
                    new EquipmentDiagnosticReport.Issue(INFO, "translation_missing", subject, key)));
            display.emptyIcon().filter(icon -> !spriteExists.test(icon)).ifPresent(icon -> issues.add(
                    new EquipmentDiagnosticReport.Issue(WARNING, "slot_icon_missing", subject, icon.toString())));
        }));
        return new EquipmentDiagnosticReport("client_slot_display", issues);
    }

    /** The caller must identify which renderer's support was supplied; this never proves a draw succeeded. */
    public static EquipmentDiagnosticReport held(EquipmentStructure structure, AppearanceCatalog catalog,
                                                AppearanceSupport support) {
        var plan = AppearanceResolver.resolve(structure, catalog, support);
        List<EquipmentDiagnosticReport.Issue> issues = new ArrayList<>();
        for (var outcome : plan.outcomes()) {
            if (outcome.status() == AppearancePlan.Status.READY) continue;
            boolean optional = outcome.reason() == AppearancePlan.Reason.HOST_UNCONFIGURED
                    || outcome.reason() == AppearancePlan.Reason.COMPONENT_UNCONFIGURED;
            issues.add(new EquipmentDiagnosticReport.Issue(optional ? INFO : WARNING,
                    optional ? "appearance_optional" : "appearance_fallback", outcome.slotId().toString(),
                    outcome.reason().name() + " / " + outcome.componentId()
                            + outcome.resource().map(value -> " / " + value).orElse("")));
        }
        return new EquipmentDiagnosticReport("client_held_default_item", issues);
    }

    private static void translation(List<EquipmentDiagnosticReport.Issue> issues, String prefix,
                                    ResourceLocation id, Predicate<String> translated) {
        var key = prefix + "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        if (!translated.test(key)) issues.add(new EquipmentDiagnosticReport.Issue(INFO, "translation_missing", id.toString(), key));
    }
}
