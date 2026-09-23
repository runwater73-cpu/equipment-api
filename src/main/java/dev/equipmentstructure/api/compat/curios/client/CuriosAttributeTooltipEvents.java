package dev.equipmentstructure.api.compat.curios.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.AddAttributeTooltipsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;
import java.util.List;

/** Reuses addon tooltip listeners after the installed Curios slot has been evaluated. */
public final class CuriosAttributeTooltipEvents {
    private static final ThreadLocal<AddAttributeTooltipsEvent> CURRENT = new ThreadLocal<>();
    private CuriosAttributeTooltipEvents() {}

    public static boolean includesNativeCurios(AddAttributeTooltipsEvent event) { return CURRENT.get() == event; }

    public static void append(ItemStack item, AttributeTooltipContext context, List<Component> lines) {
        var event = new AddAttributeTooltipsEvent(item, lines::add, context);
        var previous = CURRENT.get();
        CURRENT.set(event);
        try { NeoForge.EVENT_BUS.post(event); }
        finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
