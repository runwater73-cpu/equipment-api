package dev.equipmentstructure.api.network;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.appearance.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.LinkedHashMap;
import java.util.Map;

/** One confirmation, one equipment snapshot and one atomic server transaction for all edited parts. */
public record SetAppearanceLayoutPayload(int containerId, ItemStack expectedEquipment, ResourceLocation editorSlot,
        Map<ResourceLocation, AppearancePartPresentation> edits, boolean originalVisible) implements CustomPacketPayload {
    public static final int MAX_EDITS = 1024;
    public SetAppearanceLayoutPayload {
        expectedEquipment = expectedEquipment.copy();
        java.util.Objects.requireNonNull(editorSlot, "editorSlot");
        edits = Map.copyOf(edits);
        if (edits.size() > MAX_EDITS) throw new IllegalArgumentException("Too many appearance edits");
    }
    @Override public ItemStack expectedEquipment() { return expectedEquipment.copy(); }
    public static final Type<SetAppearanceLayoutPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "set_appearance_layout"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetAppearanceLayoutPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public SetAppearanceLayoutPayload decode(RegistryFriendlyByteBuf b) {
            int container = b.readVarInt();
            var expected = ItemStack.STREAM_CODEC.decode(b);
            var editor = ResourceLocation.STREAM_CODEC.decode(b);
            boolean visible = b.readBoolean();
            int count = b.readVarInt();
            if (count < 0 || count > MAX_EDITS) throw new IllegalArgumentException("Invalid appearance edit count");
            var edits = new LinkedHashMap<ResourceLocation, AppearancePartPresentation>();
            for (int i = 0; i < count; i++) {
                var slot = ResourceLocation.STREAM_CODEC.decode(b);
                var pose = new AppearancePose(b.readDouble(), b.readDouble(), b.readDouble(),
                        new AppearanceRotation(b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble()), b.readDouble());
                boolean partVisible = b.readBoolean();
                var center = b.readEnum(AppearanceMotionSettings.Center.class);
                var tilt = new AppearanceRotation(b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble());
                if (edits.put(slot, new AppearancePartPresentation(pose, partVisible, new AppearanceMotionSettings(center, tilt))) != null) {
                    throw new IllegalArgumentException("Duplicate appearance slot");
                }
            }
            return new SetAppearanceLayoutPayload(container, expected, editor, edits, visible);
        }
        @Override public void encode(RegistryFriendlyByteBuf b, SetAppearanceLayoutPayload p) {
            b.writeVarInt(p.containerId()); ItemStack.STREAM_CODEC.encode(b, p.expectedEquipment());
            ResourceLocation.STREAM_CODEC.encode(b, p.editorSlot()); b.writeBoolean(p.originalVisible());
            b.writeVarInt(p.edits().size());
            p.edits().forEach((slot, edit) -> {
                ResourceLocation.STREAM_CODEC.encode(b, slot);
                var t = edit.pose().transform();
                b.writeDouble(t.position().x()); b.writeDouble(t.position().y()); b.writeDouble(t.position().z());
                b.writeDouble(t.rotation().x()); b.writeDouble(t.rotation().y());
                b.writeDouble(t.rotation().z()); b.writeDouble(t.rotation().w());
                b.writeDouble(edit.pose().scale()); b.writeBoolean(edit.visible());
                b.writeEnum(edit.motion().center());
                var tilt = edit.motion().tilt();
                b.writeDouble(tilt.x()); b.writeDouble(tilt.y()); b.writeDouble(tilt.z()); b.writeDouble(tilt.w());
            });
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
