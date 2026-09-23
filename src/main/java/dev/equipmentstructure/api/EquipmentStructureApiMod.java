package dev.equipmentstructure.api;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import dev.equipmentstructure.api.command.EquipmentStructureCommands;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenus;
import dev.equipmentstructure.api.network.EquipmentStructureNetwork;
import dev.equipmentstructure.api.attribute.EquipmentAttributeResolver;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

/**
 * Equipment Structure API 的模组入口。
 *
 * <p>本项目只负责通用装备结构基础设施：装备承载体、槽位、接口、部件
 * 数据、属性解析和结构事件。具体材料、熔炼炉、锻打和装备内容由上层模组提供。</p>
 */
@Mod(EquipmentStructureApiMod.MOD_ID)
public final class EquipmentStructureApiMod {

    public static final String MOD_ID = "equipment_structure_api";
    public static final String NETWORK_VERSION = "0.2";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EquipmentStructureApiMod(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,
                dev.equipmentstructure.api.client.EquipmentTooltipConfig.SPEC);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, EquipmentFeatureConfig.SPEC);
        EquipmentStructureDataComponents.DATA_COMPONENTS.register(modEventBus);
        EquipmentAssemblyMenus.MENUS.register(modEventBus);
        EquipmentComponentRuntime.register(modEventBus);
        if (net.neoforged.fml.ModList.get().isLoaded("curios")) {
            dev.equipmentstructure.api.compat.curios.CuriosArmorCompat.initialize(modEventBus);
        }
        // Content integrations register their own compatibility types and explicit host bindings.
        // Use the typed overload so registration remains reliable when the mod event bus
        // is initialized before annotation scan data is available in a dev run.
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, EquipmentStructureNetwork::register);
        NeoForge.EVENT_BUS.register(EquipmentStructureApiMod.class);
        LOGGER.info("Equipment Structure API initializing.");
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        EquipmentStructureCommands.register(event.getDispatcher());
    }

    /** Injects registered component effects whenever NeoForge queries item attributes. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void addComponentAttributes(ItemAttributeModifierEvent event) {
        EquipmentAttributeResolver.addTo(event.getItemStack(), event);
    }
}
