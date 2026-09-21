package dev.equipmentstructure.api.attribute;

/**
 * Determines how contributions sharing an attribute are combined.
 *
 * <p>{@link #STACK} is the default and keeps every contribution.  A
 * {@link #REPLACE} rule keeps the highest-priority contribution for the same
 * stacking key.  {@link #EXCLUSIVE} keeps only the highest-priority active
 * contribution for the attribute, which is useful for mutually exclusive
 * modes supplied by different components.</p>
 */
public enum EquipmentAttributeStacking {
    STACK,
    REPLACE,
    EXCLUSIVE
}
