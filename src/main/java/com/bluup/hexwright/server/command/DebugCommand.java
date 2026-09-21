package com.bluup.hexwright.server.command;

import com.bluup.hexwright.HexwrightDebug;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class DebugCommand {

    private static final String ALL = "all";

    private DebugCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hexwright")
                .requires(CommandGate::creative)
                .then(Commands.literal("debug")
                    .requires(source -> source.hasPermission(2))
                    .executes(DebugCommand::report)
                    .then(Commands.argument("area", StringArgumentType.word())
                        .suggests(DebugCommand::suggestAreas)
                        .then(Commands.literal("on").executes(ctx -> set(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> set(ctx, false)))))));
    }

    private static int report(CommandContext<CommandSourceStack> ctx) {
        Set<String> live = HexwrightDebug.active();
        Component message = live.isEmpty()
            ? Component.literal("Hexwright logging is quiet. Areas: " + String.join(", ", sorted()))
                .withStyle(ChatFormatting.GRAY)
            : Component.literal("Hexwright is tracing: " + String.join(", ", live))
                .withStyle(ChatFormatting.AQUA);
        ctx.getSource().sendSuccess(() -> message, false);
        return SINGLE_SUCCESS;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, boolean wanted) {
        CommandSourceStack source = ctx.getSource();
        String area = StringArgumentType.getString(ctx, "area").toLowerCase();

        if (!area.equals(ALL) && !HexwrightDebug.AREAS.contains(area)) {
            source.sendFailure(Component.literal(
                "No such debug area '" + area + "'. Try: " + ALL + ", " + String.join(", ", sorted())));
            return 0;
        }

        List<String> touched = area.equals(ALL) ? sorted() : List.of(area);
        for (String one : touched) {
            if (wanted) {
                HexwrightDebug.enable(one);
            } else {
                HexwrightDebug.disable(one);
            }
        }

        String what = area.equals(ALL) ? "every area" : "'" + area + "'";
        source.sendSuccess(
            () -> Component.literal("Hexwright logging for " + what + " is " + (wanted ? "on" : "off")
                    + ". It goes to the server log, not to chat.")
                .withStyle(wanted ? ChatFormatting.AQUA : ChatFormatting.GRAY),
            false
        );
        return SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestAreas(
        CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        List<String> options = new ArrayList<>();
        options.add(ALL);
        options.addAll(sorted());
        return SharedSuggestionProvider.suggest(options, builder);
    }

    private static List<String> sorted() {
        List<String> areas = new ArrayList<>(HexwrightDebug.AREAS);
        areas.sort(String::compareTo);
        return areas;
    }
}
