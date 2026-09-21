package dev.equipmentstructure.api;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * API 提供的可数据驱动装备模板 Registry。
 *
 * <p>内容模组可以在 Java 中直接创建模板，也可以通过数据包提供模板。
 * Registry 只保存槽位和版本等结构信息，不保存具体装备属性。</p>
 */
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class EquipmentStructureRegistries {

    public static final ResourceKey<Registry<EquipmentHostDefinition>> HOST_DEFINITION =
            ResourceKey.createRegistryKey(id("host_definition"));
    public static final DataMapType<Item, EquipmentHostBinding> HOST_BINDING = DataMapType.builder(
            id("host_binding"),
            Registries.ITEM,
            EquipmentHostBinding.CODEC
    ).synced(EquipmentHostBinding.CODEC, false).build();

    private EquipmentStructureRegistries() {
    }

    @SubscribeEvent
    public static void registerDatapackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(
                HOST_DEFINITION,
                EquipmentHostDefinition.CODEC,
                EquipmentHostDefinition.CODEC
        );
    }

    @SubscribeEvent
    public static void registerDataMaps(RegisterDataMapTypesEvent event) {
        event.register(HOST_BINDING);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                EquipmentStructureApiMod.MOD_ID,
                path
        );
    }
}
