package com.bluup.hexwright.server.worldgen.dungeon;

import com.bluup.hexwright.server.command.CommandGate;
import com.bluup.hexwright.server.dungeon.DungeonDimension;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class DungeonCommand {

    private DungeonCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hexwright")
                .requires(CommandGate::creative)
                .then(Commands.literal("dungeon")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("status").executes(DungeonCommand::status))
                    .then(Commands.literal("close").executes(DungeonCommand::close)))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        StructureStart start = dungeonHere(source);
        if (start == null) {
            return 0;
        }
        DungeonClosures.Entry entry = DungeonClosures.get(level.getServer())
            .find(level.dimension(), start.getChunkPos().toLong());
        String state;
        long now = System.currentTimeMillis();
        if (DungeonCloser.closing(level, start)) {
            state = "being closed right now";
        } else if (entry == null) {
            state = "not on the list yet; it will be within a tick or two";
        } else if (now < entry.notBefore) {
            String why = entry.heldBack == null ? "reason not recorded" : entry.heldBack;
            state = "held back (" + why + "); next try in " + duration(entry.notBefore - now);
        } else {
            List<String> near = DungeonCloser.playersNear(level, start.getBoundingBox());
            state = "closes within seconds of nobody being within " + DungeonCloser.PLAYER_CLEARANCE
                + " blocks of it - near it now: " + String.join(", ", near);
        }
        source.sendSuccess(() -> Component.literal("Dungeon " + start.getChunkPos() + ": " + state + ".")
            .withStyle(ChatFormatting.AQUA), false);
        return SINGLE_SUCCESS;
    }

    private static int close(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        StructureStart start = dungeonHere(source);
        if (start == null) {
            return 0;
        }
        DungeonClosures closures = DungeonClosures.get(level.getServer());
        long site = start.getChunkPos().toLong();
        closures.note(level.dimension(), site, start.getBoundingBox());
        closures.hasten(closures.find(level.dimension(), site));
        source.sendSuccess(() -> Component.literal("Dungeon " + start.getChunkPos()
            + " closes within seconds of everybody being clear of it.").withStyle(ChatFormatting.AQUA), true);
        return SINGLE_SUCCESS;
    }

    private static String duration(long millis) {
        return millis / 3_600_000L + "h " + millis / 60_000L % 60L + "m";
    }

    private static StructureStart dungeonHere(CommandSourceStack source) {
        if (DungeonDimension.isDungeonLevel(source.getLevel())) {
            source.sendFailure(Component.literal("Instanced dungeons are torn down when their party leaves - see "
                + "/hexwright dungeon instance."));
            return null;
        }
        StructureStart start = DungeonCloser.dungeonAt(source.getLevel(), BlockPos.containing(source.getPosition()));
        if (start == null) {
            source.sendFailure(Component.literal("Not standing in a deep dungeon."));
        }
        return start;
    }
}
