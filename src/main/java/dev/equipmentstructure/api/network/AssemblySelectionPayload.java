package dev.equipmentstructure.api.network;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Reveals the server-selected quick-install destination after vanilla item synchronization. */
public record AssemblySelectionPayload(int containerId, ResourceLocation slotId) implements CustomPacketPayload {
    public static final Type<AssemblySelectionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "assembly_selection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssemblySelectionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AssemblySelectionPayload::containerId,
            ResourceLocation.STREAM_CODEC, AssemblySelectionPayload::slotId, AssemblySelectionPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
