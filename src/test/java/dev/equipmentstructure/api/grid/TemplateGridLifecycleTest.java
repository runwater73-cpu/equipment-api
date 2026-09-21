package dev.equipmentstructure.api.grid;

import dev.equipmentstructure.api.BuiltinEquipmentTemplates;
import dev.equipmentstructure.api.EquipmentHostDefinition;
import dev.equipmentstructure.api.EquipmentStructureRegistries;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import com.mojang.serialization.Lifecycle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class TemplateGridLifecycleTest {
    private static final ResourceLocation HOST = ResourceLocation.fromNamespaceAndPath("lifecycle_test", "host");

    @AfterEach void cleanup() {
        EquipmentGridRegistry.replaceTemplates(Map.of());
        EquipmentGridRegistry.unregisterHost(HOST);
        GridDefinitionSync.clearClient();
    }

    @Test void datapackBoardsAreAvailableBeforeAnyEquipmentIsInitialized() {
        var definition = BuiltinEquipmentTemplates.definition(BuiltinEquipmentTemplates.SWORD,
                dev.equipmentstructure.api.BuiltinEquipmentTypes.SWORD,
                List.of(dev.equipmentstructure.api.BuiltinEquipmentSlots.BLADE));
        var registry = new MappedRegistry<EquipmentHostDefinition>(EquipmentStructureRegistries.HOST_DEFINITION, Lifecycle.stable());
        registry.register(ResourceKey.create(EquipmentStructureRegistries.HOST_DEFINITION, definition.id()),
                definition, RegistrationInfo.BUILT_IN);
        GridDefinitionSync.loadTemplates(new RegistryAccess.ImmutableRegistryAccess(List.of(registry.freeze())));
        var serverSnapshot = GridDefinitions.registered();
        assertEquals(definition.grid().orElseThrow(), serverSnapshot.hosts().get(definition.id()));
        assertTrue(GridTransactions.resolve(definition.createStructure(), serverSnapshot).layout().isPresent());
        GridDefinitionSync.receive(serverSnapshot);
        assertTrue(GridDefinitionSync.current().hosts().containsKey(definition.id()));
    }

    @Test void templateReplacementAndRemovalRestoreCodeBoardWithoutStaleWorldData() {
        var code = GridBoard.defaultBoard(GridShape.rectangle(1, 1));
        var template = GridBoard.defaultBoard(GridShape.rectangle(2, 2));
        EquipmentGridRegistry.registerHost(HOST, code);
        var original = GridDefinitions.registered();
        EquipmentGridRegistry.replaceTemplates(Map.of(HOST, template));
        assertEquals(template, GridDefinitions.registered().hosts().get(HOST));
        assertEquals(code, original.hosts().get(HOST));
        EquipmentGridRegistry.replaceTemplates(Map.of());
        assertEquals(code, EquipmentGridRegistry.host(HOST).orElseThrow());
        EquipmentGridRegistry.unregisterHost(HOST);
        assertFalse(GridDefinitions.registered().hosts().containsKey(HOST));
    }
}
