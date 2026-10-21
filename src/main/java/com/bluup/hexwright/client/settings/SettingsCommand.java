package com.bluup.hexwright.client.settings;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.bluup.hexwright.client.vehicle.VehicleDebugOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class SettingsCommand {

    private SettingsCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("hexwright")
                .then(ClientCommandManager.literal("settings").executes(SettingsCommand::open))
                .then(ClientCommandManager.literal("flightDebug")
                    .executes(ctx -> setFlightDebug(ctx, !VehicleDebugOverlay.enabled()))
                    .then(ClientCommandManager.literal("on").executes(ctx -> setFlightDebug(ctx, true)))
                    .then(ClientCommandManager.literal("off").executes(ctx -> setFlightDebug(ctx, false))))
                .then(ClientCommandManager.argument("subcommand", StringArgumentType.greedyString())
                    .executes(SettingsCommand::passToServer))));
    }

    private static int open(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft client = ctx.getSource().getClient();
        client.execute(() -> client.setScreen(new HexwrightSettingsScreen(null)));
        return Command.SINGLE_SUCCESS;
    }

    private static int setFlightDebug(CommandContext<FabricClientCommandSource> ctx, boolean on) {
        VehicleDebugOverlay.setEnabled(on);
        ctx.getSource().sendFeedback(Component.translatable(
            on ? "commands.hexwright.flight_debug.on" : "commands.hexwright.flight_debug.off"));
        return Command.SINGLE_SUCCESS;
    }

    private static int passToServer(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create();
    }
}
