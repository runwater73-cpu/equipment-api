package dev.equipmentstructure.api.network;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client request to select one stable interface instance in the open menu. */
public record SelectInterfacePayload(int containerId, ResourceLocation interfaceId)
        implements CustomPacketPayload {
    public static final Type<SelectInterfacePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "select_interface")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectInterfacePayload> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
                    SelectInterfacePayload::containerId,
                    ResourceLocation.STREAM_CODEC,
                    SelectInterfacePayload::interfaceId,
                    SelectInterfacePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
