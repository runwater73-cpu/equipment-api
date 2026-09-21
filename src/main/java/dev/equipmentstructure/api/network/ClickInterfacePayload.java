package dev.equipmentstructure.api.network;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A workspace item action identifies its logical interface in the same request as the click. */
public record ClickInterfacePayload(int containerId, ResourceLocation interfaceId, int button, int action)
        implements CustomPacketPayload {
    public static final Type<ClickInterfacePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "click_interface"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClickInterfacePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ClickInterfacePayload::containerId,
            ResourceLocation.STREAM_CODEC, ClickInterfacePayload::interfaceId,
            ByteBufCodecs.VAR_INT, ClickInterfacePayload::button,
            ByteBufCodecs.VAR_INT, ClickInterfacePayload::action,
            ClickInterfacePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
