package dev.equipmentstructure.api.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.equipmentstructure.api.diagnostic.EquipmentDiagnosticReport;
import dev.equipmentstructure.api.diagnostic.EquipmentIntegrationDiagnostics;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Paged, local feedback. Reports never broadcast to other players or write item data. */
public final class EquipmentDiagnosticCommands {
    private EquipmentDiagnosticCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> server() {
        return Commands.literal("diagnose").requires(source -> source.hasPermission(2))
                .executes(context -> registrations(context.getSource(), 1))
                .then(branch("registrations", EquipmentDiagnosticCommands::registrations))
                .then(branch("held", (source, page) -> display(source,
                        EquipmentIntegrationDiagnostics.held(source.getPlayerOrException().getMainHandItem(),
                                source.registryAccess()), page)));
    }

    @FunctionalInterface
    public interface Query { int run(CommandSourceStack source, int page) throws CommandSyntaxException; }

    public static LiteralArgumentBuilder<CommandSourceStack> branch(String name, Query query) {
        return Commands.literal(name).executes(context -> query.run(context.getSource(), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> query.run(context.getSource(), IntegerArgumentType.getInteger(context, "page"))));
    }

    private static int registrations(CommandSourceStack source, int page) {
        return display(source, EquipmentIntegrationDiagnostics.registrations(source.registryAccess()), page);
    }

    public static int display(CommandSourceStack source, EquipmentDiagnosticReport report, int page) {
        source.sendSuccess(() -> Component.translatable("diagnostic.equipment_structure_api.summary",
                Component.translatable("diagnostic.equipment_structure_api.scope." + report.scope()),
                report.count(EquipmentDiagnosticReport.Severity.ERROR), report.count(EquipmentDiagnosticReport.Severity.WARNING),
                report.count(EquipmentDiagnosticReport.Severity.INFO), report.boundedPage(page), report.pages()), false);
        for (var issue : report.page(page)) {
            var color = switch (issue.severity()) {
                case ERROR -> ChatFormatting.RED;
                case WARNING -> ChatFormatting.YELLOW;
                case INFO -> ChatFormatting.GRAY;
            };
            source.sendSuccess(() -> Component.literal("[" + issue.code() + "] ")
                    .append(Component.translatable("diagnostic.equipment_structure_api." + issue.code(),
                            issue.subject(), issue.reference())).withStyle(color), false);
        }
        // Command success means the inspection ran, not that the integration is certified.
        return 1;
    }
}
