package dev.equipmentstructure.api.network;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.grid.GridDefinitions;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GridDefinitionsPayload(GridDefinitions definitions) implements CustomPacketPayload {
    public static final Type<GridDefinitionsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "grid_definitions"));
    public static final StreamCodec<ByteBuf, GridDefinitionsPayload> STREAM_CODEC = ByteBufCodecs.fromCodec(GridDefinitions.CODEC)
            .map(GridDefinitionsPayload::new, GridDefinitionsPayload::definitions);
    public GridDefinitionsPayload { java.util.Objects.requireNonNull(definitions); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
