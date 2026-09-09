package com.bluup.hexwright.server.journal;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class JournalDebugCommand {

    private JournalDebugCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hexwright")
                .then(Commands.literal("investigations")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("complete").executes(JournalDebugCommand::completeAll))
                    .then(Commands.literal("clear").executes(JournalDebugCommand::clear))
                    .then(Commands.literal("journal").executes(JournalDebugCommand::giveJournal)))));
    }

    private static int completeAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = playerOrFail(source);
        if (player == null) {
            return 0;
        }

        int total = Investigations.all().size();
        if (total == 0) {
            source.sendFailure(Component.literal(
                "No investigations are loaded - check the log for a parse error in investigations.json."));
            return 0;
        }

        int closed = InvestigationProgress.completeAll(player);
        source.sendSuccess(
            () -> Component.literal("Completed " + closed + " of " + total
                + " investigation(s); all " + total + " rows are now visible.")
                .withStyle(ChatFormatting.AQUA),
            false
        );
        return SINGLE_SUCCESS;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = playerOrFail(source);
        if (player == null) {
            return 0;
        }

        boolean changed = InvestigationProgress.resetAll(player);
        source.sendSuccess(
            () -> Component.literal(changed
                ? "Cleared your investigation progress and tallies. Anything whose condition is true"
                    + " right now will tick itself off again within a second."
                : "You had no investigation progress to clear.")
                .withStyle(ChatFormatting.AQUA),
            false
        );
        return SINGLE_SUCCESS;
    }

    private static int giveJournal(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = playerOrFail(source);
        if (player == null) {
            return 0;
        }

        InvestigationState.get(player.server).forgetStarterJournal(player.getUUID());
        StarterJournal.give(player);
        source.sendSuccess(
            () -> Component.literal("Handed you a Field Journal.").withStyle(ChatFormatting.AQUA),
            false
        );
        return SINGLE_SUCCESS;
    }

    private static ServerPlayer playerOrFail(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return null;
        }
    }
}
