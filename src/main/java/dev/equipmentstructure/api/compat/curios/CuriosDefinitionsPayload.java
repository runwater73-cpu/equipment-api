package dev.equipmentstructure.api.compat.curios;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sent before grid definitions so destination readers and reverse item factories are ready together. */
record CuriosDefinitionsPayload(CuriosDefinitions definitions,
        java.util.Map<String, net.minecraft.resources.ResourceLocation> slots) implements CustomPacketPayload {
    static final Type<CuriosDefinitionsPayload> TYPE = new Type<>(CuriosSlotKey.id("curios_definitions"));
    static final StreamCodec<RegistryFriendlyByteBuf, CuriosDefinitionsPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                    CuriosDefinitions.CODEC.fieldOf("definitions").forGetter(CuriosDefinitionsPayload::definitions),
                    com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,
                            net.minecraft.resources.ResourceLocation.CODEC).fieldOf("slots").forGetter(CuriosDefinitionsPayload::slots)
            ).apply(i, CuriosDefinitionsPayload::new)));
    CuriosDefinitionsPayload {
        slots = java.util.Map.copyOf(slots);
        if (slots.size() > 256) throw new IllegalArgumentException("Too many Curios types");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
