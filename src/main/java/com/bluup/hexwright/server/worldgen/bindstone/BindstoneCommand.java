package com.bluup.hexwright.server.worldgen.bindstone;

import com.bluup.hexwright.server.command.CommandGate;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class BindstoneCommand {

    private BindstoneCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hexwright")
                .requires(CommandGate::creative)
                .then(Commands.literal("bindstone")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("status").executes(BindstoneCommand::status))
                    .then(Commands.literal("regenerate").executes(BindstoneCommand::regenerate)))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        StructureStart start = pillarHere(source);
        if (start == null) {
            return 0;
        }
        long key = start.getChunkPos().toLong();
        BindstoneRegenerations.Entry entry = BindstoneRegenerations.get(level.getServer()).find(level.dimension(), key);
        String state;
        if (BindstoneRegenerator.moving(level, key)) {
            state = "being moved right now";
        } else if (entry == null || !entry.claimed) {
            state = "unclaimed";
        } else if (System.currentTimeMillis() < entry.notBefore) {
            state = "claimed, but the last attempt gave up (" + entry.heldBack + "); next try in "
                + (entry.notBefore - System.currentTimeMillis()) / 60_000L + "m";
        } else {
            List<String> near = BindstoneRegenerator.playersNear(level, start.getBoundingBox());
            state = "claimed; it moves within seconds of nobody being within " + BindstoneRegenerator.PLAYER_CLEARANCE
                + " blocks of it - near it now: " + String.join(", ", near)
                + (entry.heldBack == null ? "" : ". The last attempt gave up: " + entry.heldBack);
        }
        String history = entry == null || entry.moves == 0 ? "never moved"
            : "moved " + entry.moves + " time(s) from its home in " + new ChunkPos(entry.home);
        source.sendSuccess(() -> Component.literal("Bindstone pillar " + start.getChunkPos() + ", " + history + ": "
            + state + ".").withStyle(ChatFormatting.AQUA), false);
        return SINGLE_SUCCESS;
    }

    private static int regenerate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        StructureStart start = pillarHere(source);
        BindstonePillarPiece piece = BindstoneRegenerator.pieceOf(start);
        if (piece == null) {
            return 0;
        }
        BindstoneRegenerations.get(level.getServer())
            .hasten(level.dimension(), start.getChunkPos().toLong(), piece.centre());
        source.sendSuccess(() -> Component.literal("Bindstone pillar " + start.getChunkPos()
            + " is due to move. It goes within seconds of everybody being " + BindstoneRegenerator.PLAYER_CLEARANCE
            + " blocks clear of it.").withStyle(ChatFormatting.AQUA), true);
        return SINGLE_SUCCESS;
    }

    private static @Nullable StructureStart pillarHere(CommandSourceStack source) {
        StructureStart start = BindstoneRegenerator.pillarNear(source.getLevel(), BlockPos.containing(source.getPosition()));
        if (start == null) {
            source.sendFailure(Component.literal("No bindstone pillar within a chunk of here."));
        }
        return start;
    }
}
