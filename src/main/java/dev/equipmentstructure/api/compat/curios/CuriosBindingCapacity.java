package dev.equipmentstructure.api.compat.curios;

/** Read-only binding eligibility; it neither changes native capacity nor grants modifiers. */
public interface CuriosBindingCapacity {
    int equipment$stableCapacity();
}
