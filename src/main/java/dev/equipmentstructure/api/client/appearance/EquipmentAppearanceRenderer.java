package dev.equipmentstructure.api.client.appearance;

/**
 * Host-owned renderer for a composed equipment appearance.
 * Return {@code true} only when the adapter rendered the complete supported
 * scene; returning {@code false} lets the caller keep the original model.
 */
@FunctionalInterface
public interface EquipmentAppearanceRenderer {
    /** Override to opt out of scenes that use a different coordinate/model space. */
    default boolean supports(EquipmentAppearanceScene scene) {
        return true;
    }

    boolean render(EquipmentAppearanceRenderContext context);
}
