package dev.equipmentstructure.api;

/**
 * Optional gameplay behavior for one registered component.
 *
 * <p>Identity, attributes, presentation and behavior are separate extension
 * points. This keeps a data-only component usable without implementing a
 * capability-style object, while still covering the lifecycle hooks commonly
 * exposed by Curios.</p>
 */
public interface EquipmentComponentBehavior {

    /**
     * Pure, deterministic check, also queried by client previews. May run many
     * times; do not mutate the world, registrations or captured live stacks.
     * False, an exception or recursive invocation rejects installation.
     */
    default boolean canInstall(EquipmentComponentContext context) {
        return true;
    }

    /** Pure removal check, with the same rules as {@link #canInstall}. */
    default boolean canRemove(EquipmentComponentContext context) {
        return true;
    }

    /**
     * Once per successful API installation, after the changed event. The
     * context contains the committed snapshot, even if an event listener has
     * since changed the host. This is assembly, not equipping the host on an entity.
     * Exceptions are logged and do not undo the completed transfer.
     */
    default void onInstalled(EquipmentComponentContext context) {
    }

    /**
     * Once per successful API removal, after the changed event. The context
     * contains the previous structure and removed part. Same notification
     * and exception rules as {@link #onInstalled}.
     */
    default void onRemoved(EquipmentComponentContext context) {
    }

    /**
     * Pure server-side activation predicate, evaluated while the host occupies
     * an entity equipment slot. False or an exception disables this behavior's
     * activation and tick callbacks, not installation, attributes or rendering.
     */
    default boolean isActive(EquipmentComponentContext context) {
        return true;
    }

    /**
     * The equipped host's component became active for this wearer/location.
     * This differs from onInstalled: a saved component can activate after login.
     * Data-only changes do not reactivate it. The wearer is present.
     */
    default void onActivated(EquipmentComponentContext context) {
    }

    /**
     * A previously active component stopped, including unequip, replacement,
     * inactive predicate, death or entity departure. Uses its last observed
     * snapshot and the original behavior even if that behavior was unregistered.
     * The current entity slot may now contain another item; never assume it is
     * the old host. Only undo effects owned by this behavior.
     */
    default void onDeactivated(EquipmentComponentContext context) {
    }

    /**
     * Automatically dispatched on the server for active components in vanilla
     * entity equipment slots, at most once per wearer/location/game tick.
     * The manual entry {@link EquipmentStructureApi#tickComponents}
     * shares that dispatch and cannot double-tick it. Attributes belong in
     * EquipmentAttributeRegistry, not here.
     */
    default void tick(EquipmentComponentContext context) {
    }
}
