package dev.equipmentstructure.api.attribute;

import dev.equipmentstructure.api.internal.ExtensionGuard;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Runtime registry for component-driven attribute providers. */
public final class EquipmentAttributeRegistry {
    private static final ExtensionGuard<ResourceLocation> GUARD = new ExtensionGuard<>("Attribute provider");
    private static final ConcurrentMap<ResourceLocation, EquipmentAttributeProvider> PROVIDERS =
            new ConcurrentHashMap<>();

    private EquipmentAttributeRegistry() {
    }

    /** Registers a provider once; conflicting registrations fail loudly. */
    public static void register(ResourceLocation componentId, EquipmentAttributeProvider provider) {
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(provider, "provider");
        EquipmentAttributeProvider previous = PROVIDERS.putIfAbsent(componentId, provider);
        if (previous != null && previous != provider) {
            throw new IllegalStateException("Equipment attribute provider already registered: " + componentId);
        }
    }

    /** Registers a fixed contribution list for components whose values are constant. */
    public static void register(ResourceLocation componentId,
                                EquipmentAttributeContribution... contributions) {
        Objects.requireNonNull(contributions, "contributions");
        List<EquipmentAttributeContribution> fixed = List.of(contributions.clone());
        register(componentId, context -> fixed);
    }

    public static Optional<EquipmentAttributeProvider> get(ResourceLocation componentId) {
        return Optional.ofNullable(PROVIDERS.get(Objects.requireNonNull(componentId, "componentId")));
    }

    /** Java provider identities only; does not evaluate providers or include JSON overrides. */
    public static java.util.Set<ResourceLocation> registeredIds() { return java.util.Set.copyOf(PROVIDERS.keySet()); }

    public static boolean unregister(ResourceLocation componentId) {
        GUARD.forget(componentId);
        return PROVIDERS.remove(Objects.requireNonNull(componentId, "componentId")) != null;
    }

    /** Useful for dev reloads and isolated tests. */
    public static void clear() {
        PROVIDERS.clear();
        GUARD.clear();
    }

    static List<EquipmentAttributeContribution> resolve(EquipmentAttributeContext context) {
        // Presence, including an explicitly empty list, replaces the Java provider.
        var fixed = EquipmentAttributeData.current().providers().get(context.component().id());
        if (fixed != null) return fixed;
        EquipmentAttributeProvider provider = PROVIDERS.get(context.component().id());
        if (provider == null) return List.of();
        return GUARD.call(context.component().id(), () -> {
            Collection<EquipmentAttributeContribution> values = provider.contributions(context);
            if (values == null) return List.of();
            // Include iteration in the boundary: integrations may return lazy collections.
            List<EquipmentAttributeContribution> copy = new ArrayList<>();
            for (EquipmentAttributeContribution contribution : values) {
                if (contribution != null) copy.add(contribution);
            }
            return List.copyOf(copy);
        }, List.of());
    }
}
