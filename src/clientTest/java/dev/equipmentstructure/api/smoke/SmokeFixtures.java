package dev.equipmentstructure.api.smoke;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.grid.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.Optional;

/** Opt-in loopback acceptance fixture; never compiled into release artifacts. */
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class SmokeFixtures {
    private static ServerPlayer pending;
    private static int ticks;
    private static boolean departed;
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event) {
        if (Boolean.getBoolean("equipment_structure_api.artifactsSmoke") || Boolean.getBoolean("equipment_structure_api.celestialSmoke")) return;
        event.enqueueWork(() -> {
            EquipmentHostProviders.register(Items.IRON_SWORD, BuiltinEquipmentTemplates.SWORD);
            var id = ResourceLocation.fromNamespaceAndPath("equipment_structure_api", "smoke_blade");
            EquipmentComponentRegistry.register(EquipmentComponentDefinition.builder(id, BuiltinEquipmentSlots.BLADE, BuiltinEquipmentSlots.BLADE)
                    .suitableFor(BuiltinEquipmentTypes.SWORD)
                    .footprint(GridFootprint.freelyRotating(GridShape.mask("##", ".#")))
                    .itemFactory(part -> new ItemStack(Items.AMETHYST_SHARD)).build());
            EquipmentComponentItemAdapters.register(id, Items.AMETHYST_SHARD, id, stack -> Optional.of(new CompoundTag()));
        });
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (Boolean.getBoolean("equipment_structure_api.curiosSmoke") || Boolean.getBoolean("equipment_structure_api.enigmaticSmoke")
                || Boolean.getBoolean("equipment_structure_api.artifactsSmoke") || Boolean.getBoolean("equipment_structure_api.celestialSmoke")) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.serverLevel().setDayTime(6000L);
        if (!GridDefinitions.registered().hosts().containsKey(BuiltinEquipmentTemplates.SWORD))
            throw new IllegalStateException("Template board was not loaded before login");
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        player.getInventory().setItem(9, new ItemStack(Items.AMETHYST_SHARD));
        pending = player; ticks = 0; departed = false;
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { departed = true; ticks = 0; }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        ticks++;
        if (pending != null && ticks == 40) {
            if (!dev.equipmentstructure.api.command.EquipmentStructureCommands.openForPlayer(pending))
                throw new IllegalStateException("Cannot open sword template");
            pending = null;
        }
        if (departed && ticks > 60) event.getServer().halt(false);
    }
}
