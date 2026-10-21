package com.bluup.hexwright.server.worldgen.bindstone;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class BindstoneRegenerator {

    private static final int SCHEDULE_INTERVAL = 100;

    static final int PLAYER_CLEARANCE = 64;

    private static final ResourceKey<Structure> PILLAR_KEY =
        ResourceKey.create(Registries.STRUCTURE, Hexwright.id("bindstone_pillar"));

    @Nullable
    private static BindstoneRegenerationJob job;

    private BindstoneRegenerator() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(BindstoneRegenerator::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> job = null);
    }

    private static void tick(MinecraftServer server) {
        if (job != null) {
            try {
                if (job.tick()) {
                    job = null;
                }
            } catch (RuntimeException failure) {
                Hexwright.LOGGER.error("Moving {} failed; it will be tried again later", job, failure);
                job.abandon("it threw " + failure);
                job = null;
            }
        }
        if (job == null && server.getTickCount() % SCHEDULE_INTERVAL == 0) {
            job = next(server);
        }
    }

    public static void noteClaimed(ServerLevel level, BlockPos origin) {
        BindstonePillarStructure pillar = pillarIn(level);
        if (pillar == null) {
            return;
        }
        StructureStart start = level.structureManager().getStructureWithPieceAt(origin, pillar);
        BindstonePillarPiece piece = pieceOf(start);
        if (piece == null) {
            return;
        }
        if (BindstoneRegenerations.get(level.getServer())
            .noteClaimed(level.dimension(), start.getChunkPos().toLong(), piece.centre())) {
            HexwrightDebug.log(HexwrightDebug.BINDSTONE, "[bindstone] pillar at {} claimed; it moves once everybody has left",
                piece.centre().toShortString());
        }
    }

    private static @Nullable BindstoneRegenerationJob next(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (BindstoneRegenerations.Entry entry : BindstoneRegenerations.get(server).entries()) {
            if (!entry.claimed || now < entry.notBefore) {
                continue;
            }
            ServerLevel level = server.getLevel(entry.dimension);
            BindstonePillarStructure pillar = level == null ? null : pillarIn(level);
            if (pillar == null || playerNear(level, BindstonePillarPiece.boundsFor(entry.centre))) {
                continue;
            }
            return new BindstoneRegenerationJob(level, pillar, entry);
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

    static boolean moving(ServerLevel level, long start) {
        return job != null && job.moves(level, start);
    }

    static @Nullable StructureStart pillarNear(ServerLevel level, BlockPos pos) {
        BindstonePillarStructure pillar = pillarIn(level);
        if (pillar == null) {
            return null;
        }
        ChunkPos here = new ChunkPos(pos);
        StructureStart nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos chunk = new ChunkPos(here.x + dx, here.z + dz);
                for (StructureStart start : level.structureManager().startsForStructure(chunk, structure -> structure == pillar)) {
                    double distance = start.getBoundingBox().getCenter().distSqr(pos);
                    if (distance < nearestDistance) {
                        nearest = start;
                        nearestDistance = distance;
                    }
                }
            }
        }
        return nearest;
    }

    static @Nullable BindstonePillarStructure pillarIn(Level level) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(PILLAR_KEY);
        return structure instanceof BindstonePillarStructure pillar ? pillar : null;
    }

    static @Nullable BindstonePillarPiece pieceOf(@Nullable StructureStart start) {
        if (start == null || !start.isValid() || start.getPieces().isEmpty()) {
            return null;
        }
        return start.getPieces().get(0) instanceof BindstonePillarPiece piece ? piece : null;
    }
}
