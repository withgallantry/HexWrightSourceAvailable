package com.bluup.hexwright.server.worldgen.quota;

import com.bluup.hexwright.server.command.CommandGate;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class BorderQuotaCommand {

    private BorderQuotaCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hexwright")
                .requires(CommandGate::creative)
                .then(Commands.literal("quota")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("status").executes(BorderQuotaCommand::status))
                    .then(Commands.literal("check").executes(BorderQuotaCommand::check)))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Map<ResourceKey<Level>, List<String>> reports = BorderQuotas.reports();
        if (reports.isEmpty()) {
            source.sendSuccess(() -> Component.literal(BorderQuotas.counting()
                ? "Counting now; ask again in a moment."
                : "Nothing counted yet. The first count runs a minute after the server starts, or run /hexwright quota check.")
                .withStyle(ChatFormatting.AQUA), false);
        }
        for (Map.Entry<ResourceKey<Level>, List<String>> report : reports.entrySet()) {
            source.sendSuccess(() -> Component.literal(report.getKey().location() + ", " + String.join("\n", report.getValue()))
                .withStyle(ChatFormatting.AQUA), false);
        }
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (PlantedSites.Site site : PlantedSites.get(level).sites()) {
                BlockPos centre = site.box().getCenter();
                source.sendSuccess(() -> Component.literal("Planted in " + level.dimension().location() + ": "
                    + site.structure().location() + " at " + centre.getX() + ", " + centre.getZ()
                    + (site.standing() ? "" : " (planned, not yet generated)")).withStyle(ChatFormatting.GRAY), false);
            }
        }
        return SINGLE_SUCCESS;
    }

    private static int check(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!BorderQuotas.countNow(source.getServer())) {
            source.sendFailure(Component.literal("A count is already under way."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Counting every level within its border; /hexwright quota status shows what was found.")
            .withStyle(ChatFormatting.AQUA), true);
        return SINGLE_SUCCESS;
    }
}
