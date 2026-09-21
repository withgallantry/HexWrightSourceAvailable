package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.server.weapon.SlashStyle;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.bluup.hexwright.client.command.ClientCommandGate;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

final class SlashBloomCommands {

    private SlashBloomCommands() {
    }

    static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("hexwrightslash")
                .requires(ClientCommandGate::creative)
                .then(ClientCommandManager.literal("status").executes(SlashBloomCommands::status))
                .then(ClientCommandManager.literal("off").executes(ctx -> setBoth(ctx, 0.0f)))
                .then(ClientCommandManager.literal("on").executes(ctx -> setBoth(ctx, 1.0f)))
                .then(multiplier("crescent", SlashBloom::setCrescentMultiplier))
                .then(multiplier("wave", SlashBloom::setWaveMultiplier))
                .then(multiplier("both", value -> {
                    SlashBloom.setCrescentMultiplier(value);
                    SlashBloom.setWaveMultiplier(value);
                }))
                .executes(SlashBloomCommands::status)));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> multiplier(
        String name, FloatSetter setter) {
        return ClientCommandManager.literal(name)
            .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg())
                .executes(ctx -> {
                    setter.set(FloatArgumentType.getFloat(ctx, "value"));
                    return status(ctx);
                }));
    }

    private static int setBoth(CommandContext<FabricClientCommandSource> ctx, float value) {
        SlashBloom.setCrescentMultiplier(value);
        SlashBloom.setWaveMultiplier(value);
        return status(ctx);
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        feedback(source, "Slash bloom: crescent x" + SlashBloom.crescentMultiplier()
            + ", wave x" + SlashBloom.waveMultiplier());
        for (SlashStyle style : SlashStyle.values()) {
            feedback(source, "  " + style.name().toLowerCase()
                + " -> crescent " + round(SlashBloom.crescentStrength(style))
                + ", wave " + round(SlashBloom.waveStrength(style)));
        }
        return SINGLE_SUCCESS;
    }

    private static String round(float value) {
        return String.format("%.2f", value);
    }

    private static void feedback(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal(message).withStyle(ChatFormatting.GRAY));
    }

    @FunctionalInterface
    private interface FloatSetter {
        void set(float value);
    }
}
