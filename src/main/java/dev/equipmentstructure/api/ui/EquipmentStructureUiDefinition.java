package dev.equipmentstructure.api.ui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashMap;
import net.minecraft.resources.ResourceLocation;
import dev.equipmentstructure.api.EquipmentSlotDefinition;

/** Client presentation metadata for one equipment host. */
public record EquipmentStructureUiDefinition(
        int accentColor,
        Texts texts,
        Map<ResourceLocation, EquipmentSlotDisplay> slots
) {
    public static Builder builder() {
        return new Builder();
    }

    /** Host labels and installation-point icons for the grid sidebar. */
    public static final class Builder {
        private int accentColor = 0xFFB8B8B8;
        private Texts texts = Texts.defaults();
        private final Map<ResourceLocation, EquipmentSlotDisplay> slots = new LinkedHashMap<>();

        public Builder accentColor(int color) {
            this.accentColor = color;
            return this;
        }


        /** Replaces the labels shown by the API screen with the author's keys. */
        public Builder texts(Texts texts) {
            this.texts = Objects.requireNonNull(texts, "texts");
            return this;
        }

        /** Describes this host's concrete slot without changing its location or accepted types. */
        public Builder slot(ResourceLocation slotId, EquipmentSlotDisplay display) {
            slots.put(Objects.requireNonNull(slotId, "slotId"), Objects.requireNonNull(display, "display"));
            return this;
        }

        public EquipmentStructureUiDefinition build() {
            return new EquipmentStructureUiDefinition(accentColor, texts, slots);
        }
    }

    public EquipmentStructureUiDefinition {
        Objects.requireNonNull(texts, "texts");
        slots = Map.copyOf(slots);
        if ((accentColor & 0xFF000000) == 0) {
            accentColor |= 0xFF000000;
        }
    }

    public static EquipmentStructureUiDefinition defaults() {
        return new EquipmentStructureUiDefinition(0xFFB8B8B8, Texts.defaults(), Map.of());
    }

    public EquipmentSlotDisplay slot(ResourceLocation slotId) {
        return slots.getOrDefault(Objects.requireNonNull(slotId, "slotId"), EquipmentSlotDisplay.defaults());
    }

    /** Translation keys for both side panels; values are fully author-defined. */
    public record Texts(String preview, String stats, String online, String interfaces,
                        String stable, String interfaceDetail, String emptyInterface,
                        String installedComponent, String unknownComponent, String removable,
                        String locked, String componentPreview, String attackDamage,
                        String attackSpeed, String componentType, String componentInterface,
                        String componentAttributes) {
        public static Texts defaults() {
            String p = "gui.equipment_structure_api.assembly.";
            return new Texts(p + "preview", p + "stats", p + "online", p + "interfaces",
                    p + "stable", p + "interface_detail", p + "empty_interface",
                    p + "installed_component", p + "unknown_component", p + "removable", p + "locked",
                    p + "component_preview",
                    p + "attack_damage", p + "attack_speed", p + "component_type",
                    p + "component_interface", p + "component_attributes");
        }
    }

    /** Returns non-fatal authoring issues without affecting rendering. */
    public List<String> validateForHost(List<EquipmentSlotDefinition> slots) {
        Objects.requireNonNull(slots, "slots");
        java.util.Set<ResourceLocation> known = slots.stream()
                .map(EquipmentSlotDefinition::id).collect(java.util.stream.Collectors.toSet());
        List<String> issues = new java.util.ArrayList<>();
        this.slots.keySet().stream().filter(id -> !known.contains(id)).sorted()
                .forEach(id -> issues.add("Display is not used by host interface: " + id));
        return List.copyOf(issues);
    }
}
