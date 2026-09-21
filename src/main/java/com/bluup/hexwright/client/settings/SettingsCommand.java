package com.bluup.hexwright.client.settings;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

@Environment(EnvType.CLIENT)
public final class SettingsCommand {

    private SettingsCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("hexwright")
                .then(ClientCommandManager.literal("settings").executes(SettingsCommand::open))
                .then(ClientCommandManager.argument("subcommand", StringArgumentType.greedyString())
                    .executes(SettingsCommand::passToServer))));
    }

    private static int open(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft client = ctx.getSource().getClient();
        client.execute(() -> client.setScreen(new HexwrightSettingsScreen(null)));
        return Command.SINGLE_SUCCESS;
    }

    private static int passToServer(CommandContext<FabricClientCommandSource> ctx) {
        ClientPacketListener connection = ctx.getSource().getClient().getConnection();
        if (connection != null) {
            connection.sendCommand(ctx.getInput());
        }
        return Command.SINGLE_SUCCESS;
    }
}
