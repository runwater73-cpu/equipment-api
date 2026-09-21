package dev.equipmentstructure.api.client.appearance;

/** Controls whether composed parts are layered over, or replace, the host model. */
public enum EquipmentAppearanceRenderMode {
    /** Keep the original/third-party model and draw ready component assets over it. */
    OVERLAY,
    /** Let the host renderer draw the complete composed model. */
    REPLACE
}
