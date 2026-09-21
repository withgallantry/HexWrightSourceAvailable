package com.bluup.hexwright.server.journal;

import com.bluup.hexwright.server.command.CommandGate;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class JournalDebugCommand {

    private JournalDebugCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hexwright")
                .requires(CommandGate::creative)
                .then(Commands.literal("investigations")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("complete").executes(JournalDebugCommand::completeAll))
                    .then(Commands.literal("clear").executes(JournalDebugCommand::clear))
                    .then(Commands.literal("journal").executes(JournalDebugCommand::giveJournal))
                    .then(Commands.literal("artifacts")
                        .then(Commands.literal("reveal").executes(JournalDebugCommand::revealArtifacts))
                        .then(Commands.literal("clear").executes(JournalDebugCommand::clearArtifacts)))
                    .then(Commands.literal("toast")
                        .then(Commands.argument("id", StringArgumentType.string())
                            .suggests(JournalDebugCommand::suggestEntries)
                            .executes(JournalDebugCommand::toast))))));
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

    private static int revealArtifacts(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = playerOrFail(source);
        if (player == null) {
            return 0;
        }
        int total = Artifacts.all().size();
        if (total == 0) {
            source.sendFailure(Component.literal(
                "No artifacts are loaded - check the log for a parse error in artifacts.json."));
            return 0;
        }
        int found = InvestigationProgress.discoverAllArtifacts(player);
        source.sendSuccess(() -> Component.literal("Recorded " + found + " of " + total + " artifact(s).")
            .withStyle(ChatFormatting.AQUA), false);
        return SINGLE_SUCCESS;
    }

    private static int clearArtifacts(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = playerOrFail(source);
        if (player == null) {
            return 0;
        }
        boolean changed = InvestigationProgress.forgetArtifacts(player);
        source.sendSuccess(() -> Component.literal(changed
                ? "Forgot your recorded artifacts. Any you are carrying will be recorded again within a second."
                : "You had no recorded artifacts to forget.")
            .withStyle(ChatFormatting.AQUA), false);
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

    private static int toast(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = playerOrFail(source);
        if (player == null) {
            return 0;
        }

        String id = StringArgumentType.getString(ctx, "id");
        Investigation investigation = Investigations.get().byId().get(id);
        if (investigation != null) {
            HexwrightNetworking.sendJournalToast(player,
                investigation.completion().discoversStructure()
                    ? JournalToastKind.STRUCTURE
                    : JournalToastKind.INVESTIGATION,
                id);
        } else if (LoreEntries.get().byId().containsKey(id)) {
            HexwrightNetworking.sendJournalToast(player, JournalToastKind.LORE, id);
        } else if (Artifacts.get().byId().containsKey(id)) {
            HexwrightNetworking.sendJournalToast(player, JournalToastKind.ARTIFACT, id);
        } else {
            source.sendFailure(Component.literal(
                "No investigation, lore entry or artifact is called '" + id + "'."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Raised a journal toast for '" + id + "'.")
            .withStyle(ChatFormatting.AQUA), false);
        return SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestEntries(CommandContext<CommandSourceStack> ctx,
                                                                 SuggestionsBuilder builder) {
        List<String> ids = new ArrayList<>(Investigations.get().byId().keySet());
        ids.addAll(LoreEntries.get().byId().keySet());
        ids.addAll(Artifacts.get().byId().keySet());
        return SharedSuggestionProvider.suggest(ids, builder);
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
