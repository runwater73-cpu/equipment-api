package dev.equipmentstructure.api.ui;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentComponentRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Lightweight assembly of the API section. Never queries attributes, factories or host initialization. */
public final class EquipmentTooltips {
    private EquipmentTooltips() {}

    public static List<Component> lines(ItemStack stack, boolean expanded, Optional<Component> openKey) {
        EquipmentItemView view = EquipmentItemView.capture(stack);
        Optional<EquipmentComponentInstance> loose = view.structure().isPresent()
                ? Optional.empty() : EquipmentComponentRegistry.fromItemStack(stack);
        if (view.structure().isEmpty() && loose.isEmpty()) return List.of();
        List<Component> lines = new ArrayList<>();
        if (view.structure().isPresent()) {
            lines.add(Component.translatable("tooltip.equipment_structure_api.installed",
                    view.installedCount(), view.slots().size()).withStyle(ChatFormatting.GRAY));
            openKey.ifPresent(key -> lines.add(Component.translatable("tooltip.equipment_structure_api.open", key.copy())
                    .withStyle(ChatFormatting.DARK_GRAY)));
        }
        EquipmentTooltipRegistry.resolve(new EquipmentTooltipContext(view, loose, expanded)).ifPresent(display -> {
            if (display.replaceDefaults()) lines.clear();
            lines.addAll(display.lines());
        });
        return List.copyOf(lines);
    }
}
