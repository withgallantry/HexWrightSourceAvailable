package com.bluup.hexwright.client.powerorb;

import com.bluup.hexwright.client.command.ClientCommandGate;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class PowerOrbBloomCommands {

    private PowerOrbBloomCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("hexwrightorb")
                .requires(ClientCommandGate::creative)
                .then(ClientCommandManager.literal("status").executes(PowerOrbBloomCommands::status))
                .then(ClientCommandManager.literal("reset").executes(PowerOrbBloomCommands::reset))
                .executes(PowerOrbBloomCommands::status);
            for (PowerOrbBloom.Spirit spirit : PowerOrbBloom.Spirit.values()) {
                root.then(ClientCommandManager.literal(name(spirit))
                    .then(ClientCommandManager.argument("strength", FloatArgumentType.floatArg())
                        .executes(ctx -> {
                            spirit.setStrength(FloatArgumentType.getFloat(ctx, "strength"));
                            return status(ctx);
                        })));
            }
            dispatcher.register(root);
        });
    }

    private static int reset(CommandContext<FabricClientCommandSource> ctx) {
        for (PowerOrbBloom.Spirit spirit : PowerOrbBloom.Spirit.values()) {
            spirit.setStrength(spirit.authored);
        }
        return status(ctx);
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        feedback(source, "Power orb bloom:");
        for (PowerOrbBloom.Spirit spirit : PowerOrbBloom.Spirit.values()) {
            feedback(source, "  " + name(spirit) + " -> " + String.format(Locale.ROOT, "%.2f", spirit.strength())
                + " (authored " + spirit.authored + ")");
        }
        return SINGLE_SUCCESS;
    }

    private static String name(PowerOrbBloom.Spirit spirit) {
        return spirit.name().toLowerCase(Locale.ROOT);
    }

    private static void feedback(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal(message).withStyle(ChatFormatting.GRAY));
    }
}
