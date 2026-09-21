package dev.equipmentstructure.api.ui;

import net.minecraft.resources.ResourceLocation;
import dev.equipmentstructure.api.EquipmentHostDefinition;
import dev.equipmentstructure.api.EquipmentSlotDefinition;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Registry for author-controlled presentation metadata, separate from structure data. */
public final class EquipmentStructureUiRegistry {
    private static final Map<ResourceLocation, EquipmentStructureUiDefinition> DEFINITIONS =
            new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, EquipmentSlotDisplay> INTERFACES = new ConcurrentHashMap<>();
    private static volatile ResourceDisplays resources = new ResourceDisplays(Map.of(), Map.of());

    private record ResourceDisplays(Map<ResourceLocation, Map<ResourceLocation, EquipmentSlotDisplay>> hosts,
                                    Map<ResourceLocation, EquipmentSlotDisplay> interfaces) {}

    private EquipmentStructureUiRegistry() {
    }

    public static void register(ResourceLocation hostId, EquipmentStructureUiDefinition definition) {
        ResourceLocation checkedHost = Objects.requireNonNull(hostId, "hostId");
        EquipmentStructureUiDefinition checkedDefinition =
                Objects.requireNonNull(definition, "definition");
        EquipmentStructureUiDefinition previous = DEFINITIONS.putIfAbsent(checkedHost,
                checkedDefinition);
        if (previous != null && !previous.equals(checkedDefinition)) {
            throw new IllegalStateException("Equipment UI definition already registered: " + checkedHost);
        }
    }

    public static EquipmentStructureUiDefinition get(ResourceLocation hostId) {
        return resolve(hostId, List.of(), resources);
    }

    /** Resolves display defaults against the actual slots, including dynamically added slots. */
    public static EquipmentStructureUiDefinition get(ResourceLocation hostId, List<EquipmentSlotDefinition> slots) {
        return resolve(hostId, List.copyOf(slots), resources);
    }

    /** Registers presentation only; this neither registers an interface nor grants installation permission. */
    public static void registerInterface(ResourceLocation interfaceId, EquipmentSlotDisplay display) {
        Objects.requireNonNull(interfaceId, "interfaceId");
        Objects.requireNonNull(display, "display");
        var previous = INTERFACES.putIfAbsent(interfaceId, display);
        if (previous != null && !previous.equals(display)) {
            throw new IllegalStateException("Equipment interface display already registered: " + interfaceId);
        }
    }

    public static boolean unregisterInterface(ResourceLocation interfaceId) {
        return INTERFACES.remove(Objects.requireNonNull(interfaceId, "interfaceId")) != null;
    }

    private static EquipmentStructureUiDefinition resolve(ResourceLocation hostId, List<EquipmentSlotDefinition> actualSlots,
                                                           ResourceDisplays generation) {
        var base = DEFINITIONS.getOrDefault(Objects.requireNonNull(hostId, "hostId"),
                EquipmentStructureUiDefinition.defaults());
        var slots = new java.util.HashMap<ResourceLocation, EquipmentSlotDisplay>();
        for (var slot : actualSlots) {
            var display = generation.interfaces().getOrDefault(slot.interfaceType(), INTERFACES.get(slot.interfaceType()));
            if (display != null) slots.put(slot.id(), display);
        }
        // Whole-entry overrides preserve the existing meaning of an explicit empty {}.
        slots.putAll(base.slots());
        slots.putAll(generation.hosts().getOrDefault(hostId, Map.of()));
        if (slots.equals(base.slots())) return base;
        return new EquipmentStructureUiDefinition(base.accentColor(), base.texts(), slots);
    }

    /** Client resource-loader lifecycle; replace the complete generation, preserving Java defaults. */
    public static void replaceResourceSlots(Map<ResourceLocation, Map<ResourceLocation, EquipmentSlotDisplay>> resources) {
        replaceResourceDisplays(resources, EquipmentStructureUiRegistry.resources.interfaces());
    }

    /** Publishes host and interface resources together so a failed reload cannot mix generations. */
    public static void replaceResourceDisplays(Map<ResourceLocation, Map<ResourceLocation, EquipmentSlotDisplay>> hosts,
                                               Map<ResourceLocation, EquipmentSlotDisplay> interfaces) {
        var copy = new java.util.HashMap<ResourceLocation, Map<ResourceLocation, EquipmentSlotDisplay>>();
        hosts.forEach((host, slots) -> copy.put(host, Map.copyOf(slots)));
        resources = new ResourceDisplays(Map.copyOf(copy), Map.copyOf(interfaces));
    }

    /** Effective host metadata for diagnostics, with no rendering callbacks. */
    public static Map<ResourceLocation, EquipmentStructureUiDefinition> definitions() {
        return definitions(Map.of());
    }

    public static Map<ResourceLocation, EquipmentStructureUiDefinition> definitions(Map<ResourceLocation, EquipmentHostDefinition> templates) {
        var generation = resources;
        var hosts = new java.util.HashSet<>(DEFINITIONS.keySet());
        hosts.addAll(generation.hosts().keySet());
        hosts.addAll(templates.keySet());
        var result = new java.util.HashMap<ResourceLocation, EquipmentStructureUiDefinition>();
        hosts.forEach(host -> result.put(host, resolve(host,
                templates.containsKey(host) ? templates.get(host).slots() : List.of(), generation)));
        return Map.copyOf(result);
    }

    public static boolean unregister(ResourceLocation hostId) {
        return DEFINITIONS.remove(Objects.requireNonNull(hostId, "hostId")) != null;
    }

    public static void clear() {
        DEFINITIONS.clear();
        INTERFACES.clear();
        resources = new ResourceDisplays(Map.of(), Map.of());
    }
}
