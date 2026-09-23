package dev.equipmentstructure.api.compat.curios;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.equipmentstructure.api.EquipmentStructureApiMod;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Resource stacks are applied in pack order; malformed changes retain the previous complete snapshot. */
final class CuriosDefinitionLoader extends SimplePreparableReloadListener<CuriosDefinitions> {
    private static final String DIRECTORY = "equipment_structure_api/curios_compat";
    @Override protected CuriosDefinitions prepare(ResourceManager manager, ProfilerFiller profiler) {
        CuriosDefinitions result = CuriosDefinitions.javaDefaults();
        try {
            for (var entry : manager.listResourceStacks(DIRECTORY, id -> id.getPath().endsWith(".json"))
                    .entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
                for (var resource : entry.getValue()) {
                    try (var reader = resource.openAsReader()) {
                        result = result.overlay(CuriosDefinitions.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow());
                    }
                }
            }
            return result;
        } catch (Exception failure) {
            EquipmentStructureApiMod.LOGGER.error("Curios armor definitions rejected; retaining previous definitions", failure);
            return CuriosArmorCompat.serverDefinitions();
        }
    }
    @Override protected void apply(CuriosDefinitions definitions, ResourceManager manager, ProfilerFiller profiler) {
        CuriosArmorCompat.replaceServerDefinitions(definitions);
    }
}
