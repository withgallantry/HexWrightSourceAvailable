package com.bluup.hexwright.client.armour;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.bluup.hexwright.client.command.ClientCommandGate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class MageAttireCommands {

    private MageAttireCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("hexwrightattire")
                .requires(ClientCommandGate::creative)
                .then(piece("mantle", MageAttireRenderer.MANTLE))
                .then(piece("hat", MageAttireRenderer.HAT))
                .then(piece("cape", MageAttireRenderer.CAPE))
                .then(piece("passagehat", MageAttireRenderer.PASSAGE_HAT))
                .then(ClientCommandManager.literal("reset").executes(ctx -> {
                    MageAttireRenderer.MANTLE.reset();
                    MageAttireRenderer.HAT.reset();
                    MageAttireRenderer.CAPE.reset();
                    MageAttireRenderer.PASSAGE_HAT.reset();
                    return status(ctx);
                }))
                .executes(MageAttireCommands::status)));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> piece(
        String name, MageAttireRenderer.Placement where) {
        return ClientCommandManager.literal(name)
            .then(ClientCommandManager.literal("reset").executes(ctx -> {
                where.reset();
                return status(ctx);
            }))
            .then(ClientCommandManager.literal("offset")
                .then(ClientCommandManager.argument("x", FloatArgumentType.floatArg())
                    .then(ClientCommandManager.argument("y", FloatArgumentType.floatArg())
                        .then(ClientCommandManager.argument("z", FloatArgumentType.floatArg())
                            .executes(ctx -> {
                                where.setOffset(FloatArgumentType.getFloat(ctx, "x"),
                                    FloatArgumentType.getFloat(ctx, "y"),
                                    FloatArgumentType.getFloat(ctx, "z"));
                                return status(ctx);
                            })))))
            .then(single("x", value -> where.setOffset(value, where.y(), where.z())))
            .then(single("y", value -> where.setOffset(where.x(), value, where.z())))
            .then(single("z", value -> where.setOffset(where.x(), where.y(), value)))
            .then(single("scale", where::setScale))
            .then(single("yaw", where::setYaw))
            .executes(MageAttireCommands::status);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> single(
        String name, FloatSetter setter) {
        return ClientCommandManager.literal(name)
            .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg())
                .executes(ctx -> {
                    setter.set(FloatArgumentType.getFloat(ctx, "value"));
                    return status(ctx);
                }));
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        line(ctx, "Mantle", MageAttireRenderer.MANTLE);
        line(ctx, "Hat", MageAttireRenderer.HAT);
        line(ctx, "Cape", MageAttireRenderer.CAPE);
        line(ctx, "Passage Hat", MageAttireRenderer.PASSAGE_HAT);
        return Command.SINGLE_SUCCESS;
    }

    private static void line(CommandContext<FabricClientCommandSource> ctx, String name,
                             MageAttireRenderer.Placement where) {
        ctx.getSource().sendFeedback(Component.literal(String.format(
                "%s: offset %.3f %.3f %.3f, scale %.3f, yaw %.1f",
                name, where.x(), where.y(), where.z(), where.scale(), where.yaw()))
            .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @FunctionalInterface
    private interface FloatSetter {
        void set(float value);
    }
}
