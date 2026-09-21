package dev.equipmentstructure.api.ui;

import dev.equipmentstructure.api.internal.ExtensionGuard;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Runtime registry for client-side component information providers. */
public final class EquipmentComponentDisplayRegistry {
    private static final ExtensionGuard<ResourceLocation> GUARD = new ExtensionGuard<>("Component display provider");
    private static final ConcurrentMap<ResourceLocation, EquipmentComponentDisplayProvider> PROVIDERS =
            new ConcurrentHashMap<>();

    private EquipmentComponentDisplayRegistry() {
    }

    /** Registers one provider for a concrete component ID. */
    public static void register(ResourceLocation componentId,
                                EquipmentComponentDisplayProvider provider) {
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(provider, "provider");
        EquipmentComponentDisplayProvider previous = PROVIDERS.putIfAbsent(componentId, provider);
        if (previous != null && previous != provider) {
            throw new IllegalStateException(
                    "Equipment component display provider already registered: " + componentId);
        }
    }

    public static Optional<EquipmentComponentDisplayProvider> get(ResourceLocation componentId) {
        return Optional.ofNullable(PROVIDERS.get(Objects.requireNonNull(componentId, "componentId")));
    }

    public static boolean unregister(ResourceLocation componentId) {
        GUARD.forget(componentId);
        return PROVIDERS.remove(Objects.requireNonNull(componentId, "componentId")) != null;
    }

    /** Resolves the provider for a read-only display snapshot. */
    public static Optional<EquipmentComponentDisplay> resolve(EquipmentComponentDisplayContext context) {
        EquipmentComponentDisplayProvider provider = PROVIDERS.get(context.component().id());
        if (provider == null) return Optional.empty();
        return GUARD.call(context.component().id(), () -> Optional.ofNullable(provider.describe(context)), Optional.empty());
    }

    /** Useful for client reloads and isolated tests. */
    public static void clear() {
        PROVIDERS.clear();
        GUARD.clear();
    }
}
