package com.bluup.hexwright.server.worldgen.arena;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.boss.ancient.AncientBallistaEntity;
import com.bluup.hexwright.server.boss.ancient.AncientGolemEntity;
import com.bluup.hexwright.server.boss.ancient.AncientMercenaryEntity;
import com.bluup.hexwright.server.boss.ancient.GolemHeadEntity;
import com.bluup.hexwright.server.worldgen.dungeon.PlayerBuilds;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

final class GolemRegenerationJob {

    private static final TicketType<ChunkPos> TICKET =
        TicketType.create("hexwright_golem_regeneration", Comparator.comparingLong(ChunkPos::toLong));

    private static final int TICKET_DISTANCE = 0;

    private static final int LOAD_TIMEOUT_TICKS = 20 * 120;

    private static final long RETRY_MILLIS = 60L * 60L * 1000L;

    private static final int BULK_LIMIT = 64;

    private static final double CREW_REACH = 4.0D;

    private final ServerLevel level;
    private final ArenaStructure arena;
    private final GolemClears.Entry entry;
    private final LongSet chunks;
    private final RandomSource random = RandomSource.create();

    private final LongSet held = new LongOpenHashSet();

    private int waited;
    private boolean finished;

    GolemRegenerationJob(ServerLevel level, ArenaStructure arena, GolemClears.Entry entry) {
        this.level = level;
        this.arena = arena;
        this.entry = entry;
        this.chunks = withMargin(chunksOf(entry.box));
        hold(this.chunks);
        HexwrightDebug.log(HexwrightDebug.GOLEM, "[golem] arena {} is due and everybody has left; laying it again",
            new ChunkPos(entry.start));
    }

    boolean regenerates(ServerLevel level, long start) {
        return level == this.level && start == this.entry.start;
    }

    boolean tick() {
        if (this.finished) {
            return true;
        }
        List<String> near = GolemRegenerator.playersNear(this.level, this.entry.box);
        if (!near.isEmpty()) {
            HexwrightDebug.log(HexwrightDebug.GOLEM, "[golem] {} came back to arena {}; holding off",
                String.join(", ", near), new ChunkPos(this.entry.start));
            end();
            return true;
        }
        if (arrived()) {
            regenerate();
        }
        return this.finished;
    }

    void abandon(String reason) {
        HexwrightDebug.log(HexwrightDebug.GOLEM, "[golem] arena {} held back: {}",
            new ChunkPos(this.entry.start), reason);
        GolemClears.get(this.level.getServer())
            .postpone(this.entry, System.currentTimeMillis() + RETRY_MILLIS, reason);
        end();
    }

    private void regenerate() {
        GolemClears clears = GolemClears.get(this.level.getServer());
        StructureStart start = chunkAt(this.entry.start).getStartForStructure(this.arena);
        ArenaPiece piece = GolemRegenerator.pieceOf(start);
        if (piece == null) {
            HexwrightDebug.log(HexwrightDebug.GOLEM, "[golem] {} holds no arena any more; dropping it",
                new ChunkPos(this.entry.start));
            clears.forget(this.entry);
            end();
            return;
        }
        BoundingBox box = start.getBoundingBox();
        clears.located(this.entry, box);
        AABB area = AABB.of(box);
        if (!this.level.getEntitiesOfClass(AncientGolemEntity.class, area, Entity::isAlive).isEmpty()) {
            HexwrightDebug.log(HexwrightDebug.GOLEM, "[golem] arena {} already has a golem standing in it",
                start.getChunkPos());
            clears.forget(this.entry);
            end();
            return;
        }
        String built = builtIn(piece, box);
        if (built != null) {
            abandon("somebody has built in it (" + built + ")");
            return;
        }

        for (Entity old : this.level.getEntities((Entity) null, area.inflate(CREW_REACH),
            entity -> entity instanceof AncientBallistaEntity || entity instanceof AncientMercenaryEntity)) {
            old.discard();
        }
        for (long packed : chunksOf(box)) {
            ChunkPos chunk = new ChunkPos(packed);
            start.placeInChunk(this.level, this.level.structureManager(),
                this.level.getChunkSource().getGenerator(), this.random, column(chunk), chunk);
        }
        clears.forget(this.entry);
        HexwrightDebug.log(HexwrightDebug.GOLEM, "[golem] arena {} laid again with a new golem", start.getChunkPos());
        end();
    }

    private @Nullable String builtIn(ArenaPiece piece, BoundingBox box) {
        int bulk = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Long2ObjectMap.Entry<Block> cell : piece.templateBlocks().long2ObjectEntrySet()) {
            pos.set(cell.getLongKey());
            BlockState state = this.level.getBlockState(pos);
            Block placed = cell.getValue();
            if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.is(placed)
                || state.is(BlockTags.REPLACEABLE)) {
                continue;
            }
            if (!state.is(PlayerBuilds.DEBRIS) && !state.is(PlayerBuilds.NATURAL)) {
                return PlayerBuilds.describe(state, pos.immutable());
            }
            if ((state.is(PlayerBuilds.DEBRIS) || placed.defaultBlockState().isAir())
                && state.isCollisionShapeFullBlock(this.level, pos)) {
                bulk++;
            }
        }
        for (long packed : chunksOf(box)) {
            for (BlockEntity entity : chunkAt(packed).getBlockEntities().values()) {
                if (box.isInside(entity.getBlockPos()) && !PlayerBuilds.isWorldgen(entity)) {
                    return PlayerBuilds.describe(entity.getBlockState(), entity.getBlockPos());
                }
            }
        }
        List<Entity> kept = this.level.getEntities((Entity) null, AABB.of(box),
            entity -> !arenaOwn(entity) && PlayerBuilds.placedByPlayer(entity));
        if (!kept.isEmpty()) {
            return PlayerBuilds.describe(kept.get(0));
        }
        return bulk > BULK_LIMIT ? bulk + " blocks set down in the pit" : null;
    }

    private static boolean arenaOwn(Entity entity) {
        return entity instanceof AncientBallistaEntity || entity instanceof AncientMercenaryEntity
            || entity instanceof GolemHeadEntity;
    }

    private LevelChunk chunkAt(long packed) {
        return this.level.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
    }

    private void hold(LongSet chunks) {
        for (long packed : chunks) {
            if (this.held.add(packed)) {
                ChunkPos pos = new ChunkPos(packed);
                this.level.getChunkSource().addRegionTicket(TICKET, pos, TICKET_DISTANCE, pos);
            }
        }
    }

    private void end() {
        for (long packed : this.held) {
            ChunkPos pos = new ChunkPos(packed);
            this.level.getChunkSource().removeRegionTicket(TICKET, pos, TICKET_DISTANCE, pos);
        }
        this.held.clear();
        this.finished = true;
    }

    private boolean arrived() {
        for (long packed : this.chunks) {
            if (this.level.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed)) == null
                || !this.level.areEntitiesLoaded(packed)) {
                if (++this.waited > LOAD_TIMEOUT_TICKS) {
                    abandon("chunks around " + new ChunkPos(packed) + " never finished loading");
                }
                return false;
            }
        }
        return true;
    }

    private BoundingBox column(ChunkPos chunk) {
        return new BoundingBox(
            chunk.getMinBlockX(), this.level.getMinBuildHeight(), chunk.getMinBlockZ(),
            chunk.getMaxBlockX(), this.level.getMaxBuildHeight(), chunk.getMaxBlockZ());
    }

    private static LongSet chunksOf(BoundingBox box) {
        LongSet chunks = new LongOpenHashSet();
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                chunks.add(ChunkPos.asLong(x, z));
            }
        }
        return chunks;
    }

    private static LongSet withMargin(LongSet chunks) {
        LongSet out = new LongOpenHashSet();
        for (long packed : chunks) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    out.add(ChunkPos.asLong(ChunkPos.getX(packed) + dx, ChunkPos.getZ(packed) + dz));
                }
            }
        }
        return out;
    }

    @Override
    public String toString() {
        return "ancient arena " + new ChunkPos(this.entry.start);
    }
}
