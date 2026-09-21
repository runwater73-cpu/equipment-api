package dev.equipmentstructure.api.client.appearance;

import dev.equipmentstructure.api.appearance.AppearanceSupport;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.entity.EquipmentSlot;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Humanoid armor frames, independent of item-display frames. Explicit calibration wins. */
public final class AppearanceArmorBindings {
    private static final Map<Key, Binding> BINDINGS = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> AUTOMATIC_DISABLED = ConcurrentHashMap.newKeySet();
    private static final Binding DEFAULT = new Binding(16, Map.of(),
            () -> AppearanceModelAssetRegistry.support(Set.of(), false));
    private AppearanceArmorBindings() {}
    public static void register(ResourceLocation hostId, Supplier<AppearanceSupport> support) {
        register(hostId, null, support);
    }

    public static void register(ResourceLocation hostId, EquipmentSlot slot,
                                Supplier<AppearanceSupport> support) {
        register(hostId, slot, 16, Map.of(), support);
    }

    public static void register(ResourceLocation hostId, EquipmentSlot slot, float modelUnitsPerBlock,
                                Map<ResourceLocation, Set<ArmorAttachment>> attachments,
                                Supplier<AppearanceSupport> support) {
        Objects.requireNonNull(hostId, "hostId");
        if (slot != null && ArmorAttachment.defaults(slot).isEmpty()) {
            throw new IllegalArgumentException("Armor appearance bindings require an armor slot");
        }
        var binding = new Binding(modelUnitsPerBlock, attachments, support);
        if (slot != null && binding.attachments().values().stream()
                .flatMap(Set::stream).anyMatch(anchor -> !anchor.supports(slot))) {
            throw new IllegalArgumentException("Attachment is outside the armor slot: " + slot);
        }
        if (BINDINGS.putIfAbsent(new Key(hostId, slot), binding) != null) {
            throw new IllegalStateException("Armor appearance binding already registered: " + hostId);
        }
    }
    static Binding get(ResourceLocation hostId, EquipmentSlot slot) {
        Binding exact = BINDINGS.get(new Key(hostId, slot));
        return exact != null ? exact : BINDINGS.get(new Key(hostId, null));
    }
    static Binding resolve(ResourceLocation hostId, EquipmentSlot slot) {
        if (slot == null || ArmorAttachment.defaults(slot).isEmpty()) return null;
        Binding registered = get(hostId, slot);
        if (registered != null) return registered;
        return AUTOMATIC_DISABLED.contains(hostId) ? null : DEFAULT;
    }
    /** Disable default bone mapping when a host owns its attachment rendering. */
    public static void setAutomaticEnabled(ResourceLocation hostId, boolean enabled) {
        Objects.requireNonNull(hostId, "hostId");
        if (enabled) AUTOMATIC_DISABLED.remove(hostId);
        else AUTOMATIC_DISABLED.add(hostId);
    }
    public static boolean unregister(ResourceLocation hostId, EquipmentSlot slot) {
        return BINDINGS.remove(new Key(Objects.requireNonNull(hostId), slot)) != null;
    }
    public static void clear() { BINDINGS.clear(); AUTOMATIC_DISABLED.clear(); }
    record Binding(float modelUnitsPerBlock, Map<ResourceLocation, Set<ArmorAttachment>> attachments,
                   Supplier<AppearanceSupport> support) {
        Binding {
            if (!Float.isFinite(modelUnitsPerBlock) || modelUnitsPerBlock <= 0
                    || !Float.isFinite(1 / modelUnitsPerBlock)) {
                throw new IllegalArgumentException("Model units must have a finite positive reciprocal");
            }
            var copy = new java.util.HashMap<ResourceLocation, Set<ArmorAttachment>>();
            attachments.forEach((id, anchors) -> {
                if (anchors.isEmpty()) throw new IllegalArgumentException("Empty armor attachment set");
                copy.put(Objects.requireNonNull(id), Set.copyOf(anchors));
            });
            attachments = Map.copyOf(copy);
            Objects.requireNonNull(support, "support");
        }
        Set<ArmorAttachment> anchors(ResourceLocation componentSlot, EquipmentSlot equipmentSlot) {
            return attachments.getOrDefault(componentSlot, ArmorAttachment.defaults(equipmentSlot));
        }
        boolean canHideOriginal() { return this != DEFAULT; }
    }
    private record Key(ResourceLocation hostId, EquipmentSlot slot) {}
}
