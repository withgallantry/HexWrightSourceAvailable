package com.bluup.hexwright.server.malediction;

import com.bluup.hexwright.common.malediction.MaledictionFinish;
import com.bluup.hexwright.common.malediction.MaledictionForm;
import com.bluup.hexwright.server.command.CommandGate;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class MaledictionCommand {

    private MaledictionCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hexwright")
                .requires(CommandGate::creative)
                .then(Commands.literal("malediction")
                    .then(Commands.literal("learn").executes(MaledictionCommand::learn))
                    .then(Commands.literal("recharge").executes(MaledictionCommand::recharge)))));
    }

    private static int learn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MaledictionState state = MaledictionState.get(player.server);
        for (MaledictionForm form : MaledictionForm.values()) {
            state.learn(player.getUUID(), form);
        }
        for (MaledictionFinish finish : MaledictionFinish.values()) {
            state.learn(player.getUUID(), finish);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Studied every Form and Finish."), false);
        return SINGLE_SUCCESS;
    }

    private static int recharge(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MaledictionState.get(player.server).refill(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("Every Form is back to full."), false);
        return SINGLE_SUCCESS;
    }
}
