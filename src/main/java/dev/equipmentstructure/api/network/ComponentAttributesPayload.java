package dev.equipmentstructure.api.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.attribute.EquipmentAttributeDefinition;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Complete server-authoritative snapshot, including empty maps and explicitly empty providers. */
public record ComponentAttributesPayload(Map<ResourceLocation, EquipmentAttributeDefinition> definitions)
        implements CustomPacketPayload {
    public static final Type<ComponentAttributesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "component_attributes"));
    public static final Codec<Map<ResourceLocation, EquipmentAttributeDefinition>> MAP_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, EquipmentAttributeDefinition.CODEC).validate(value ->
                    value.size() <= 4096 ? DataResult.success(value)
                            : DataResult.error(() -> "Too many component attribute definitions (max 4096)"));
    public static final StreamCodec<ByteBuf, ComponentAttributesPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodec(MAP_CODEC).map(ComponentAttributesPayload::new, ComponentAttributesPayload::definitions);

    public ComponentAttributesPayload { definitions = Map.copyOf(definitions); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
