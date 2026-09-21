package dev.equipmentstructure.api.network;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.appearance.AppearancePose;
import dev.equipmentstructure.api.appearance.AppearanceRotation;
import dev.equipmentstructure.api.appearance.AppearanceTransform;
import dev.equipmentstructure.api.appearance.AppearanceVector;
import dev.equipmentstructure.api.appearance.AppearanceVisibility;
import dev.equipmentstructure.api.appearance.AppearanceVisibilityStorage;
import dev.equipmentstructure.api.EquipmentStructureApi;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative confirmation of one interface instance's visual pose. */
public record SetAppearancePosePayload(int containerId, ItemStack expectedEquipment, ResourceLocation slotId, AppearancePose pose,
                                      AppearanceVisibility visibility)
        implements CustomPacketPayload {
    public SetAppearancePosePayload {
        expectedEquipment = expectedEquipment.copy();
        java.util.Objects.requireNonNull(visibility, "visibility");
    }

    public SetAppearancePosePayload(int containerId, ItemStack expectedEquipment, ResourceLocation slotId, AppearancePose pose) {
        this(containerId, expectedEquipment, slotId, pose, new AppearanceVisibility(
                AppearanceVisibilityStorage.originalVisible(expectedEquipment),
                EquipmentStructureApi.component(expectedEquipment, slotId)
                        .map(AppearanceVisibilityStorage::componentVisible).orElse(true)));
    }

    @Override public ItemStack expectedEquipment() { return expectedEquipment.copy(); }
    public static final Type<SetAppearancePosePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EquipmentStructureApiMod.MOD_ID, "set_appearance_pose"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetAppearancePosePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override public SetAppearancePosePayload decode(RegistryFriendlyByteBuf b) {
                    return new SetAppearancePosePayload(b.readVarInt(), ItemStack.STREAM_CODEC.decode(b), ResourceLocation.STREAM_CODEC.decode(b),
                            new AppearancePose(new AppearanceTransform(new AppearanceVector(b.readDouble(), b.readDouble(), b.readDouble()),
                                    new AppearanceRotation(b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble())), b.readDouble()),
                            new AppearanceVisibility(b.readBoolean(), b.readBoolean()));
                }
                @Override public void encode(RegistryFriendlyByteBuf b, SetAppearancePosePayload p) {
                    b.writeVarInt(p.containerId());
                    ItemStack.STREAM_CODEC.encode(b, p.expectedEquipment());
                    ResourceLocation.STREAM_CODEC.encode(b, p.slotId());
                    var t = p.pose().transform();
                    b.writeDouble(t.position().x()); b.writeDouble(t.position().y()); b.writeDouble(t.position().z());
                    b.writeDouble(t.rotation().x()); b.writeDouble(t.rotation().y());
                    b.writeDouble(t.rotation().z()); b.writeDouble(t.rotation().w());
                    b.writeDouble(p.pose().scale());
                    b.writeBoolean(p.visibility().originalVisible());
                    b.writeBoolean(p.visibility().componentVisible());
                }
            };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
