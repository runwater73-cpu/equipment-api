package dev.equipmentstructure.api.compat.curios;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public record CuriosBindingActionPayload(int containerId, int stateId, CuriosSlotKey key,
        boolean quick, ItemStack expectedCarried) implements CustomPacketPayload {
    public static final Type<CuriosBindingActionPayload> TYPE = new Type<>(CuriosSlotKey.id("binding_action"));
    public CuriosBindingActionPayload { expectedCarried = expectedCarried.copy(); }
    @Override public ItemStack expectedCarried() { return expectedCarried.copy(); }
    public static final StreamCodec<RegistryFriendlyByteBuf, CuriosBindingActionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public CuriosBindingActionPayload decode(RegistryFriendlyByteBuf b) {
            return new CuriosBindingActionPayload(b.readVarInt(), b.readVarInt(), new CuriosSlotKey(b.readUtf(128), b.readVarInt(), false), b.readBoolean(), ItemStack.OPTIONAL_STREAM_CODEC.decode(b));
        }
        @Override public void encode(RegistryFriendlyByteBuf b, CuriosBindingActionPayload value) {
            b.writeVarInt(value.containerId); b.writeVarInt(value.stateId); b.writeUtf(value.key.type(), 128);
            b.writeVarInt(value.key.index()); b.writeBoolean(value.quick); ItemStack.OPTIONAL_STREAM_CODEC.encode(b, value.expectedCarried);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
