package dev.equipmentstructure.api.network;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Acknowledgement for a particular request. Item state still arrives through vanilla menu sync. */
public record GridActionResultPayload(int containerId, int requestId, boolean applied) implements CustomPacketPayload {
    public static final Type<GridActionResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "grid_action_result"));
    public static final StreamCodec<FriendlyByteBuf, GridActionResultPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public GridActionResultPayload decode(FriendlyByteBuf b) {
            return new GridActionResultPayload(b.readVarInt(), b.readVarInt(), b.readBoolean());
        }
        @Override public void encode(FriendlyByteBuf b, GridActionResultPayload p) {
            b.writeVarInt(p.containerId()); b.writeVarInt(p.requestId()); b.writeBoolean(p.applied());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
