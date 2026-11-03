package com.bluup.hexwright.server.soulforge;

import com.bluup.hexwright.server.command.CommandGate;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class SoulforgeCommand {

    private SoulforgeCommand() {
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hexwright")
                .requires(CommandGate::creative)
                .then(Commands.literal("soulforge")
                    .then(Commands.literal("recall").executes(SoulforgeCommand::recall))
                    .then(Commands.literal("dismiss").executes(SoulforgeCommand::dismiss))
                    .then(Commands.literal("info")
                        .executes(ctx -> info(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                            .requires(source -> source.hasPermission(2))
                            .executes(ctx -> info(ctx, EntityArgument.getPlayer(ctx, "player")))))
                    .then(Commands.literal("list")
                        .requires(source -> source.hasPermission(2))
                        .executes(SoulforgeCommand::list))
                    .then(Commands.literal("sever")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(SoulforgeCommand::sever)))
                    .then(Commands.literal("blacklist")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("add")
                            .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .executes(SoulforgeCommand::blacklistAdd)))
                        .then(Commands.literal("remove")
                            .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                    Soulforge.blacklisted(ctx.getSource().getServer()), builder))
                                .executes(SoulforgeCommand::blacklistRemove)))
                        .then(Commands.literal("list").executes(SoulforgeCommand::blacklistList))))));
    }

    private static int recall(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!Soulforge.recall(ctx.getSource().getPlayerOrException())) {
            ctx.getSource().sendFailure(Component.literal("Your soul is forged to no weapon."));
            return 0;
        }
        return SINGLE_SUCCESS;
    }

    private static int dismiss(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!Soulforge.dismiss(ctx.getSource().getPlayerOrException())) {
            ctx.getSource().sendFailure(Component.literal("Your soul is forged to no weapon."));
            return 0;
        }
        return SINGLE_SUCCESS;
    }

    private static int info(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        Component described = Soulforge.describe(ctx.getSource().getServer(), player.getUUID());
        if (described == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(player.getScoreboardName() + " has no bond.")
                .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> described.copy().withStyle(ChatFormatting.AQUA), false);
        return SINGLE_SUCCESS;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        for (Component line : Soulforge.describeAll(ctx.getSource().getServer())) {
            ctx.getSource().sendSuccess(() -> line.copy().withStyle(ChatFormatting.AQUA), false);
        }
        return SINGLE_SUCCESS;
    }

    private static int sever(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer owner = EntityArgument.getPlayer(ctx, "player");
        Item weapon = Soulforge.unbind(ctx.getSource().getServer(), owner.getUUID(), true);
        if (weapon == null) {
            ctx.getSource().sendFailure(Component.literal(owner.getScoreboardName() + " has no bond to sever."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Severed " + owner.getScoreboardName() + "'s bond; their ")
            .append(weapon.getDescription()).append(" crumbles in " + Soulforge.DOOM_MINUTES + " minutes.")
            .withStyle(ChatFormatting.AQUA), true);
        return SINGLE_SUCCESS;
    }

    private static int blacklistAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int added = 0;
        for (GameProfile profile : GameProfileArgument.getGameProfiles(ctx, "targets")) {
            if (Soulforge.blacklist(ctx.getSource().getServer(), profile)) {
                added++;
                ctx.getSource().sendSuccess(() -> Component.literal(profile.getName()
                    + " can no longer own a Soulforged weapon.").withStyle(ChatFormatting.AQUA), true);
            }
        }
        if (added == 0) {
            ctx.getSource().sendFailure(Component.literal("Already blacklisted."));
        }
        return added;
    }

    private static int blacklistRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int removed = 0;
        for (GameProfile profile : GameProfileArgument.getGameProfiles(ctx, "targets")) {
            if (Soulforge.unblacklist(ctx.getSource().getServer(), profile.getId())) {
                removed++;
                ctx.getSource().sendSuccess(() -> Component.literal(profile.getName()
                    + " may own a Soulforged weapon again.").withStyle(ChatFormatting.AQUA), true);
            }
        }
        if (removed == 0) {
            ctx.getSource().sendFailure(Component.literal("Not blacklisted."));
        }
        return removed;
    }

    private static int blacklistList(CommandContext<CommandSourceStack> ctx) {
        List<String> names = Soulforge.blacklisted(ctx.getSource().getServer());
        Component line = names.isEmpty() ? Component.literal("Nobody is blacklisted.")
            : Component.literal("Blacklisted (" + names.size() + "): " + String.join(", ", names));
        ctx.getSource().sendSuccess(() -> line.copy().withStyle(ChatFormatting.AQUA), false);
        return names.size();
    }
}
