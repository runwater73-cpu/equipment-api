package dev.equipmentstructure.api.menu;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Registry for the API-provided generic equipment assembly menu. */
public final class EquipmentAssemblyMenus {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(
            Registries.MENU, EquipmentStructureApiMod.MOD_ID
    );

    public static final Supplier<MenuType<EquipmentAssemblyMenu>> ASSEMBLY = MENUS.register(
            "assembly", () -> IMenuTypeExtension.create(EquipmentAssemblyMenu::new)
    );

    private EquipmentAssemblyMenus() {
    }
}
