package dev.equipmentstructure.api.network;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.grid.GridPlacement;
import dev.equipmentstructure.api.grid.GridRotation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/** Client intentions only. Item snapshots are compared with server items, never installed as data. */
public record GridActionPayload(int containerId, int stateId, int requestId, ItemStack expectedEquipment,
        ItemStack expectedCarried, String definitions, Action action, ResourceLocation slotId,
        Map<ResourceLocation, GridPlacement> placements) implements CustomPacketPayload {
    public enum Action { INITIALIZE, MOVE, INSTALL, REPLACE, REMOVE, QUICK_REMOVE }
    public static final int MAX_EDITS = 1024;
    public static final Type<GridActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "grid_action"));
    public GridActionPayload {
        expectedEquipment = expectedEquipment.copy();
        expectedCarried = expectedCarried.copy();
        java.util.Objects.requireNonNull(action);
        java.util.Objects.requireNonNull(slotId);
        placements = Map.copyOf(placements);
        if (!definitions.matches("[0-9a-f]{64}") || placements.size() > MAX_EDITS)
            throw new IllegalArgumentException("Invalid grid request");
        boolean valid = switch (action) {
            case INITIALIZE, REMOVE, QUICK_REMOVE -> placements.isEmpty();
            case INSTALL, REPLACE -> placements.size() == 1 && placements.containsKey(slotId);
            case MOVE -> !placements.isEmpty();
        };
        if (!valid) throw new IllegalArgumentException("Unexpected grid action targets");
    }
    @Override public ItemStack expectedEquipment() { return expectedEquipment.copy(); }
    @Override public ItemStack expectedCarried() { return expectedCarried.copy(); }

    public static final StreamCodec<RegistryFriendlyByteBuf, GridActionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public GridActionPayload decode(RegistryFriendlyByteBuf b) {
            int container = b.readVarInt(), state = b.readVarInt(), request = b.readVarInt();
            var equipment = ItemStack.STREAM_CODEC.decode(b);
            var carried = ItemStack.OPTIONAL_STREAM_CODEC.decode(b);
            String definitions = b.readUtf(64);
            var action = b.readEnum(Action.class);
            var slot = b.readResourceLocation();
            int count = b.readVarInt();
            if (count < 0 || count > MAX_EDITS) throw new IllegalArgumentException("Invalid grid edit count");
            var edits = new LinkedHashMap<ResourceLocation, GridPlacement>();
            for (int n = 0; n < count; n++) {
                var id = b.readResourceLocation();
                var p = new GridPlacement(b.readVarInt(), b.readVarInt(), b.readEnum(GridRotation.class));
                if (edits.put(id, p) != null) throw new IllegalArgumentException("Duplicate grid edit");
            }
            return new GridActionPayload(container, state, request, equipment, carried, definitions, action, slot, edits);
        }
        @Override public void encode(RegistryFriendlyByteBuf b, GridActionPayload p) {
            b.writeVarInt(p.containerId()); b.writeVarInt(p.stateId()); b.writeVarInt(p.requestId());
            ItemStack.STREAM_CODEC.encode(b, p.expectedEquipment());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(b, p.expectedCarried());
            b.writeUtf(p.definitions(), 64); b.writeEnum(p.action()); b.writeResourceLocation(p.slotId());
            b.writeVarInt(p.placements().size());
            p.placements().forEach((id, position) -> {
                b.writeResourceLocation(id); b.writeVarInt(position.x()); b.writeVarInt(position.y()); b.writeEnum(position.rotation());
            });
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
