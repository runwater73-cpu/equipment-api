package dev.equipmentstructure.api.client.appearance;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.EquipmentStructureApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

/**
 * Per-draw source, never stored in an appearance plan or cache. Equipment is copied;
 * wearer is a borrowed, read-only reference valid for this render call only.
 */
public final class AppearanceRenderSubject {
    private final ItemStack equipment;
    private final Optional<EquipmentStructure> structure;
    private final ItemDisplayContext itemContext;
    private final EquipmentSlot equipmentSlot;
    private final ArmorAttachment anchor;
    private final LivingEntity wearer;
    private final float partialTick;
    private final boolean editorPreview;

    private AppearanceRenderSubject(ItemStack equipment, ItemDisplayContext itemContext,
                                    EquipmentSlot equipmentSlot, ArmorAttachment anchor,
                                    LivingEntity wearer, float partialTick, boolean editorPreview) {
        this.equipment = equipment == null ? null : equipment.copy();
        this.structure = this.equipment == null
                ? Optional.empty()
                : EquipmentStructureApi.structure(this.equipment);
        this.itemContext = itemContext;
        this.equipmentSlot = equipmentSlot;
        this.anchor = anchor;
        this.wearer = wearer;
        if (!Float.isFinite(partialTick)) throw new IllegalArgumentException("Non-finite partial tick");
        this.partialTick = partialTick;
        this.editorPreview = editorPreview;
    }

    /** Empty subject for context-free renderer queries; does not initialize Minecraft item registries. */
    public static AppearanceRenderSubject empty() {
        return new AppearanceRenderSubject(null, null, null, null, null, 0, false);
    }

    public static AppearanceRenderSubject equipment(ItemStack stack) {
        return new AppearanceRenderSubject(Objects.requireNonNull(stack, "stack"), null, null, null, null, 0, false);
    }

    public static AppearanceRenderSubject item(ItemStack stack, ItemDisplayContext context) {
        return item(stack, context, 0);
    }

    /** Item render subject with the frame's fractional game-time tick. */
    public static AppearanceRenderSubject item(ItemStack stack, ItemDisplayContext context, float partialTick) {
        return new AppearanceRenderSubject(Objects.requireNonNull(stack, "stack"), Objects.requireNonNull(context),
                null, null, null, partialTick, false);
    }

    public static AppearanceRenderSubject armor(ItemStack stack, EquipmentSlot slot,
                                               ArmorAttachment anchor, LivingEntity wearer, float partialTick) {
        Objects.requireNonNull(slot, "slot");
        if (ArmorAttachment.defaults(slot).isEmpty() || anchor != null && !anchor.supports(slot)) {
            throw new IllegalArgumentException("Invalid armor slot/anchor");
        }
        return new AppearanceRenderSubject(Objects.requireNonNull(stack, "stack"), null, slot, anchor, wearer, partialTick, false);
    }

    /** Preview subject used by the placement editor. Motion is sampled at its phase origin. */
    public static AppearanceRenderSubject editor(ItemStack stack) {
        return new AppearanceRenderSubject(Objects.requireNonNull(stack, "stack"), null, null, null, null, 0, true);
    }

    /** Animal armor has a BODY equipment slot; it does not use humanoid ArmorAttachment anchors. */
    public static AppearanceRenderSubject animalArmor(ItemStack stack, LivingEntity wearer,
                                                      float partialTick, boolean editorPreview) {
        return new AppearanceRenderSubject(Objects.requireNonNull(stack, "stack"), null,
                EquipmentSlot.BODY, null, wearer, partialTick, editorPreview);
    }

    public static AppearanceRenderSubject editorArmor(ItemStack stack, EquipmentSlot slot,
                                                      ArmorAttachment anchor) {
        Objects.requireNonNull(slot, "slot");
        return new AppearanceRenderSubject(Objects.requireNonNull(stack, "stack"), null, slot, anchor, null, 0, true);
    }

    public ItemStack equipment() { return equipment == null ? ItemStack.EMPTY.copy() : equipment.copy(); }

    public Optional<EquipmentStructure> structure() { return structure; }
    public Optional<EquipmentComponentInstance> component(ResourceLocation slot) {
        return structure.flatMap(value -> value.component(slot));
    }
    public Optional<ItemDisplayContext> itemContext() { return Optional.ofNullable(itemContext); }
    public Optional<EquipmentSlot> equipmentSlot() { return Optional.ofNullable(equipmentSlot); }
    public Optional<ArmorAttachment> anchor() { return Optional.ofNullable(anchor); }
    public Optional<LivingEntity> wearer() { return Optional.ofNullable(wearer); }
    public float partialTick() { return partialTick; }
    public boolean editorPreview() { return editorPreview; }
}
