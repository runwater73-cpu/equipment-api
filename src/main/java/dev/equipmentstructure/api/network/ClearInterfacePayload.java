package dev.equipmentstructure.api.network;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client request to clear the currently selected logical interface. */
public record ClearInterfacePayload(int containerId) implements CustomPacketPayload {
    public static final Type<ClearInterfacePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID,
                    "clear_interface")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ClearInterfacePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    ClearInterfacePayload::containerId,
                    ClearInterfacePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
