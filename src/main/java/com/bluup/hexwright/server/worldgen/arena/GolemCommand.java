package com.bluup.hexwright.server.worldgen.arena;

import com.bluup.hexwright.server.boss.ancient.AncientGolemEntity;
import com.bluup.hexwright.server.command.CommandGate;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class GolemCommand {

    private GolemCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hexwright")
                .requires(CommandGate::creative)
                .then(Commands.literal("golem")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("status").executes(GolemCommand::status))
                    .then(Commands.literal("regenerate").executes(GolemCommand::regenerate)))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        StructureStart start = arenaHere(source);
        if (start == null) {
            return 0;
        }
        long key = start.getChunkPos().toLong();
        GolemClears.Entry entry = GolemClears.get(level.getServer()).find(level.dimension(), key);
        ArenaStructure arena = GolemRegenerator.arenaIn(level);
        long now = System.currentTimeMillis();
        String state;
        if (GolemRegenerator.regenerating(level, key)) {
            state = "being laid again right now";
        } else if (entry == null) {
            state = "its golem is standing";
        } else if (arena == null || !arena.regenerates()) {
            state = "beaten " + duration(now - entry.clearedAt) + " ago, and regeneration is turned off";
        } else if (now < entry.due) {
            state = "beaten " + duration(now - entry.clearedAt) + " ago; a new golem is due in "
                + duration(entry.due - now)
                + (entry.heldBack == null ? "" : ". The last attempt gave up: " + entry.heldBack);
        } else {
            List<String> near = GolemRegenerator.playersNear(level, start.getBoundingBox());
            state = "due; a new golem stands within seconds of nobody being within "
                + GolemRegenerator.PLAYER_CLEARANCE + " blocks of it - near it now: " + String.join(", ", near);
        }
        source.sendSuccess(() -> Component.literal("Ancient arena " + start.getChunkPos() + ": " + state + ".")
            .withStyle(ChatFormatting.AQUA), false);
        return SINGLE_SUCCESS;
    }

    private static int regenerate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        StructureStart start = arenaHere(source);
        if (start == null) {
            return 0;
        }
        if (!level.getEntitiesOfClass(AncientGolemEntity.class, AABB.of(start.getBoundingBox()),
            Entity::isAlive).isEmpty()) {
            source.sendFailure(Component.literal("This arena's golem is still standing."));
            return 0;
        }
        GolemClears.get(level.getServer())
            .hasten(level.dimension(), start.getChunkPos().toLong(), start.getBoundingBox());
        source.sendSuccess(() -> Component.literal("Ancient arena " + start.getChunkPos()
            + " is due a new golem. It stands within seconds of everybody being "
            + GolemRegenerator.PLAYER_CLEARANCE + " blocks clear of it.").withStyle(ChatFormatting.AQUA), true);
        return SINGLE_SUCCESS;
    }

    private static @Nullable StructureStart arenaHere(CommandSourceStack source) {
        StructureStart start = GolemRegenerator.arenaAt(source.getLevel(), BlockPos.containing(source.getPosition()));
        if (start == null) {
            source.sendFailure(Component.literal("Not standing in an Ancient Golem arena."));
        }
        return start;
    }

    static String duration(long millis) {
        long minutes = Math.max(0L, millis) / 60_000L;
        return minutes / 60L + "h " + minutes % 60L + "m";
    }
}
