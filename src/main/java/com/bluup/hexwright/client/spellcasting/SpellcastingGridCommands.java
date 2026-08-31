package com.bluup.hexwright.client.spellcasting;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class SpellcastingGridCommands {
    private SpellcastingGridCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("hexwrightgrid")
                .then(ClientCommandManager.literal("on").executes(ctx -> set(ctx.getSource(), true)))
                .then(ClientCommandManager.literal("off").executes(ctx -> set(ctx.getSource(), false)))
                .then(ClientCommandManager.literal("toggle")
                    .executes(ctx -> set(ctx.getSource(), !PatternDrawBatch.isEnabled())))
                .then(ClientCommandManager.literal("status").executes(ctx -> status(ctx.getSource())))
                .executes(ctx -> status(ctx.getSource()))));
    }

    private static int set(FabricClientCommandSource source, boolean enabled) {
        PatternDrawBatch.setEnabled(enabled);
        feedback(source, enabled
            ? "Grid batching on - one draw call for the whole grid."
            : "Grid batching off - Hex Casting draws each stroke and dot itself.");
        return SINGLE_SUCCESS;
    }

    private static int status(FabricClientCommandSource source) {
        feedback(source, "Grid batching " + (PatternDrawBatch.isEnabled() ? "on" : "off") + ".");
        return SINGLE_SUCCESS;
    }

    private static void feedback(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal(message).withStyle(ChatFormatting.AQUA));
    }
}
