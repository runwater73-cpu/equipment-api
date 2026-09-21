package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.EquipmentComponentRegistry;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.EquipmentStructureApiMod;
import dev.equipmentstructure.api.client.appearance.*;
import dev.equipmentstructure.api.command.EquipmentDiagnosticCommands;
import dev.equipmentstructure.api.diagnostic.EquipmentDiagnosticReport;
import dev.equipmentstructure.api.diagnostic.EquipmentVisualDiagnostics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Client-local commands; dedicated servers never load language, model or renderer classes. */
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID, value = Dist.CLIENT)
public final class EquipmentDiagnosticClientCommands {
    private EquipmentDiagnosticClientCommands() {}

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("equipment_structure_api_client")
                .then(Commands.literal("diagnose")
                        .executes(context -> EquipmentDiagnosticCommands.display(context.getSource(), resources(), 1))
                        .then(EquipmentDiagnosticCommands.branch("resources", (source, page) ->
                                EquipmentDiagnosticCommands.display(source, resources(), page)))
                        .then(EquipmentDiagnosticCommands.branch("held", (source, page) -> {
                            var structure = EquipmentStructureApi.structure(source.getPlayerOrException().getMainHandItem());
                            var report = structure.map(value -> EquipmentVisualDiagnostics.held(value,
                                    AppearanceRuntime.catalog(), AppearanceModelAssetRegistry.support(Set.of(), false)))
                                    .orElseGet(() -> new EquipmentDiagnosticReport("client_held_default_item", List.of(
                                            new EquipmentDiagnosticReport.Issue(EquipmentDiagnosticReport.Severity.INFO,
                                                    "held_no_structure", "held", ""))));
                            return EquipmentDiagnosticCommands.display(source, withReloadErrors(report), page);
                        }))));
    }

    private static EquipmentDiagnosticReport resources() {
        var assets = new HashSet<>(AppearanceModelAssetRegistry.assets());
        assets.addAll(AppearanceGuiAssetRegistry.assets());
        var hosts = new java.util.HashMap<net.minecraft.resources.ResourceLocation, dev.equipmentstructure.api.EquipmentHostDefinition>();
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null) level.registryAccess().registry(dev.equipmentstructure.api.EquipmentStructureRegistries.HOST_DEFINITION)
                .ifPresent(registry -> registry.entrySet().forEach(entry -> hosts.put(entry.getKey().location(), entry.getValue())));
        var displays = dev.equipmentstructure.api.ui.EquipmentStructureUiRegistry.definitions(hosts);
        var report = EquipmentVisualDiagnostics.catalog(AppearanceRuntime.catalog(),
                EquipmentComponentRegistry.definitions(), assets, I18n::exists, displays);
        var issues = new ArrayList<>(report.issues());
        issues.addAll(EquipmentVisualDiagnostics.slotDisplays(displays, hosts, I18n::exists,
                EquipmentAssemblyGridView::hasSprite).issues());
        return withReloadErrors(new EquipmentDiagnosticReport(report.scope(), issues));
    }

    private static EquipmentDiagnosticReport withReloadErrors(EquipmentDiagnosticReport report) {
        var issues = new ArrayList<>(report.issues());
        AppearanceResourceReloadListener.lastErrors().forEach(error -> issues.add(new EquipmentDiagnosticReport.Issue(
                EquipmentDiagnosticReport.Severity.ERROR, "appearance_reload_failed",
                Long.toString(AppearanceResourceReloadListener.generation()), error)));
        EquipmentSlotDisplayReloadListener.lastErrors().forEach(error -> issues.add(new EquipmentDiagnosticReport.Issue(
                EquipmentDiagnosticReport.Severity.ERROR, "slot_display_reload_failed", "slot_display", error)));
        GridComponentDisplayReloadListener.lastErrors().forEach(error -> issues.add(new EquipmentDiagnosticReport.Issue(
                EquipmentDiagnosticReport.Severity.ERROR, "grid_display_reload_failed", "grid_display", error)));
        return new EquipmentDiagnosticReport(report.scope(), issues);
    }
}
