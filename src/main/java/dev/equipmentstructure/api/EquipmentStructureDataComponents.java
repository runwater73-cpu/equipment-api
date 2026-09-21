package dev.equipmentstructure.api;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** ItemStack 上的装备结构数据组件。 */
public final class EquipmentStructureDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, EquipmentStructureApiMod.MOD_ID);

    /** 保存一件具体装备的主体、槽位、接口和已安装部件。 */
    public static final Supplier<DataComponentType<EquipmentStructure>> EQUIPMENT_STRUCTURE =
            DATA_COMPONENTS.register(
                    "equipment_structure",
                    () -> DataComponentType.<EquipmentStructure>builder()
                            .persistent(EquipmentStructure.CODEC)
                            .networkSynchronized(ByteBufCodecs.fromCodec(EquipmentStructure.CODEC))
                            .build()
            );

    private EquipmentStructureDataComponents() {
    }
}
