package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.server.command.CommandGate;
import com.bluup.hexwright.server.dungeon.DungeonDifficulty.Setting;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

final class DungeonDifficultyCommand {

    private static final DecimalFormat NUMBER = new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private DungeonDifficultyCommand() {
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<CommandSourceStack> difficulty = Commands.literal("difficulty")
                .executes(ctx -> list(ctx, null))
                .then(Commands.literal("reset").executes(DungeonDifficultyCommand::reset));
            Map<String, LiteralArgumentBuilder<CommandSourceStack>> groups = new LinkedHashMap<>();
            for (Setting setting : Setting.values()) {
                groups.computeIfAbsent(setting.group(),
                        group -> Commands.literal(group).executes(ctx -> list(ctx, group)))
                    .then(Commands.literal(setting.key())
                        .executes(ctx -> show(ctx, setting))
                        .then(Commands.literal("reset").executes(ctx -> set(ctx, setting, setting.defaultValue())))
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(setting.min(), setting.max()))
                            .executes(ctx -> set(ctx, setting, DoubleArgumentType.getDouble(ctx, "value")))));
            }
            groups.values().forEach(difficulty::then);
            dispatcher.register(Commands.literal("hexwright")
                .requires(CommandGate::creative)
                .then(Commands.literal("dungeon")
                    .requires(source -> source.hasPermission(2))
                    .then(difficulty)));
        });
    }

    private static int list(CommandContext<CommandSourceStack> ctx, @Nullable String group) {
        CommandSourceStack source = ctx.getSource();
        for (Setting setting : Setting.values()) {
            if (group == null || group.equals(setting.group())) {
                source.sendSuccess(() -> line(source.getServer(), setting), false);
            }
        }
        return SINGLE_SUCCESS;
    }

    private static int show(CommandContext<CommandSourceStack> ctx, Setting setting) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> line(source.getServer(), setting), false);
        return SINGLE_SUCCESS;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, Setting setting, double value) {
        CommandSourceStack source = ctx.getSource();
        DungeonDifficulty.set(source.getServer(), setting, value);
        String when = setting.group().equals(DungeonDifficulty.MOBS)
            ? "Dungeon mobs have it now."
            : "Bosses take it up from their next second with somebody fighting them.";
        String now = value == setting.defaultValue()
            ? " is back to its default, " + NUMBER.format(value) + ". "
            : " is now " + NUMBER.format(value) + " (default " + NUMBER.format(setting.defaultValue()) + "). ";
        source.sendSuccess(() -> Component.literal(setting.group() + " " + setting.key() + now + when)
            .withStyle(ChatFormatting.AQUA), true);
        return SINGLE_SUCCESS;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        DungeonDifficulty.reset(source.getServer());
        source.sendSuccess(() -> Component.literal("Every dungeon difficulty setting is back to its default.")
            .withStyle(ChatFormatting.AQUA), true);
        return SINGLE_SUCCESS;
    }

    private static Component line(MinecraftServer server, Setting setting) {
        double value = setting.value(server);
        boolean changed = value != setting.defaultValue();
        String set = NUMBER.format(value) + (changed ? " (default " + NUMBER.format(setting.defaultValue()) + ")" : "");
        return Component.literal(setting.group() + " " + setting.key() + " = " + set + " - " + setting.description())
            .withStyle(changed ? ChatFormatting.AQUA : ChatFormatting.GRAY);
    }
}
