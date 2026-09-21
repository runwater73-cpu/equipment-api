package dev.equipmentstructure.api.client.appearance;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.appearance.AppearanceDefinitions;
import dev.equipmentstructure.api.appearance.AppearancePlan;
import dev.equipmentstructure.api.appearance.AppearanceResolver;
import dev.equipmentstructure.api.appearance.AppearanceSupport;
import dev.equipmentstructure.api.appearance.AppearanceTransform;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppearanceModelAssetRegistryOrderTest {
    @Test
    void placementsAreOrderedByLayerThenOrderThenSlotId() {
        var hostId = id("host");
        var slotStructure = List.of(
                EquipmentSlotDefinition.of(id("z_surface"), id("type")),
                EquipmentSlotDefinition.of(id("a_structure"), id("type")),
                EquipmentSlotDefinition.of(id("c_structure"), id("type")),
                EquipmentSlotDefinition.of(id("b_effect"), id("type")));
        var parts = Map.of(
                id("z_surface"), List.of(new EquipmentComponentInstance(id("surface"), id("type"))),
                id("a_structure"), List.of(new EquipmentComponentInstance(id("structure"), id("type"))),
                id("c_structure"), List.of(new EquipmentComponentInstance(id("structure"), id("type"))),
                id("b_effect"), List.of(new EquipmentComponentInstance(id("effect"), id("type"))));
        var structure = new EquipmentStructure(hostId, hostId, slotStructure, parts);
        var host = new AppearanceDefinitions.Host(hostId,
                Map.of(id("surface_port"), new AppearanceDefinitions.Port(AppearanceTransform.IDENTITY),
                        id("structure_port"), new AppearanceDefinitions.Port(AppearanceTransform.IDENTITY),
                        id("effect_port"), new AppearanceDefinitions.Port(AppearanceTransform.IDENTITY)),
                Map.of(id("z_surface"), binding(id("surface_port")),
                        id("a_structure"), binding(id("structure_port")),
                        id("c_structure"), binding(id("structure_port")),
                        id("b_effect"), binding(id("effect_port"))), Set.of());
        var catalog = new dev.equipmentstructure.api.appearance.AppearanceCatalog(0, Map.of(hostId, host),
                Map.of(id("surface"), part(id("surface"), AppearanceDefinitions.RenderMode.SURFACE,
                                AppearanceDefinitions.RenderLayer.SURFACE, 0),
                        id("structure"), part(id("structure"), AppearanceDefinitions.RenderMode.GEOMETRY,
                                AppearanceDefinitions.RenderLayer.STRUCTURE, 3),
                        id("effect"), part(id("effect"), AppearanceDefinitions.RenderMode.EFFECT,
                                AppearanceDefinitions.RenderLayer.EFFECT, -4)));
        var plan = AppearanceResolver.resolve(structure, catalog,
                new AppearanceSupport(0, Set.of(id("surface_asset"), id("structure_asset"), id("effect_asset")), Set.of(), true));
        assertEquals(List.of(id("a_structure"), id("c_structure"), id("z_surface"), id("b_effect")),
                plan.orderedPlacements().stream().map(AppearancePlan.Placement::slotId).toList());
    }

    private static AppearanceDefinitions.Part part(ResourceLocation id, AppearanceDefinitions.RenderMode mode,
                                                    AppearanceDefinitions.RenderLayer layer, int order) {
        return new AppearanceDefinitions.Part(id, id(id.getPath() + "_asset"), AppearanceTransform.IDENTITY, Set.of(),
                new AppearanceDefinitions.RenderProfile(mode, layer, order, 0));
    }

    private static AppearanceDefinitions.Binding binding(ResourceLocation port) {
        return new AppearanceDefinitions.Binding(port,
                new AppearanceDefinitions.Joint(id("owner"), AppearanceDefinitions.ANCHOR_ONLY,
                        AppearanceTransform.IDENTITY), Set.of());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("render_order_test", path);
    }
}
