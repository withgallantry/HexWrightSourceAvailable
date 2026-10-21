package com.bluup.hexwright.server.worldgen.dungeon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.dungeon.DungeonDimension;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class DungeonCloser {

    private static final int SCHEDULE_INTERVAL = 100;

    static final int PLAYER_CLEARANCE = 32;

    @Nullable
    private static DungeonClosureJob job;

    private DungeonCloser() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DungeonCloser::tick);
        ServerChunkEvents.CHUNK_LOAD.register(DungeonCloser::noteChunk);
        ServerLifecycleEvents.SERVER_STARTED.register(DungeonCloser::noteCleared);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> job = null);
    }

    private static void tick(MinecraftServer server) {
        if (job != null) {
            try {
                if (job.tick()) {
                    job = null;
                }
            } catch (RuntimeException failure) {
                Hexwright.LOGGER.error("Closing {} failed; it will be tried again later", job, failure);
                job.abandon("it threw " + failure, DungeonClosureJob.RETRY_MILLIS);
                job = null;
            }
        }
        if (job == null && server.getTickCount() % SCHEDULE_INTERVAL == 0) {
            job = next(server);
        }
    }

    private static void noteChunk(ServerLevel level, LevelChunk chunk) {
        if (DungeonDimension.isDungeonLevel(level)) {
            return;
        }
        DungeonStructure dungeon = DungeonStructure.in(level);
        if (dungeon == null) {
            return;
        }
        StructureStart start = chunk.getStartForStructure(dungeon);
        LongSet references = chunk.getReferencesForStructure(dungeon);
        if ((start == null || !start.isValid()) && references.isEmpty()) {
            return;
        }
        DungeonClosures closures = DungeonClosures.get(level.getServer());
        if (start != null && start.isValid()
            && closures.note(level.dimension(), chunk.getPos().toLong(), start.getBoundingBox())) {
            HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] found the dungeon at {}; closing it", chunk.getPos());
        }
        for (long referenced : references) {
            closures.note(level.dimension(), referenced, null);
        }
    }

    private static void noteCleared(MinecraftServer server) {
        DungeonClosures closures = DungeonClosures.get(server);
        DungeonClears.get(server).forEachCleared((dimension, start) -> {
            if (!dimension.equals(DungeonDimension.KEY)) {
                closures.note(dimension, start, null);
            }
        });
    }

    private static @Nullable DungeonClosureJob next(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (DungeonClosures.Entry entry : DungeonClosures.get(server).entries()) {
            if (now < entry.notBefore) {
                continue;
            }
            ServerLevel level = server.getLevel(entry.dimension);
            DungeonStructure dungeon = level == null ? null : DungeonStructure.in(level);
            if (dungeon == null || DungeonDimension.isDungeonLevel(level)) {
                continue;
            }
            if (entry.extent != null && playerNear(level, entry.extent)) {
                continue;
            }
            return new DungeonClosureJob(level, dungeon, entry);
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

    static @Nullable StructureStart dungeonAt(ServerLevel level, BlockPos pos) {
        DungeonStructure dungeon = DungeonStructure.in(level);
        if (dungeon == null || pos.getY() < dungeon.lowestFloorY() || pos.getY() > dungeon.highestCeilingY()) {
            return null;
        }
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, dungeon);
        return start.isValid() ? start : null;
    }

    static boolean closing(ServerLevel level, StructureStart start) {
        return job != null && job.closes(level, start.getChunkPos().toLong());
    }
}
