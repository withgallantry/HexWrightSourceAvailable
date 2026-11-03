package com.bluup.hexwright.server.worldgen.arena;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.worldgen.HexwrightWorldgen;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class GolemRegenerator {

    private static final int SCHEDULE_INTERVAL = 100;

    static final int PLAYER_CLEARANCE = 96;

    @Nullable
    private static GolemRegenerationJob job;

    private GolemRegenerator() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(GolemRegenerator::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> job = null);
    }

    private static void tick(MinecraftServer server) {
        if (job != null) {
            try {
                if (job.tick()) {
                    job = null;
                }
            } catch (RuntimeException failure) {
                Hexwright.LOGGER.error("Regenerating {} failed; it will be tried again later", job, failure);
                job.abandon("it threw " + failure);
                job = null;
            }
        }
        if (job == null && server.getTickCount() % SCHEDULE_INTERVAL == 0) {
            job = next(server);
        }
    }

    public static boolean noteKilled(ServerLevel level, BlockPos pos) {
        ArenaStructure arena = arenaIn(level);
        StructureStart start = arena == null ? StructureStart.INVALID_START
            : level.structureManager().getStructureWithPieceAt(pos, arena);
        if (!start.isValid()) {
            return false;
        }
        long delay = arena.regenerationDelayMillis(level.getRandom());
        GolemClears.get(level.getServer()).clear(level.dimension(), start.getChunkPos().toLong(),
            start.getBoundingBox(), System.currentTimeMillis() + delay);
        HexwrightDebug.log(HexwrightDebug.GOLEM, "[golem] arena {} beaten; a new golem is due in {}",
            start.getChunkPos(), GolemCommand.duration(delay));
        return true;
    }

    private static @Nullable GolemRegenerationJob next(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (GolemClears.Entry entry : GolemClears.get(server).entries()) {
            if (now < entry.due) {
                continue;
            }
            ServerLevel level = server.getLevel(entry.dimension);
            ArenaStructure arena = level == null ? null : arenaIn(level);
            if (arena == null || !arena.regenerates() || playerNear(level, entry.box)) {
                continue;
            }
            return new GolemRegenerationJob(level, arena, entry);
        }
        return null;
    }

    static boolean playerNear(ServerLevel level, BoundingBox box) {
        return !playersNear(level, box).isEmpty();
    }

    static List<String> playersNear(ServerLevel level, BoundingBox box) {
        BoundingBox around = box.inflatedBy(PLAYER_CLEARANCE);
        List<String> names = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && around.isInside(player.blockPosition())) {
                names.add(player.getGameProfile().getName());
            }
        }
        return names;
    }

    static boolean regenerating(ServerLevel level, long start) {
        return job != null && job.regenerates(level, start);
    }

    static @Nullable StructureStart arenaAt(ServerLevel level, BlockPos pos) {
        ArenaStructure arena = arenaIn(level);
        if (arena == null) {
            return null;
        }
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, arena);
        return start.isValid() ? start : null;
    }

    static @Nullable ArenaStructure arenaIn(Level level) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
            .get(HexwrightWorldgen.ANCIENT_ARENA_KEY);
        return structure instanceof ArenaStructure arena ? arena : null;
    }

    static @Nullable ArenaPiece pieceOf(@Nullable StructureStart start) {
        if (start == null || !start.isValid() || start.getPieces().isEmpty()) {
            return null;
        }
        return start.getPieces().get(0) instanceof ArenaPiece piece ? piece : null;
    }
}
