package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.server.worldgen.dungeon.DungeonInstanceLayout;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;

final class DungeonInstanceJob {

    private static final TicketType<ChunkPos> TICKET =
        TicketType.create("hexwright_dungeon_instance", Comparator.comparingLong(ChunkPos::toLong));

    private static final int TICKET_DISTANCE = 0;

    private static final int LOAD_TIMEOUT_TICKS = 20 * 120;

    private static final long WORK_BUDGET_NANOS = 10_000_000L;

    static final int LOOTR_REACH = 16;

    private static final int LINGER_TICKS = 40;

    private static final int CLEAR_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private final ServerLevel level;
    private final DungeonInstance instance;
    private final LongSet held = new LongOpenHashSet();
    private final Deque<Runnable> work = new ArrayDeque<>();
    private boolean loaded;
    private int waited;
    private int linger;

    private DungeonInstanceJob(ServerLevel level, DungeonInstance instance, BoundingBox holding) {
        this.level = level;
        this.instance = instance;
        for (int x = holding.minX() >> 4; x <= holding.maxX() >> 4; x++) {
            for (int z = holding.minZ() >> 4; z <= holding.maxZ() >> 4; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                this.held.add(chunk.toLong());
                level.getChunkSource().addRegionTicket(TICKET, chunk, TICKET_DISTANCE, chunk);
            }
        }
    }

    static DungeonInstanceJob build(ServerLevel level, DungeonInstance instance, BoundingBox bounds,
                                    DungeonInstanceLayout.Plan plan, Runnable built) {
        DungeonInstanceJob job = new DungeonInstanceJob(level, instance, bounds.inflatedBy(LOOTR_REACH));
        job.clearing(bounds);
        DungeonSafeRoom room = instance.safeRoom();
        job.work.add(() -> room.build(level));
        for (long packed : DungeonInstanceLayout.chunks(plan)) {
            ChunkPos chunk = new ChunkPos(packed);
            job.work.add(() -> DungeonInstanceLayout.place(level, plan, chunk, instance.seed()));
        }
        job.work.add(() -> DungeonInstanceLayout.ladder(level, instance.shaftFoot(), room.box().minY() + 1,
            instance.seed()));
        job.work.add(() -> DungeonInstanceLayout.register(level, plan.start()));
        job.work.add(built);
        job.linger = LINGER_TICKS;
        return job;
    }

    static DungeonInstanceJob buildArena(ServerLevel level, DungeonInstance instance, BoundingBox bounds,
                                         ArenaInstanceLayout.Plan plan, Runnable built) {
        DungeonInstanceJob job = new DungeonInstanceJob(level, instance, bounds);
        job.clearing(bounds);
        job.work.add(() -> plan.room().build(level));
        for (long packed : ArenaInstanceLayout.chunks(plan)) {
            ChunkPos chunk = new ChunkPos(packed);
            job.work.add(() -> ArenaInstanceLayout.place(level, plan, chunk));
        }
        job.work.add(() -> ArenaInstanceLayout.bridge(level, plan, instance.seed()));
        job.work.add(() -> ArenaInstanceLayout.garrison(level, plan, instance.kind()));
        job.work.add(built);
        return job;
    }

    static DungeonInstanceJob clear(ServerLevel level, DungeonInstance instance, BoundingBox bounds, Runnable cleared) {
        DungeonInstanceJob job = new DungeonInstanceJob(level, instance, bounds);
        if (instance.kind() == DungeonInstance.Kind.DUNGEON) {
            job.work.add(() -> DungeonInstanceLayout.unregister(level, instance.startChunk(), instance.extent()));
        }
        job.clearing(bounds);
        job.work.add(cleared);
        return job;
    }

    DungeonInstance instance() {
        return this.instance;
    }

    boolean tick() {
        if (!this.loaded) {
            this.loaded = arrived();
            return false;
        }
        if (!this.work.isEmpty()) {
            long deadline = System.nanoTime() + WORK_BUDGET_NANOS;
            do {
                this.work.poll().run();
            } while (!this.work.isEmpty() && System.nanoTime() < deadline);
            return false;
        }
        if (this.linger-- > 0) {
            return false;
        }
        release();
        return true;
    }

    void release() {
        for (long packed : this.held) {
            ChunkPos chunk = new ChunkPos(packed);
            this.level.getChunkSource().removeRegionTicket(TICKET, chunk, TICKET_DISTANCE, chunk);
        }
        this.held.clear();
    }

    private void clearing(BoundingBox bounds) {
        int lowest = this.level.getSectionIndex(bounds.minY());
        int highest = this.level.getSectionIndex(bounds.maxY());
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
                for (int section = lowest; section <= highest; section++) {
                    int chunkX = x;
                    int chunkZ = z;
                    int index = section;
                    this.work.add(() -> clearSection(chunkX, chunkZ, index, bounds.minY(), bounds.maxY()));
                }
            }
        }
        this.work.add(() -> sweep(bounds));
    }

    private void clearSection(int chunkX, int chunkZ, int index, int minY, int maxY) {
        LevelChunk chunk = this.level.getChunk(chunkX, chunkZ);
        LevelChunkSection section = chunk.getSection(index);
        if (section.hasOnlyAir()) {
            return;
        }
        int baseY = SectionPos.sectionToBlockCoord(this.level.getSectionYFromSectionIndex(index));
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy < SectionPos.SECTION_SIZE; dy++) {
            int y = baseY + dy;
            if (y < minY || y > maxY) {
                continue;
            }
            for (int dx = 0; dx < SectionPos.SECTION_SIZE; dx++) {
                for (int dz = 0; dz < SectionPos.SECTION_SIZE; dz++) {
                    if (!section.getBlockState(dx, dy, dz).isAir()) {
                        this.level.setBlock(pos.set(chunk.getPos().getBlockX(dx), y, chunk.getPos().getBlockZ(dz)),
                            air, CLEAR_FLAGS);
                    }
                }
            }
        }
    }

    private void sweep(BoundingBox bounds) {
        for (Entity entity : this.level.getEntities((Entity) null, AABB.of(bounds), entity -> !(entity instanceof Player))) {
            entity.discard();
        }
    }

    private boolean arrived() {
        for (long packed : this.held) {
            if (this.level.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed)) == null
                || !this.level.areEntitiesLoaded(packed)) {
                if (++this.waited > LOAD_TIMEOUT_TICKS) {
                    throw new IllegalStateException("chunks around " + new ChunkPos(packed) + " never finished loading");
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "dungeon instance " + this.instance.id() + " (" + this.instance.state() + ")";
    }
}
