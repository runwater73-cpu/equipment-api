package dev.equipmentstructure.api.ui;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Host-local presentation of one installation point; never changes compatibility or interaction. */
public record EquipmentSlotDisplay(Optional<String> nameKey, Optional<ResourceLocation> emptyIcon,
                                   List<String> descriptionKeys) {
    private static final Codec<String> KEY = Codec.STRING.validate(value -> value.isBlank()
            ? DataResult.error(() -> "Translation key must not be blank") : DataResult.success(value));
    public static final Codec<EquipmentSlotDisplay> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            KEY.optionalFieldOf("name").forGetter(EquipmentSlotDisplay::nameKey),
            ResourceLocation.CODEC.optionalFieldOf("empty_icon").forGetter(EquipmentSlotDisplay::emptyIcon),
            KEY.listOf().validate(values -> values.size() > 8
                    ? DataResult.error(() -> "At most 8 slot description lines are supported") : DataResult.success(values))
                    .optionalFieldOf("description", List.of()).forGetter(EquipmentSlotDisplay::descriptionKeys)
    ).apply(instance, EquipmentSlotDisplay::new));

    public EquipmentSlotDisplay {
        Objects.requireNonNull(nameKey, "nameKey");
        Objects.requireNonNull(emptyIcon, "emptyIcon");
        descriptionKeys = List.copyOf(descriptionKeys);
        if (nameKey.filter(String::isBlank).isPresent() || descriptionKeys.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Translation key must not be blank");
        }
        if (descriptionKeys.size() > 8) throw new IllegalArgumentException("At most 8 slot description lines are supported");
    }

    public static EquipmentSlotDisplay defaults() {
        return new EquipmentSlotDisplay(Optional.empty(), Optional.empty(), List.of());
    }

    public String nameKey(ResourceLocation slotId) {
        return nameKey.orElse("slot." + slotId.getNamespace() + "." + slotId.getPath().replace('/', '.'));
    }

    public Component name(ResourceLocation slotId) {
        var nativeSlot = dev.equipmentstructure.api.compat.curios.CuriosSlotKey.parse(slotId);
        if (nativeSlot.isPresent() && nameKey.filter(key -> key.startsWith("curios.identifier.")).isPresent()) {
            var key = nativeSlot.get();
            var name = Component.translatableWithFallback(nameKey(slotId), key.type()).append(" #" + (key.index() + 1));
            return key.cosmetic() ? name.append(Component.translatable("gui.equipment_structure_api.curios.cosmetic")) : name;
        }
        return Component.translatableWithFallback(nameKey(slotId), slotId.toString());
    }

    public List<Component> tooltip(ResourceLocation slotId) {
        var lines = new ArrayList<Component>();
        lines.add(name(slotId));
        descriptionKeys.forEach(key -> lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY)));
        return List.copyOf(lines);
    }
}
