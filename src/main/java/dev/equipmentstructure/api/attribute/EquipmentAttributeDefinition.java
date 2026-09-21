package dev.equipmentstructure.api.attribute;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Serializable, fixed attribute provider for one component ID (the resource file ID). */
public record EquipmentAttributeDefinition(List<Modifier> modifiers) {
    public static final int MAX_MODIFIERS = 256;
    public static final Codec<EquipmentAttributeDefinition> CODEC = Modifier.CODEC.listOf()
            .fieldOf("modifiers").codec().xmap(EquipmentAttributeDefinition::new,
                    EquipmentAttributeDefinition::modifiers).validate(EquipmentAttributeDefinition::validate);

    public EquipmentAttributeDefinition { modifiers = List.copyOf(modifiers); }

    private static DataResult<EquipmentAttributeDefinition> validate(EquipmentAttributeDefinition value) {
        if (value.modifiers.size() > MAX_MODIFIERS) return DataResult.error(() -> "Too many attribute modifiers (max 256)");
        Set<String> seen = new HashSet<>();
        for (Modifier modifier : value.modifiers) {
            if (!seen.add(modifier.attribute() + "|" + modifier.id())) {
                return DataResult.error(() -> "Duplicate attribute/modifier ID: " + modifier.attribute() + "/" + modifier.id());
            }
        }
        return DataResult.success(value);
    }

    public List<EquipmentAttributeContribution> contributions(RegistryAccess registries) {
        validate(this).getOrThrow();
        return modifiers.stream().map(modifier -> modifier.contribution(registries)).toList();
    }

    public record Modifier(ResourceLocation attribute, ResourceLocation id, double amount,
                           AttributeModifier.Operation operation, EquipmentSlotGroup equipmentSlot,
                           int priority, Optional<ResourceLocation> stackingKey, EquipmentAttributeStacking stacking,
                           Optional<ResourceLocation> host, Optional<ResourceLocation> slot,
                           Optional<ResourceLocation> interfaceType) {
        private static final Codec<Double> FINITE = Codec.DOUBLE.validate(value -> Double.isFinite(value)
                ? DataResult.success(value) : DataResult.error(() -> "Attribute amount must be finite"));
        private static final Codec<EquipmentAttributeStacking> STACKING = Codec.STRING.comapFlatMap(value -> {
            for (var stacking : EquipmentAttributeStacking.values()) {
                if (stacking.name().toLowerCase(Locale.ROOT).equals(value)) return DataResult.success(stacking);
            }
            return DataResult.error(() -> "Unknown attribute stacking: " + value);
        }, value -> value.name().toLowerCase(Locale.ROOT));

        public static final Codec<Modifier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("attribute").forGetter(Modifier::attribute),
                ResourceLocation.CODEC.fieldOf("id").forGetter(Modifier::id),
                FINITE.fieldOf("amount").forGetter(Modifier::amount),
                AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(Modifier::operation),
                EquipmentSlotGroup.CODEC.fieldOf("equipment_slot").forGetter(Modifier::equipmentSlot),
                Codec.INT.optionalFieldOf("priority", 0).forGetter(Modifier::priority),
                ResourceLocation.CODEC.optionalFieldOf("stacking_key").forGetter(Modifier::stackingKey),
                STACKING.optionalFieldOf("stacking", EquipmentAttributeStacking.STACK).forGetter(Modifier::stacking),
                ResourceLocation.CODEC.optionalFieldOf("host_id").forGetter(Modifier::host),
                ResourceLocation.CODEC.optionalFieldOf("slot_id").forGetter(Modifier::slot),
                ResourceLocation.CODEC.optionalFieldOf("interface_type").forGetter(Modifier::interfaceType)
        ).apply(instance, Modifier::new));

        private EquipmentAttributeContribution contribution(RegistryAccess registries) {
            var holder = registries.registryOrThrow(Registries.ATTRIBUTE)
                    .getHolderOrThrow(ResourceKey.create(Registries.ATTRIBUTE, attribute));
            return new EquipmentAttributeContribution(holder, id, amount, operation, equipmentSlot,
                    priority, stackingKey.orElse(id), stacking, context ->
                    (host.isEmpty() || context.hostId().equals(host))
                            && (slot.isEmpty() || slot.get().equals(context.slot().id()))
                            && (interfaceType.isEmpty() || interfaceType.get().equals(context.slot().interfaceType())));
        }
    }
}
