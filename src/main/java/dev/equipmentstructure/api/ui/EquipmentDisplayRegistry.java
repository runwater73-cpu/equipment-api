package dev.equipmentstructure.api.ui;

import dev.equipmentstructure.api.internal.ExtensionGuard;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Client presentation extensions keyed by host ID, separate from component information. */
public final class EquipmentDisplayRegistry {
    private static final Map<ResourceLocation, EquipmentDisplayProvider> PROVIDERS = new ConcurrentHashMap<>();
    private static final ExtensionGuard<ResourceLocation> GUARD = new ExtensionGuard<>("Equipment display provider");
    private EquipmentDisplayRegistry() {}

    public static void register(ResourceLocation hostId, EquipmentDisplayProvider provider) {
        Objects.requireNonNull(hostId, "hostId");
        Objects.requireNonNull(provider, "provider");
        var previous = PROVIDERS.putIfAbsent(hostId, provider);
        if (previous != null && previous != provider) throw new IllegalStateException("Equipment display already registered: " + hostId);
    }

    public static Optional<EquipmentDisplay> resolve(EquipmentItemView item) {
        return item.hostId().flatMap(id -> {
            var provider = PROVIDERS.get(id);
            return provider == null ? Optional.empty()
                    : GUARD.call(id, () -> Optional.ofNullable(provider.describe(item)), Optional.empty());
        });
    }

    public static boolean unregister(ResourceLocation hostId) {
        GUARD.forget(hostId);
        return PROVIDERS.remove(Objects.requireNonNull(hostId, "hostId")) != null;
    }
    public static void clear() { PROVIDERS.clear(); GUARD.clear(); }
}
