package dev.equipmentstructure.api.diagnostic;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.attribute.EquipmentAttributeData;
import dev.equipmentstructure.api.attribute.EquipmentAttributeRegistry;
import dev.equipmentstructure.api.internal.ExtensionGuard;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.equipmentstructure.api.diagnostic.EquipmentDiagnosticReport.Severity.*;

/** Read-only author diagnostics. No installation events, behavior callbacks, initialization or migration. */
public final class EquipmentIntegrationDiagnostics {
    private static final ExtensionGuard<String> PROBES = new ExtensionGuard<>("Equipment diagnostics");
    private EquipmentIntegrationDiagnostics() {}

    /** Metadata only: no user providers, readers or factories are invoked by this scan. */
    public static EquipmentDiagnosticReport registrations(RegistryAccess registries) {
        List<EquipmentDiagnosticReport.Issue> issues = new ArrayList<>();
        var components = EquipmentComponentRegistry.definitions();
        for (var definition : components.values()) {
            checkDefinition(issues, definition);
            checkInterface(issues, definition.id(), definition.interfaceType());
        }
        for (var binding : EquipmentComponentItemAdapters.bindings()) {
            if (!binding.definitionCurrent()) add(issues, ERROR, "adapter_definition_changed", binding.id(), binding.componentId());
        }
        for (var binding : EquipmentComponentItemAdapters.tagBindings()) {
            if (!binding.definitionCurrent()) add(issues, ERROR, "adapter_definition_changed", binding.id(), binding.componentId());
            if (BuiltInRegistries.ITEM.getTag(binding.tag()).map(tag -> tag.size() == 0).orElse(true)) {
                add(issues, INFO, "adapter_tag_empty", binding.id(), binding.tag().location());
            }
        }
        for (var id : EquipmentAttributeRegistry.registeredIds()) {
            if (!components.containsKey(id)) add(issues, WARNING, "attribute_component_unknown", id, "Java");
        }
        for (var id : EquipmentComponentBehaviorRegistry.registeredIds()) {
            if (!components.containsKey(id)) add(issues, WARNING, "behavior_component_unknown", id, "");
        }
        for (var entry : EquipmentAttributeData.definitions().entrySet()) {
            var id = entry.getKey();
            if (!components.containsKey(id)) add(issues, WARNING, "attribute_component_unknown", id, "JSON");
            if (EquipmentAttributeRegistry.get(id).isPresent()) add(issues, INFO, "json_overrides_java", id, "");
            if (entry.getValue().modifiers().isEmpty()) add(issues, INFO, "json_disables_attributes", id, "");
        }
        var hosts = registries.registry(EquipmentStructureRegistries.HOST_DEFINITION);
        hosts.ifPresent(registry -> registry.entrySet().forEach(entry -> {
            var key = entry.getKey().location();
            var host = entry.getValue();
            if (!key.equals(host.id())) add(issues, ERROR, "host_id_mismatch", key, host.id());
            for (var slot : host.slots()) checkInterface(issues, key + "/" + slot.id(), slot.interfaceType());
        }));
        EquipmentHostProviders.fixedBindings().forEach((item, host) -> {
            if (hosts.flatMap(registry -> registry.getOptional(host)).isEmpty()) {
                add(issues, ERROR, "host_binding_missing", BuiltInRegistries.ITEM.getKey(item), host);
            }
        });
        BuiltInRegistries.ITEM.holders().forEach(holder -> {
            var binding = holder.getData(EquipmentStructureRegistries.HOST_BINDING);
            if (binding != null && hosts.flatMap(registry -> registry.getOptional(binding.host())).isEmpty()) {
                add(issues, WARNING, "datamap_host_missing", holder.key().location(), binding.host());
            }
        });
        return new EquipmentDiagnosticReport("registrations", issues);
    }

    /** Probes actual held data on a defensive copy; a registered factory is never called with invented empty data. */
    public static EquipmentDiagnosticReport held(ItemStack input, RegistryAccess registries) {
        return PROBES.call("held", () -> inspectHeld(input.copy(), registries),
                new EquipmentDiagnosticReport("held", List.of(new EquipmentDiagnosticReport.Issue(
                        ERROR, "probe_failed", "held", ""))));
    }

    private static EquipmentDiagnosticReport inspectHeld(ItemStack stack, RegistryAccess registries) {
        List<EquipmentDiagnosticReport.Issue> issues = new ArrayList<>();
        if (stack.isEmpty()) {
            add(issues, INFO, "held_empty", "held", "");
            return new EquipmentDiagnosticReport("held", issues);
        }
        var item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        var structure = EquipmentStructureApi.structure(stack);
        if (structure.isPresent()) {
            var value = structure.get();
            var template = registries.registry(EquipmentStructureRegistries.HOST_DEFINITION)
                    .flatMap(registry -> registry.getOptional(value.hostId()));
            if (template.isEmpty()) add(issues, INFO, "snapshot_host_unlisted", item, value.hostId());
            else if (template.get().version() != value.version()) add(issues, INFO, "snapshot_version_differs",
                    value.hostId(), value.version() + " -> " + template.get().version());
            for (var slot : value.slots()) {
                String subject = value.hostId() + "/" + slot.id();
                checkInterface(issues, subject, slot.interfaceType());
                var installed = value.component(slot.id());
                if (installed.isEmpty()) {
                    continue;
                }
                var component = installed.get();
                if (!slot.accepts(value.equipmentType(), component))
                    add(issues, ERROR, "installed_incompatible", subject, component.id());
                inspectComponent(issues, component, subject, null);
            }
        } else {
            var host = EquipmentHostProviders.resolve(stack.copy(), registries);
            if (host.isPresent()) {
                boolean found = registries.registry(EquipmentStructureRegistries.HOST_DEFINITION)
                        .flatMap(registry -> registry.getOptional(host.get())).isPresent();
                add(issues, found ? INFO : ERROR, found ? "host_not_initialized" : "host_binding_missing", item, host.get());
            }
        }
        var probe = EquipmentComponentInspection.inspect(stack);
        if (probe.status() == EquipmentComponentInspection.Status.MATCH) {
            inspectComponent(issues, probe.component().orElseThrow(), item.toString(), stack);
        } else if (probe.status() != EquipmentComponentInspection.Status.NO_MATCH) {
            add(issues, ERROR, "item_" + probe.status().name().toLowerCase(Locale.ROOT), item,
                    probe.adapter().map(Object::toString).orElse("native"));
        } else if (structure.isEmpty() && issues.isEmpty()) add(issues, INFO, "item_unrecognized", item, "");
        return new EquipmentDiagnosticReport("held", issues);
    }

    private static void inspectComponent(List<EquipmentDiagnosticReport.Issue> issues,
                                         EquipmentComponentInstance component, String subject, ItemStack original) {
        var definition = EquipmentComponentRegistry.get(component.id());
        if (definition.isEmpty()) {
            add(issues, ERROR, "component_unregistered", subject, component.id());
            return;
        }
        var registered = definition.get();
        var actualInterface = component.interfaceType().orElseThrow();
        if (!registered.componentType().equals(component.componentType())
                || !registered.interfaceType().equals(actualInterface)) {
            add(issues, ERROR, "component_identity_changed", subject, component.id());
        }
        checkDefinition(issues, registered);
        if (registered.itemFactory().isEmpty()) return;
        var restored = EquipmentComponentRegistry.createItemStack(component).orElse(ItemStack.EMPTY);
        if (restored.isEmpty()) {
            add(issues, ERROR, "component_restore_failed", subject, component.id());
            return;
        }
        if (original != null && !ItemStack.isSameItemSameComponents(original, restored)) {
            add(issues, ERROR, "component_item_lossy", subject, component.id());
        }
        var reread = EquipmentComponentInspection.inspect(restored);
        if (reread.status() != EquipmentComponentInspection.Status.MATCH
                || reread.component().filter(value -> EquipmentComponentInspection.sameInstance(component, value)).isEmpty()) {
            add(issues, ERROR, "component_data_lossy", subject, component.id());
        }
    }

    private static void checkDefinition(List<EquipmentDiagnosticReport.Issue> issues, EquipmentComponentDefinition definition) {
        if (definition.removable() && definition.itemFactory().isEmpty()) {
            add(issues, WARNING, "component_factory_missing", definition.id(), "");
        }
    }

    private static void checkInterface(List<EquipmentDiagnosticReport.Issue> issues, Object owner, ResourceLocation id) {
        // Registration supplies reusable defaults; explicit exact-match interfaces remain legal.
        if (!EquipmentInterfaceRegistry.isRegistered(id)) add(issues, INFO, "interface_unlisted", owner, id);
    }

    private static void add(List<EquipmentDiagnosticReport.Issue> issues, EquipmentDiagnosticReport.Severity severity,
                            String code, Object subject, Object reference) {
        issues.add(new EquipmentDiagnosticReport.Issue(severity, code, subject.toString(), reference.toString()));
    }
}
