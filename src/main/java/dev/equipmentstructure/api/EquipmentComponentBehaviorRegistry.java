package dev.equipmentstructure.api;

import dev.equipmentstructure.api.internal.ExtensionGuard;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Runtime registry for optional component lifecycle behavior. */
public final class EquipmentComponentBehaviorRegistry {
    private static final ConcurrentMap<ResourceLocation, EquipmentComponentBehavior> BEHAVIORS =
            new ConcurrentHashMap<>();
    private static final ExtensionGuard<CallbackKey> GUARD =
            new ExtensionGuard<>("Component behavior");

    private enum Hook { CAN_INSTALL, CAN_REMOVE, INSTALLED, REMOVED, ACTIVE, ACTIVATED, DEACTIVATED, TICK }
    private record CallbackKey(ResourceLocation componentId, Hook hook) {}

    private EquipmentComponentBehaviorRegistry() {
    }

    /** Registers one behavior for a concrete component ID. */
    public static void register(ResourceLocation componentId, EquipmentComponentBehavior behavior) {
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(behavior, "behavior");
        EquipmentComponentBehavior previous = BEHAVIORS.putIfAbsent(componentId, behavior);
        if (previous != null && previous != behavior) {
            throw new IllegalStateException("Equipment component behavior already registered: " + componentId);
        }
    }

    public static Optional<EquipmentComponentBehavior> get(ResourceLocation componentId) {
        return Optional.ofNullable(BEHAVIORS.get(Objects.requireNonNull(componentId, "componentId")));
    }

    /** Registered identities only; querying diagnostics must not execute gameplay callbacks. */
    public static java.util.Set<ResourceLocation> registeredIds() { return java.util.Set.copyOf(BEHAVIORS.keySet()); }

    public static boolean unregister(ResourceLocation componentId) {
        Objects.requireNonNull(componentId, "componentId");
        for (Hook hook : Hook.values()) GUARD.forget(new CallbackKey(componentId, hook));
        return BEHAVIORS.remove(componentId) != null;
    }

    /** Returns false when the behavior rejects the operation or throws. */
    static boolean canInstall(EquipmentComponentContext context) {
        return call(context, Hook.CAN_INSTALL, behavior -> behavior.canInstall(context), true, false);
    }

    /** Returns false when the behavior rejects the operation or throws. */
    static boolean canRemove(EquipmentComponentContext context) {
        return call(context, Hook.CAN_REMOVE, behavior -> behavior.canRemove(context), true, false);
    }

    static void installed(EquipmentComponentContext context) {
        call(context, Hook.INSTALLED, behavior -> {
            behavior.onInstalled(context);
            return true;
        }, true, false);
    }

    static void removed(EquipmentComponentContext context) {
        call(context, Hook.REMOVED, behavior -> {
            behavior.onRemoved(context);
            return true;
        }, true, false);
    }

    static boolean isEmpty() { return BEHAVIORS.isEmpty(); }

    static boolean active(EquipmentComponentContext context, EquipmentComponentBehavior behavior) {
        return GUARD.call(new CallbackKey(context.component().id(), Hook.ACTIVE), () -> behavior.isActive(context), false);
    }

    static void activated(EquipmentComponentContext context, EquipmentComponentBehavior behavior) {
        notify(context, behavior, Hook.ACTIVATED, value -> value.onActivated(context));
    }

    static void deactivated(EquipmentComponentContext context, EquipmentComponentBehavior behavior) {
        notify(context, behavior, Hook.DEACTIVATED, value -> value.onDeactivated(context));
    }

    static void tick(EquipmentComponentContext context, EquipmentComponentBehavior behavior) {
        notify(context, behavior, Hook.TICK, value -> value.tick(context));
    }

    private static void notify(EquipmentComponentContext context, EquipmentComponentBehavior behavior,
                               Hook hook, java.util.function.Consumer<EquipmentComponentBehavior> action) {
        GUARD.call(new CallbackKey(context.component().id(), hook), () -> {
            action.accept(behavior);
            return true;
        }, false);
    }

    /** Useful for isolated tests and development reloads. */
    public static void clear() {
        BEHAVIORS.clear();
        GUARD.clear();
    }

    private static <T> T call(EquipmentComponentContext context, Hook hook,
                              java.util.function.Function<EquipmentComponentBehavior, T> operation,
                              T absent, T failure) {
        EquipmentComponentBehavior behavior = BEHAVIORS.get(context.component().id());
        if (behavior == null) return absent;
        return GUARD.call(new CallbackKey(context.component().id(), hook), () -> operation.apply(behavior), failure);
    }
}
