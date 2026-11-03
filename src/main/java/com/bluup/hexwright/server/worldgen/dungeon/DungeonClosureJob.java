package com.bluup.hexwright.server.worldgen.dungeon;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.block.ResonantAnchorRegistry;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class DungeonClosureJob {

    private static final TicketType<ChunkPos> TICKET =
        TicketType.create("hexwright_dungeon_closure", Comparator.comparingLong(ChunkPos::toLong));

    private static final int TICKET_DISTANCE = 0;

    private static final int LOAD_TIMEOUT_TICKS = 20 * 120;

    private static final long WORK_BUDGET_NANOS = 15_000_000L;

    static final long RETRY_MILLIS = 10L * 60L * 1000L;

    private static final long PLAYER_RETRY_MILLIS = 60L * 1000L;

    private enum Stage { LOADING, WORKING }

    private final ServerLevel level;
    private final DungeonClosures.Entry entry;

    private StructureStart start;

    private List<DungeonPiece> pieces;

    private final List<BoundingBox> areas = new ArrayList<>();

    private LongSet inner = new LongOpenHashSet();
    private final LongSet held = new LongOpenHashSet();
    private int waited;

    private final Map<DungeonPiece, Long2ObjectMap<Block>> templates = new IdentityHashMap<>();

    private DungeonBackfill.Pockets pockets = DungeonBackfill.Pockets.none();

    private final Deque<Runnable> work = new ArrayDeque<>();

    private Stage stage = Stage.LOADING;
    private boolean finished;

    DungeonClosureJob(ServerLevel level, DungeonStructure dungeon, DungeonClosures.Entry entry) {
        this.level = level;
        this.entry = entry;

        StructureStart start = startIn(dungeon, new ChunkPos(entry.start));
        List<DungeonPiece> pieces = start == null ? new ArrayList<>() : dungeonPieces(start);
        List<DungeonPiece> halfLaid = List.of();
        if (pieces.isEmpty() && entry.target != null) {
            start = startIn(dungeon, new ChunkPos(entry.target));
            pieces = start == null ? new ArrayList<>() : dungeonPieces(start);
        } else if (!pieces.isEmpty() && entry.target != null) {
            halfLaid = halfLaid(dungeon, entry);
            pieces.addAll(halfLaid);
        }
        if (start == null || pieces.isEmpty()) {
            HexwrightDebug.log(HexwrightDebug.DUNGEON,
                "[dungeon] {} has no dungeon to close any more; dropping it from the list", new ChunkPos(entry.start));
            DungeonClosures.get(level.getServer()).forget(entry);
            this.finished = true;
            return;
        }
        this.start = start;
        this.pieces = pieces;
        this.areas.add(start.getBoundingBox());
        if (!halfLaid.isEmpty()) {
            this.areas.add(extentOf(halfLaid));
        }
        DungeonClosures.get(level.getServer()).note(entry.dimension, entry.start, start.getBoundingBox());

        String near = playersNear();
        if (near != null) {
            abandon(near, PLAYER_RETRY_MILLIS);
            return;
        }
        this.inner = chunksIn(start.getBoundingBox());
        this.inner.addAll(DungeonStructure.chunksOf(pieces));
        hold(withMargin(this.inner));
        HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] {} closing: {} rooms{}; holding {} chunks",
            start.getChunkPos(), pieces.size(), halfLaid.isEmpty() ? "" : " (" + halfLaid.size() + " half-laid by a move)",
            this.held.size());
    }

    boolean closes(ServerLevel level, long start) {
        return level == this.level && start == this.entry.start;
    }

    boolean tick() {
        if (this.finished) {
            return true;
        }
        String near = playersNear();
        if (near != null) {
            abandon(near, PLAYER_RETRY_MILLIS);
            return true;
        }
        switch (this.stage) {
            case LOADING -> {
                if (arrived()) {
                    queueWork();
                }
            }
            case WORKING -> work();
        }
        return this.finished;
    }

    void abandon(String reason, long retryMillis) {
        HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] closing {} stopped: {}", new ChunkPos(this.entry.start), reason);
        DungeonClosures.get(this.level.getServer())
            .postpone(this.entry, System.currentTimeMillis() + retryMillis, reason);
        release();
        this.finished = true;
    }

    private void queueWork() {
        for (long packed : this.inner) {
            this.work.add(() -> this.level.getChunkSource()
                .getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed)).postProcessGeneration());
        }
        for (DungeonPiece piece : this.pieces) {
            this.work.add(() -> this.templates.put(piece, piece.templateBlocks()));
        }
        this.work.add(() -> this.pockets = DungeonBackfill.pocketsFor(this.level, this.held, this.templates));
        for (long chunk : DungeonStructure.chunksOf(this.pieces)) {
            this.work.add(() -> DungeonBackfill.fill(this.level, this.templates, new ChunkPos(chunk), this.pockets));
        }
        this.work.add(this::closeShafts);
        this.work.add(this::finish);
        this.stage = Stage.WORKING;
    }

    private void work() {
        long deadline = System.nanoTime() + WORK_BUDGET_NANOS;
        do {
            this.work.poll().run();
        } while (!this.work.isEmpty() && !this.finished && System.nanoTime() < deadline);
    }

    private void closeShafts() {
        for (DungeonPiece piece : this.pieces) {
            BlockPos column = piece.caveTapColumn();
            if (column != null) {
                DungeonBackfill.closeShaft(this.level, column, piece.getBoundingBox().maxY(), this.pockets);
            }
        }
    }

    private void finish() {
        DungeonInstanceLayout.unregister(this.level, this.start.getChunkPos().toLong(), this.start.getBoundingBox());
        unregisterAnchors();
        DungeonClosures.get(this.level.getServer()).forget(this.entry);
        release();
        this.finished = true;
        HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] {} closed: {} rooms filled, {} kept in pockets",
            this.start.getChunkPos(), this.pieces.size(), this.pockets.kept().size());
    }

    private void unregisterAnchors() {
        ResonantAnchorRegistry registry = ResonantAnchorRegistry.get(this.level.getServer());
        for (DungeonPiece piece : this.pieces) {
            String rune = piece.anchorRune();
            BlockPos spot = rune == null ? null : piece.anchorSpot();
            if (spot != null) {
                registry.unregister(DungeonGroups.registryKey(DungeonGroups.wordFor(this.level.getSeed(), rune)),
                    this.level.dimension(), spot);
            }
        }
    }

    private @Nullable String playersNear() {
        for (BoundingBox area : this.areas) {
            List<String> names = DungeonCloser.playersNear(this.level, area);
            if (!names.isEmpty()) {
                return "players are near it (" + String.join(", ", names) + ")";
            }
        }
        return null;
    }

    private List<DungeonPiece> halfLaid(DungeonStructure dungeon, DungeonClosures.Entry entry) {
        long seed = layoutSeed(this.level.getSeed(), entry.home, entry.generation);
        return dungeon.layOut(this.level.getStructureManager(), entry.target, RandomSource.create(seed),
            DungeonStructure.extrasFor(seed), DungeonGroups.runeForSite(this.level.getSeed(), new ChunkPos(entry.home)));
    }

    private @Nullable StructureStart startIn(DungeonStructure dungeon, ChunkPos chunk) {
        ChunkAccess access = this.level.getChunk(chunk.x, chunk.z, ChunkStatus.STRUCTURE_STARTS);
        StructureStart start = this.level.structureManager()
            .getStartForStructure(SectionPos.bottomOf(access), dungeon, access);
        return start != null && start.isValid() ? start : null;
    }

    private void hold(LongSet chunks) {
        for (long packed : chunks) {
            if (this.held.add(packed)) {
                ChunkPos pos = new ChunkPos(packed);
                this.level.getChunkSource().addRegionTicket(TICKET, pos, TICKET_DISTANCE, pos);
            }
        }
        this.waited = 0;
    }

    private void release() {
        for (long packed : this.held) {
            ChunkPos pos = new ChunkPos(packed);
            this.level.getChunkSource().removeRegionTicket(TICKET, pos, TICKET_DISTANCE, pos);
        }
        this.held.clear();
    }

    private boolean arrived() {
        for (long packed : this.held) {
            if (this.level.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed)) == null
                || !this.level.areEntitiesLoaded(packed)) {
                if (++this.waited > LOAD_TIMEOUT_TICKS) {
                    abandon("chunks around " + new ChunkPos(packed) + " never finished loading", RETRY_MILLIS);
                }
                return false;
            }
        }
        return true;
    }

    private static List<DungeonPiece> dungeonPieces(StructureStart start) {
        List<DungeonPiece> pieces = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof DungeonPiece room) {
                pieces.add(room);
            }
        }
        return pieces;
    }

    private static BoundingBox extentOf(List<? extends StructurePiece> pieces) {
        return BoundingBox.encapsulatingBoxes(pieces.stream().map(StructurePiece::getBoundingBox).toList())
            .orElseThrow();
    }

    private static LongSet chunksIn(BoundingBox box) {
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
            int x = ChunkPos.getX(packed);
            int z = ChunkPos.getZ(packed);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    out.add(ChunkPos.asLong(x + dx, z + dz));
                }
            }
        }
        return out;
    }

    private static long layoutSeed(long worldSeed, long site, int generation) {
        long hash = worldSeed ^ site * 0x9E3779B97F4A7C15L ^ (generation + 1L) * 0xD1B54A32D192ED03L;
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        return hash ^ (hash >>> 31);
    }

    @Override
    public String toString() {
        return "dungeon " + new ChunkPos(this.entry.start) + " (" + this.stage + ")";
    }
}
