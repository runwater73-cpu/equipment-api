package dev.equipmentstructure.api.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.equipmentstructure.api.EquipmentHostProviders;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Server entry point used by the API key binding and integration code. */
public final class EquipmentStructureCommands {

    private EquipmentStructureCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("equipment_structure_api")
                .then(Commands.literal("open").executes(context -> open(context.getSource())))
                .then(EquipmentDiagnosticCommands.server()));
    }

    private static int open(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Player player = source.getPlayerOrException();
        if (!openForPlayer(player)) {
            source.sendFailure(Component.translatable(
                    "commands.equipment_structure_api.open.unsupported"
            ));
            return 0;
        }
        return 1;
    }

    /** Opens the API menu after validating and initializing the player's held equipment. */
    public static boolean openForPlayer(Player player) {
        if (!player.isAlive() || player.isRemoved()) return false;
        // Repeated requests in flight must not close/reopen and transfer equipment twice.
        if (player.containerMenu instanceof EquipmentAssemblyMenu) return true;
        if (player.containerMenu != player.inventoryMenu) return false;
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        var server = player.level().getServer();
        if (server == null) {
            return false;
        }

        ItemStack equipment = ItemStack.EMPTY;
        boolean movedHeldEquipment = false;
        if (!held.isEmpty()
                && (EquipmentStructureApi.hasStructure(held)
                || EquipmentHostProviders.resolve(held, server.registryAccess()).isPresent())) {
            equipment = held.copyWithCount(1);
            if (!EquipmentStructureApi.hasStructure(equipment)
                    && !EquipmentStructureApi.initializeFromProvider(equipment, server.registryAccess())) {
                return false;
            }
            movedHeldEquipment = true;
        }
        dev.equipmentstructure.api.EquipmentSlotItemAdapters.prepare(equipment, server.registryAccess(), player);
        java.util.List<EquipmentSlotDefinition> definitions = EquipmentStructureApi.slots(equipment);
        if (!dev.equipmentstructure.api.menu.EquipmentAssemblyLimits.supports(definitions.size())) return false;
        SimpleContainer container = new SimpleContainer(
                EquipmentAssemblyMenu.containerSize()
        );
        container.setItem(EquipmentAssemblyMenu.EQUIPMENT_SLOT, equipment);
        SimpleContainer menuContainer = container;
        ItemStack originalHeld = held.copy();
        if (movedHeldEquipment) {
            player.setItemInHand(InteractionHand.MAIN_HAND, held.copyWithCount(held.getCount() - 1));
        }
        // A code-defined board may have been added after login. Send its generation before opening the screen.
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            dev.equipmentstructure.api.grid.GridDefinitionSync.loadTemplates(server.registryAccess());
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                    new dev.equipmentstructure.api.network.GridDefinitionsPayload(
                            dev.equipmentstructure.api.grid.GridDefinitions.registered()));
        }
        var opened = player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new EquipmentAssemblyMenu(
                        containerId, inventory, menuContainer, definitions
                ),
                Component.translatable("gui.equipment_structure_api.assembly.title")
        ), data -> EquipmentAssemblyMenu.writeDefinitions(data, definitions));
        if (opened.isEmpty()) {
            if (movedHeldEquipment) player.setItemInHand(InteractionHand.MAIN_HAND, originalHeld);
            return false;
        }
        return true;
    }
}
