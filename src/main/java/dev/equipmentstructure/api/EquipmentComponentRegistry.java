package dev.equipmentstructure.api;

import net.minecraft.resources.ResourceLocation;
import dev.equipmentstructure.api.internal.ExtensionGuard;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.item.ItemStack;

/** Runtime registry for concrete component definitions. */
public final class EquipmentComponentRegistry {
    private static final Map<ResourceLocation, EquipmentComponentDefinition> DEFINITIONS =
            new ConcurrentHashMap<>();
    private static final ExtensionGuard<ResourceLocation> ITEM_FACTORIES = new ExtensionGuard<>("Component item factory");
    private static final ExtensionGuard<ResourceLocation> ITEM_RESTORES = new ExtensionGuard<>("Component item restoration");

    private EquipmentComponentRegistry() {}

    public static void register(EquipmentComponentDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        EquipmentComponentDefinition previous = DEFINITIONS.putIfAbsent(definition.id(), definition);
        if (previous != null && !previous.equals(definition)) {
            throw new IllegalStateException("Equipment component already registered: " + definition.id());
        }
        if (previous == null) dev.equipmentstructure.api.grid.GridDefinitions.invalidateRegistered();
    }

    public static Optional<EquipmentComponentDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(DEFINITIONS.get(Objects.requireNonNull(id, "id")));
    }

    public static boolean isRegistered(ResourceLocation id) {
        return DEFINITIONS.containsKey(id);
    }

    /** Read-only metadata snapshot; does not invoke item factories or readers. */
    public static Map<ResourceLocation, EquipmentComponentDefinition> definitions() { return Map.copyOf(DEFINITIONS); }

    public static boolean unregister(ResourceLocation id) {
        ITEM_FACTORIES.forget(id);
        ITEM_RESTORES.forget(id);
        boolean changed = DEFINITIONS.remove(Objects.requireNonNull(id, "id")) != null;
        if (changed) dev.equipmentstructure.api.grid.GridDefinitions.invalidateRegistered();
        return changed;
    }

    /**
     * Shared read path for menus, shift-click and tooltips. Explicit external bindings have priority;
     * otherwise the item's native reader is used. Both require a registered, exact identity and
     * a factory that preserves the entire input item. All callbacks receive a count-one copy.
     */
    public static Optional<EquipmentComponentInstance> fromItemStack(ItemStack stack) {
        return EquipmentComponentInspection.inspect(stack).component();
    }

    /** Converts a registered component instance back into its owning item, if provided. */
    public static Optional<ItemStack> createItemStack(EquipmentComponentInstance instance) {
        Objects.requireNonNull(instance, "instance");
        return ITEM_FACTORIES.call(instance.id(), () ->
                get(instance.id()).flatMap(definition -> definition.createItemStack(instance))
                        .filter(stack -> !stack.isEmpty()).map(stack -> stack.copyWithCount(1)), Optional.empty());
    }

    /**
     * Restores an item for a physical transfer. The current instance data must survive being
     * read back; a lossy or missing reader/factory leaves the installed component untouched.
     * Low-level createItemStack remains available for diagnostics and programmatic projections.
     */
    public static Optional<ItemStack> createValidatedItemStack(EquipmentComponentInstance instance) {
        Objects.requireNonNull(instance, "instance");
        return ITEM_RESTORES.call(instance.id(), () -> {
            var definition = get(instance.id());
            if (definition.isEmpty()) return Optional.empty();
            return createItemStack(instance).filter(stack ->
                    EquipmentComponentInspection.readRestored(stack).component()
                            .filter(restored -> EquipmentComponentInspection.sameInstance(instance, restored)).isPresent()
                            && get(instance.id()).equals(definition));
        }, Optional.empty());
    }

    public static void clear() {
        DEFINITIONS.clear();
        dev.equipmentstructure.api.grid.GridDefinitions.invalidateRegistered();
        EquipmentComponentItemAdapters.clear();
        ITEM_FACTORIES.clear();
        ITEM_RESTORES.clear();
        EquipmentComponentInspection.clear();
    }
}
