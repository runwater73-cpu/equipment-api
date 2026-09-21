package dev.equipmentstructure.api.ui;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentStructureApiMod;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Independent namespaces for host tooltips and loose-component tooltips. */
public final class EquipmentTooltipRegistry {
    private record Key(boolean host, ResourceLocation id) {}
    private static final Map<Key, EquipmentTooltipProvider> PROVIDERS = new ConcurrentHashMap<>();
    private static final Set<Key> FAILED = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Boolean> RESOLVING = ThreadLocal.withInitial(() -> false);

    private EquipmentTooltipRegistry() {}

    public static void registerHost(ResourceLocation id, EquipmentTooltipProvider provider) { register(new Key(true, id), provider); }
    public static void registerComponent(ResourceLocation id, EquipmentTooltipProvider provider) { register(new Key(false, id), provider); }
    public static boolean unregisterHost(ResourceLocation id) { return unregister(new Key(true, id)); }
    public static boolean unregisterComponent(ResourceLocation id) { return unregister(new Key(false, id)); }

    private static void register(Key key, EquipmentTooltipProvider provider) {
        Objects.requireNonNull(key.id(), "id");
        Objects.requireNonNull(provider, "provider");
        var previous = PROVIDERS.putIfAbsent(key, provider);
        if (previous != null && previous != provider) throw new IllegalStateException("Tooltip provider already registered: " + key);
    }

    private static boolean unregister(Key key) {
        Objects.requireNonNull(key.id(), "id");
        FAILED.remove(key);
        return PROVIDERS.remove(key) != null;
    }

    public static Optional<EquipmentTooltip> resolve(EquipmentTooltipContext context) {
        Objects.requireNonNull(context, "context");
        Optional<Key> key = context.item().hostId().map(id -> new Key(true, id))
                .or(() -> context.looseComponent().map(EquipmentComponentInstance::id).map(id -> new Key(false, id)));
        if (key.isEmpty() || RESOLVING.get()) return Optional.empty();
        var provider = PROVIDERS.get(key.get());
        if (provider == null) return Optional.empty();
        RESOLVING.set(true);
        try {
            var result = Optional.ofNullable(provider.describe(context));
            FAILED.remove(key.get());
            return result;
        } catch (RuntimeException exception) {
            if (FAILED.add(key.get())) EquipmentStructureApiMod.LOGGER.warn("Equipment tooltip provider failed for {}", key.get(), exception);
            return Optional.empty();
        } finally {
            RESOLVING.remove();
        }
    }

    public static void clear() { PROVIDERS.clear(); FAILED.clear(); }
}
