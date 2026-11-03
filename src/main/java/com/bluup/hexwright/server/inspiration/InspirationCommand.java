package com.bluup.hexwright.server.inspiration;

import com.bluup.hexwright.server.command.CommandGate;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class InspirationCommand {

    private InspirationCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hexwright")
                .requires(CommandGate::creative)
                .then(Commands.literal("inspiration")
                    .executes(ctx -> show(ctx, ctx.getSource().getPlayerOrException()))
                    .then(Commands.argument("player", EntityArgument.player())
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> show(ctx, EntityArgument.getPlayer(ctx, "player"))))
                    .then(Commands.literal("give")
                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                            .executes(ctx -> move(ctx, ctx.getSource().getPlayerOrException(), false))
                            .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> move(ctx, EntityArgument.getPlayer(ctx, "player"), false)))))
                    .then(Commands.literal("set")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                            .executes(ctx -> move(ctx, ctx.getSource().getPlayerOrException(), true))
                            .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> move(ctx, EntityArgument.getPlayer(ctx, "player"), true)))))
                    .then(Commands.literal("drop")
                        .then(Commands.argument("orbs", IntegerArgumentType.integer(1, 64))
                            .executes(InspirationCommand::drop))))));
    }

    private static int show(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        int total = InspirationState.get(ctx.getSource().getServer()).total(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal(
            player.getGameProfile().getName() + " has " + total + " Inspiration."), false);
        return SINGLE_SUCCESS;
    }

    private static int move(CommandContext<CommandSourceStack> ctx, ServerPlayer player, boolean absolute) {
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        InspirationState state = InspirationState.get(ctx.getSource().getServer());
        if (absolute) {
            state.set(player.getUUID(), amount);
        } else {
            state.add(player.getUUID(), amount);
        }
        com.bluup.hexwright.inits.HexwrightNetworking.sendInspirationSync(player);
        return show(ctx, player);
    }

    private static int drop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Inspiration.drop(player.serverLevel(), player.position().add(0.0, 1.0, 0.0),
            IntegerArgumentType.getInteger(ctx, "orbs"));
        return SINGLE_SUCCESS;
    }
}
