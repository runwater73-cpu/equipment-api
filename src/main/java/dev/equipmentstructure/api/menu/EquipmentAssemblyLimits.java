package dev.equipmentstructure.api.menu;

/** Limits of the bundled menu, independent from persistent equipment data. */
public final class EquipmentAssemblyLimits {
    public static final int MAX_INTERFACES = 256;
    private EquipmentAssemblyLimits() {}
    public static boolean supports(int count) { return count >= 0 && count <= MAX_INTERFACES; }
}
