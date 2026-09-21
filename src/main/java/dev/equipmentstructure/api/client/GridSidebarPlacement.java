package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.grid.*;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;

/** A replacement retains its location when possible, otherwise finds space without moving any other part. */
final class GridSidebarPlacement {
    private GridSidebarPlacement() {}
    static Optional<GridPlacement> find(GridLayout layout, ResourceLocation slot, GridFootprint footprint) {
        return find(layout, slot, footprint, java.util.List.of());
    }
    static Optional<GridPlacement> find(GridLayout layout, ResourceLocation slot, GridFootprint footprint,
            java.util.List<dev.equipmentstructure.api.grid.space.ComponentSpaceDefinition> spaces) {
        var old = layout.parts().get(slot);
        if (old != null && layout.checkPlacement(slot, footprint, old.placement(), spaces).allowed()) return Optional.of(old.placement());
        return GridTransactions.firstFit(layout, slot, footprint, spaces);
    }
}
