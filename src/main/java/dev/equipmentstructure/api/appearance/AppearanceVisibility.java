package dev.equipmentstructure.api.appearance;

/** Visual choices for the host item and the currently selected installed component. */
public record AppearanceVisibility(boolean originalVisible, boolean componentVisible) {
    public static final AppearanceVisibility VISIBLE = new AppearanceVisibility(true, true);
}
