package dev.equipmentstructure.api.diagnostic;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.appearance.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static dev.equipmentstructure.api.diagnostic.EquipmentDiagnosticReport.Severity.*;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentVisualDiagnosticsTest {
    private static final ResourceLocation HOST = id("host"), PART = id("part"), SLOT = id("slot"), TYPE = id("type"), ASSET = id("asset");

    @Test void slotDiagnosticsCheckRealLabelsAndSpritesWithoutRequiringDatapackHosts() {
        var display = new dev.equipmentstructure.api.ui.EquipmentSlotDisplay(java.util.Optional.of("custom.name"),
                java.util.Optional.of(ASSET), List.of("custom.help"));
        var ui = dev.equipmentstructure.api.ui.EquipmentStructureUiDefinition.builder().slot(SLOT, display).build();
        var missing = EquipmentVisualDiagnostics.slotDisplays(Map.of(HOST, ui), Map.of(), key -> false, icon -> false);
        assertEquals(2, missing.count(INFO));
        assertEquals(1, missing.count(WARNING));
        assertEquals(0, missing.count(ERROR));
        var valid = EquipmentVisualDiagnostics.slotDisplays(Map.of(HOST, ui), Map.of(), key -> true, ASSET::equals);
        assertTrue(valid.issues().isEmpty());
        var wrongHost = new EquipmentHostDefinition(HOST, TYPE,
                List.of(EquipmentSlotDefinition.of(id("different"), TYPE)));
        var unused = EquipmentVisualDiagnostics.slotDisplays(Map.of(HOST, ui), Map.of(HOST, wrongHost), key -> true, ASSET::equals);
        assertEquals("slot_display_unused", unused.issues().getFirst().code());
    }

    @Test void authoredSlotNameSuppressesFalseMissingConventionalTranslation() {
        var joint = new AppearanceDefinitions.Joint(id("owner"), AppearanceDefinitions.ANCHOR_ONLY, AppearanceTransform.IDENTITY);
        var ui = dev.equipmentstructure.api.ui.EquipmentStructureUiDefinition.builder().slot(SLOT,
                new dev.equipmentstructure.api.ui.EquipmentSlotDisplay(java.util.Optional.of("custom.name"),
                        java.util.Optional.empty(), List.of())).build();
        var report = EquipmentVisualDiagnostics.catalog(catalog(Map.of(SLOT, AppearanceDefinitions.Binding.free(joint))),
                Map.of(PART, component(PART)), Set.of(ASSET),
                key -> !key.startsWith("slot."), Map.of(HOST, ui));
        assertTrue(report.issues().isEmpty());
    }

    @Test void optionalVisualAndAttributeAbsenceIsNotAnError() {
        var report = EquipmentVisualDiagnostics.catalog(new AppearanceCatalog(0, Map.of(), Map.of()),
                Map.of(PART, component(PART)), Set.of(), key -> true);
        assertTrue(report.issues().isEmpty());
        var held = EquipmentVisualDiagnostics.held(structure(), new AppearanceCatalog(0, Map.of(), Map.of()),
                new AppearanceSupport(0, Set.of(), Set.of(), false));
        assertEquals(0, held.count(ERROR));
        assertEquals(0, held.count(WARNING));
        assertEquals("appearance_optional", held.issues().getFirst().code());
    }

    @Test void declaredMissingAssetAndUnknownPartAreReportedSeparately() {
        var catalog = catalog(Map.of());
        var report = EquipmentVisualDiagnostics.catalog(catalog, Map.of(), Set.of(), key -> true);
        assertEquals(Set.of("appearance_component_unknown", "appearance_asset_unprepared"),
                report.issues().stream().map(EquipmentDiagnosticReport.Issue::code).collect(java.util.stream.Collectors.toSet()));
        var ready = EquipmentVisualDiagnostics.catalog(catalog,
                Map.of(PART, component(PART)), Set.of(ASSET), key -> true);
        assertTrue(ready.issues().isEmpty());
    }

    @Test void missingTranslationsAreAdvisoryAndRepeatKeysCollapse() {
        var first = component(PART);
        var second = component(id("another"));
        var report = EquipmentVisualDiagnostics.catalog(catalog(Map.of()), Map.of(PART, first, second.id(), second),
                Set.of(ASSET), key -> false);
        assertEquals(2, report.count(INFO));
        assertEquals(0, report.count(ERROR));
        assertEquals(0, report.count(WARNING));
    }

    @Test void declaredHostWithMissingBindingProducesSpecificFallback() {
        var report = EquipmentVisualDiagnostics.held(structure(), catalog(Map.of()),
                new AppearanceSupport(1, Set.of(ASSET), Set.of(), false));
        assertEquals(1, report.count(WARNING));
        assertTrue(report.issues().getFirst().reference().contains("BINDING_UNCONFIGURED"));
    }

    @Test void readyPlanProducesNoFalsePositiveAndKeepsSnapshot() {
        var before = structure();
        var joint = new AppearanceDefinitions.Joint(id("owner"), AppearanceDefinitions.ANCHOR_ONLY, AppearanceTransform.IDENTITY);
        var report = EquipmentVisualDiagnostics.held(before, catalog(Map.of(SLOT, AppearanceDefinitions.Binding.free(joint))),
                new AppearanceSupport(1, Set.of(ASSET), Set.of(), false));
        assertTrue(report.issues().isEmpty());
        assertEquals(before, structure());
    }

    private static AppearanceCatalog catalog(Map<ResourceLocation, AppearanceDefinitions.Binding> bindings) {
        return new AppearanceCatalog(1, Map.of(HOST, new AppearanceDefinitions.Host(HOST, Map.of(), bindings, Set.of())),
                Map.of(PART, new AppearanceDefinitions.Part(PART, ASSET, AppearanceTransform.IDENTITY, Set.of())));
    }
    private static EquipmentStructure structure() {
        return new EquipmentHostDefinition(HOST, TYPE,
                List.of(EquipmentSlotDefinition.of(SLOT, TYPE))).createStructure()
                .withComponent(SLOT, new EquipmentComponentInstance(PART, TYPE));
    }
    private static EquipmentComponentDefinition component(ResourceLocation id) {
        return EquipmentComponentDefinition.builder(id, TYPE, TYPE)
                .suitableFor(TYPE)
                .footprint(dev.equipmentstructure.api.grid.GridFootprint.SINGLE_CELL)
                .removable(false)
                .build();
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("diagnostic_test", path); }
}
