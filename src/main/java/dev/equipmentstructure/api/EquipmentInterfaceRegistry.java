package dev.equipmentstructure.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime registry for interface types exposed by content mods. */
public final class EquipmentInterfaceRegistry {
    private static final Map<ResourceLocation, EquipmentInterfaceDefinition> DEFINITIONS =
            new ConcurrentHashMap<>();

    private EquipmentInterfaceRegistry() {}

    public static void register(EquipmentInterfaceDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        EquipmentInterfaceDefinition previous = DEFINITIONS.putIfAbsent(definition.id(), definition);
        if (previous != null && !previous.equals(definition)) {
            throw new IllegalStateException("Equipment interface already registered: " + definition.id());
        }
    }

    /** Registers an interface type and the component category it accepts. */
    public static void register(ResourceLocation id, ResourceLocation defaultComponentType) {
        register(new EquipmentInterfaceDefinition(id, defaultComponentType));
    }

    public static Optional<EquipmentInterfaceDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(DEFINITIONS.get(Objects.requireNonNull(id, "id")));
    }

    public static boolean isRegistered(ResourceLocation id) {
        return DEFINITIONS.containsKey(id);
    }

    public static boolean unregister(ResourceLocation id) {
        return DEFINITIONS.remove(Objects.requireNonNull(id, "id")) != null;
    }

    public static void clear() { DEFINITIONS.clear(); }
}
