package dev.equipmentstructure.api.compat.curios;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.extensions.ICurioSlotExtension;

/** Creative clone invokes the same slot extension as Curios' own menu. */
public record CuriosSlotClonePayload(int containerId, int stateId, ResourceLocation slot, boolean personal) implements CustomPacketPayload {
    public static final Type<CuriosSlotClonePayload> TYPE = new Type<>(CuriosSlotKey.id("slot_clone"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CuriosSlotClonePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public CuriosSlotClonePayload decode(RegistryFriendlyByteBuf b) {
            return new CuriosSlotClonePayload(b.readVarInt(), b.readVarInt(), b.readResourceLocation(), b.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf b, CuriosSlotClonePayload p) {
            b.writeVarInt(p.containerId); b.writeVarInt(p.stateId); b.writeResourceLocation(p.slot); b.writeBoolean(p.personal);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public boolean apply(Player player) {
        if (player.level().isClientSide() || !player.hasInfiniteMaterials()
                || !(player.containerMenu instanceof EquipmentAssemblyMenu menu) || !menu.stillValid(player)
                || menu.containerId != containerId || menu.getStateId() != stateId || !menu.getCarried().isEmpty()) return false;
        var key = CuriosSlotKey.parse(slot).orElse(null);
        if (key == null || !CuriosArmorCompat.managesType(player, key.type())) return false;
        ItemStack item;
        if (personal) {
            if (!CuriosBindingActions.keys(player).contains(key)) return false;
            item = PlayerBoundCurios.item(player, slot);
        } else {
            var structure = EquipmentStructureApi.structure(menu.equipmentStack()).orElse(null);
            if (structure == null || structure.slot(slot).isEmpty()) return false;
            item = structure.component(slot).flatMap(EquipmentComponentRegistry::createValidatedItemStack).orElse(ItemStack.EMPTY);
        }
        var clone = ICurioSlotExtension.from(key.type()).getCloneStack(CuriosArmorCompat.context(player, key), item.copy());
        if (clone.isEmpty()) return false;
        menu.setCarried(PlayerBoundCurios.released(clone.copyWithCount(clone.getMaxStackSize())));
        menu.broadcastChanges(); return true;
    }
}
