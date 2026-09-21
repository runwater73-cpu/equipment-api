package dev.equipmentstructure.api.network;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Installation notification and authoritative response to a placement confirmation. */
public record AppearancePlacementStatusPayload(int containerId, ResourceLocation slotId, int status)
        implements CustomPacketPayload {
    public static final int OPEN = 0, SAVED = 1, REJECTED = 2;
    public static final Type<AppearancePlacementStatusPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "appearance_placement_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AppearancePlacementStatusPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, AppearancePlacementStatusPayload::containerId,
                    ResourceLocation.STREAM_CODEC, AppearancePlacementStatusPayload::slotId,
                    ByteBufCodecs.VAR_INT, AppearancePlacementStatusPayload::status,
                    AppearancePlacementStatusPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
