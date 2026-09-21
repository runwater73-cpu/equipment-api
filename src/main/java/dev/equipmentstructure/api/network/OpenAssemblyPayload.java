package dev.equipmentstructure.api.network;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client request to open the generic equipment assembly screen. */
public record OpenAssemblyPayload() implements CustomPacketPayload {

    public static final Type<OpenAssemblyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "open_assembly")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenAssemblyPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenAssemblyPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
