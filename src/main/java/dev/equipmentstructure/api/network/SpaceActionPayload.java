package dev.equipmentstructure.api.network;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.grid.*;
import dev.equipmentstructure.api.grid.space.ComponentSpaceTransactions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Snapshot-qualified container intent. Never trusts client item data as the transaction output. */
public record SpaceActionPayload(int containerId, int stateId, int requestId, ItemStack expectedEquipment,
        ItemStack expectedCarried, String definitions, ResourceLocation source, ResourceLocation space, String token,
        ComponentSpaceTransactions.Action action, int entry, GridPlacement target, boolean single) implements CustomPacketPayload {
    public static final Type<SpaceActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "space_action"));
    public SpaceActionPayload {
        expectedEquipment = expectedEquipment.copy(); expectedCarried = expectedCarried.copy();
        if (!definitions.matches("[0-9a-f]{64}") || token.length() > 36 || entry < -1 || entry >= 256
                || target.x() < 0 || target.y() < 0 || target.x() > 63 || target.y() > 63) throw new IllegalArgumentException("Invalid space request");
    }
    @Override public ItemStack expectedEquipment() { return expectedEquipment.copy(); }
    @Override public ItemStack expectedCarried() { return expectedCarried.copy(); }
    public static final StreamCodec<RegistryFriendlyByteBuf, SpaceActionPayload> STREAM_CODEC = new StreamCodec<>() {
        public SpaceActionPayload decode(RegistryFriendlyByteBuf b) {
            return new SpaceActionPayload(b.readVarInt(), b.readVarInt(), b.readVarInt(), ItemStack.STREAM_CODEC.decode(b),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(b), b.readUtf(64), b.readResourceLocation(), b.readResourceLocation(), b.readUtf(36),
                    b.readEnum(ComponentSpaceTransactions.Action.class), b.readVarInt(),
                    new GridPlacement(b.readVarInt(), b.readVarInt(), b.readEnum(GridRotation.class)), b.readBoolean());
        }
        public void encode(RegistryFriendlyByteBuf b, SpaceActionPayload p) {
            b.writeVarInt(p.containerId()); b.writeVarInt(p.stateId()); b.writeVarInt(p.requestId());
            ItemStack.STREAM_CODEC.encode(b, p.expectedEquipment()); ItemStack.OPTIONAL_STREAM_CODEC.encode(b, p.expectedCarried());
            b.writeUtf(p.definitions(), 64); b.writeResourceLocation(p.source()); b.writeResourceLocation(p.space()); b.writeUtf(p.token(), 36);
            b.writeEnum(p.action()); b.writeVarInt(p.entry()); b.writeVarInt(p.target().x()); b.writeVarInt(p.target().y()); b.writeEnum(p.target().rotation()); b.writeBoolean(p.single());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
