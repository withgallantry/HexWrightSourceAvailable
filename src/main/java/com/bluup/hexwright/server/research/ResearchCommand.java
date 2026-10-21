package com.bluup.hexwright.server.research;

import com.bluup.hexwright.server.command.CommandGate;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class ResearchCommand {

    private ResearchCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<CommandSourceStack> show = Commands.literal("show");
            for (TabletDamage damage : TabletDamage.values()) {
                show.then(Commands.literal(damage.getSerializedName())
                    .then(Commands.argument("seed", LongArgumentType.longArg())
                        .executes(ctx -> show(ctx, damage, LongArgumentType.getLong(ctx, "seed")))));
            }

            dispatcher.register(Commands.literal("hexwright")
                .requires(CommandGate::creative)
                .then(Commands.literal("tablet")
                    .then(Commands.literal("selftest")
                        .executes(ctx -> TabletSelfTest.run(ctx.getSource(), TabletSelfTest.DEFAULT_SAMPLE))
                        .then(Commands.argument("tablets", IntegerArgumentType.integer(1, 100000))
                            .executes(ctx -> TabletSelfTest.run(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "tablets")))))
                    .then(show)));
        });
    }

    private static int show(CommandContext<CommandSourceStack> ctx, TabletDamage damage, long seed) {
        TabletPuzzle puzzle = TabletPuzzle.generate(seed, damage);
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("[tablet] %s seed %d - %d%% lost, %d breaks, %d scraps"
            .formatted(damage.getSerializedName(), seed, puzzle.damagePercent(),
                puzzle.breaks().size(), puzzle.deck().size())), false);

        for (int row = 0; row < TabletPuzzle.ROWS; row++) {
            StringBuilder line = new StringBuilder();
            for (int col = 0; col < TabletPuzzle.COLS; col++) {
                if (puzzle.isMissing(row, col)) {
                    line.append("## ");
                } else {
                    line.append("%02d".formatted(puzzle.glyph(row, col)))
                        .append(puzzle.isPitted(row, col) ? "." : " ");
                }
            }
            String text = line.toString();
            source.sendSuccess(() -> Component.literal(text), false);
        }

        for (int i = 0; i < puzzle.deck().size(); i++) {
            TabletPuzzle.Scrap scrap = puzzle.deck().get(i);
            StringBuilder line = new StringBuilder("  scrap %d: ".formatted(i));
            for (int g = 0; g < scrap.glyphs().length; g++) {
                boolean end = g < scrap.context() || g >= scrap.context() + scrap.runLength();
                line.append(end ? "[" : " ").append("%02d".formatted(scrap.glyphs()[g]))
                    .append(end ? "]" : " ");
            }
            line.append(scrap.isDecoy() ? "  decoy" : "  fits break " + scrap.fits());
            String text = line.toString();
            source.sendSuccess(() -> Component.literal(text), false);
        }
        return SINGLE_SUCCESS;
    }
}
