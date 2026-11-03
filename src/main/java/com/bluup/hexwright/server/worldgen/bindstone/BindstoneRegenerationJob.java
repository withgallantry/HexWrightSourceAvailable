package com.bluup.hexwright.server.worldgen.bindstone;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.block.BindstoneCoreBlockEntity;
import com.bluup.hexwright.server.worldgen.dungeon.PlayerBuilds;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class BindstoneRegenerationJob {

    private static final TicketType<ChunkPos> TICKET =
        TicketType.create("hexwright_bindstone_regeneration", Comparator.comparingLong(ChunkPos::toLong));

    private static final int TICKET_DISTANCE = 0;

    private static final int LOAD_TIMEOUT_TICKS = 20 * 120;

    private static final int MIN_SHIFT_CHUNKS = 10;
    private static final int MAX_SHIFT_CHUNKS = 20;

    private static final int OLD_SITE_CLEARANCE_CHUNKS = 10;

    private static final int NEIGHBOUR_CLEARANCE_CHUNKS = 5;

    private static final int DRAWS = 64;
    private static final int SITES = 6;

    private static final long RETRY_MILLIS = 30L * 60L * 1000L;

    private static final long MAX_INHABITED_TICKS = 20L * 60L * 2L;

    private static final int STRUCTURE_CLEARANCE = 8;

    private enum Stage { LOADING_OLD, DRAWING, CHECKING_NEIGHBOURS, LOADING_SITE }

    private final ServerLevel level;
    private final BindstonePillarStructure pillar;
    private final BindstoneRegenerations.Entry entry;
    private final BoundingBox oldBox;
    private final LongSet oldChunks;
    private final RandomSource random = RandomSource.create();

    private final LongSet held = new LongOpenHashSet();

    @Nullable
    private StructureStart oldStart;

    @Nullable
    private StructureStart site;
    private final Deque<ChunkPos> neighbours = new ArrayDeque<>();
    private LongSet siteChunks = new LongOpenHashSet();

    private int draws;
    private int sitesLoaded;
    private int waited;
    private Stage stage = Stage.LOADING_OLD;
    private boolean finished;

    BindstoneRegenerationJob(ServerLevel level, BindstonePillarStructure pillar, BindstoneRegenerations.Entry entry) {
        this.level = level;
        this.pillar = pillar;
        this.entry = entry;
        this.oldBox = BindstonePillarPiece.boundsFor(entry.centre);
        this.oldChunks = withMargin(chunksOf(this.oldBox));
        hold(this.oldChunks);
        HexwrightDebug.log(HexwrightDebug.BINDSTONE, "[bindstone] everybody has left the pillar at {}; moving it",
            entry.centre.toShortString());
    }

    boolean moves(ServerLevel level, long start) {
        return level == this.level && start == this.entry.start;
    }

    boolean tick() {
        if (this.finished) {
            return true;
        }
        List<String> near = BindstoneRegenerator.playersNear(this.level, this.oldBox);
        if (!near.isEmpty()) {
            HexwrightDebug.log(HexwrightDebug.BINDSTONE, "[bindstone] {} came back to the pillar at {}; holding off",
                String.join(", ", near), this.entry.centre.toShortString());
            end();
            return true;
        }
        switch (this.stage) {
            case LOADING_OLD -> {
                if (arrived(this.oldChunks)) {
                    findOldStart();
                }
            }
            case DRAWING -> draw();
            case CHECKING_NEIGHBOURS -> checkNeighbour();
            case LOADING_SITE -> {
                if (arrived(this.oldChunks) && arrived(this.siteChunks)) {
                    settle();
                }
            }
        }
        return this.finished;
    }

    void abandon(String reason) {
        HexwrightDebug.log(HexwrightDebug.BINDSTONE, "[bindstone] moving the pillar at {} gave up: {}",
            this.entry.centre.toShortString(), reason);
        BindstoneRegenerations.get(this.level.getServer())
            .postpone(this.entry, System.currentTimeMillis() + RETRY_MILLIS, reason);
        end();
    }

    private void findOldStart() {
        LevelChunk chunk = chunkAt(this.entry.start);
        StructureStart start = chunk.getStartForStructure(this.pillar);
        if (BindstoneRegenerator.pieceOf(start) == null) {
            HexwrightDebug.log(HexwrightDebug.BINDSTONE,
                "[bindstone] {} holds no pillar any more; dropping it from the schedule", chunk.getPos());
            BindstoneRegenerations.get(this.level.getServer()).forget(this.entry);
            end();
            return;
        }
        this.oldStart = start;
        this.stage = Stage.DRAWING;
    }

    private void draw() {
        if (this.draws >= DRAWS || this.sitesLoaded >= SITES) {
            abandon("no cavern within reach would take it (" + this.draws + " surveyed, "
                + this.sitesLoaded + " looked at)");
            return;
        }
        this.draws++;
        ChunkPos home = new ChunkPos(this.entry.home);
        double angle = this.random.nextDouble() * Math.PI * 2.0D;
        int distance = MIN_SHIFT_CHUNKS + this.random.nextInt(MAX_SHIFT_CHUNKS - MIN_SHIFT_CHUNKS + 1);
        ChunkPos chunk = new ChunkPos(
            home.x + (int) Math.round(Math.cos(angle) * distance),
            home.z + (int) Math.round(Math.sin(angle) * distance));
        if (distance(chunk, new ChunkPos(this.entry.start)) < OLD_SITE_CLEARANCE_CHUNKS || nearMovedPillar(chunk)) {
            return;
        }
        StructureStart start = survey(chunk);
        if (!start.isValid() || !withinBorder(start.getBoundingBox())) {
            return;
        }
        this.site = start;
        this.neighbours.clear();
        this.neighbours.addAll(gridSitesNear(chunk));
        this.stage = Stage.CHECKING_NEIGHBOURS;
    }

    private StructureStart survey(ChunkPos chunk) {
        ChunkGenerator generator = this.level.getChunkSource().getGenerator();
        return this.pillar.generate(this.level.registryAccess(), generator, generator.getBiomeSource(),
            this.level.getChunkSource().randomState(), this.level.getStructureManager(), this.level.getSeed(),
            chunk, 0, this.level, this.pillar.biomes()::contains);
    }

    private void checkNeighbour() {
        ChunkPos neighbour = this.neighbours.poll();
        if (neighbour == null) {
            this.siteChunks = withMargin(chunksOf(this.site.getBoundingBox()));
            hold(this.siteChunks);
            this.sitesLoaded++;
            this.waited = 0;
            this.stage = Stage.LOADING_SITE;
            return;
        }
        if (this.level.structureManager().checkStructurePresence(neighbour, this.pillar, false)
            != StructureCheckResult.START_NOT_PRESENT) {
            HexwrightDebug.log(HexwrightDebug.BINDSTONE, "[bindstone] site at {} is too close to the pillar in {}",
                this.site.getChunkPos(), neighbour);
            this.site = null;
            this.stage = Stage.DRAWING;
        }
    }

    private void settle() {
        String refusal = refusal(this.site);
        if (refusal != null) {
            HexwrightDebug.log(HexwrightDebug.BINDSTONE, "[bindstone] site at {} refused - {}",
                this.site.getChunkPos(), refusal);
            LongSet siteOnly = new LongOpenHashSet(this.siteChunks);
            siteOnly.removeAll(this.oldChunks);
            release(siteOnly);
            this.site = null;
            this.stage = Stage.DRAWING;
            return;
        }
        move(this.oldStart, this.site);
    }

    private @Nullable String refusal(StructureStart site) {
        BoundingBox box = site.getBoundingBox();
        BoundingBox reach = box.inflatedBy(1);
        List<String> near = BindstoneRegenerator.playersNear(this.level, box);
        if (!near.isEmpty()) {
            return "a player is nearby (" + String.join(", ", near) + ")";
        }
        String fluid = fluidAround(BindstoneRegenerator.pieceOf(site).centre());
        if (fluid != null) {
            return fluid;
        }
        for (long packed : this.siteChunks) {
            LevelChunk chunk = chunkAt(packed);
            long inhabited = chunk.getInhabitedTime();
            if (inhabited > MAX_INHABITED_TICKS) {
                return "players have spent " + inhabited / 20L + " seconds near chunk " + chunk.getPos();
            }
            if (inhabited > 0L) {
                String found = PlayerBuilds.unnaturalIn(chunk, reach);
                if (found == null) {
                    found = PlayerBuilds.strangerIn(this.level, chunk, reach);
                }
                if (found != null) {
                    return "players have been near chunk " + chunk.getPos() + ", and there is a " + found;
                }
            }
            for (BlockEntity entity : chunk.getBlockEntities().values()) {
                if (reach.isInside(entity.getBlockPos()) && !PlayerBuilds.isWorldgen(entity)) {
                    return "something built stands at " + entity.getBlockPos().toShortString();
                }
            }
        }
        Registry<Structure> structures = this.level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (long packed : chunksOf(reach)) {
            ChunkPos chunk = new ChunkPos(packed);
            for (StructureStart other : this.level.structureManager().startsForStructure(chunk,
                structure -> !structures.wrapAsHolder(structure).is(StructureTags.MINESHAFT))) {
                if (other.getStructure() == this.pillar
                    || other.getBoundingBox().inflatedBy(STRUCTURE_CLEARANCE).intersects(reach)) {
                    return "it overlaps " + structures.getKey(other.getStructure()) + " in chunk " + chunk;
                }
            }
        }
        return null;
    }

    private @Nullable String fluidAround(BlockPos centre) {
        int reach = BindstonePillarStructure.WALL_RADIUS + 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int y = centre.getY() - BindstonePillarStructure.LAKE_DEPTH;
                     y <= centre.getY() + BindstonePillarStructure.REQUIRED_CLEARANCE + 1; y++) {
                    pos.set(centre.getX() + dx, y, centre.getZ() + dz);
                    if (!this.level.getFluidState(pos).isEmpty()) {
                        return "there is " + PlayerBuilds.describe(this.level.getBlockState(pos), pos.immutable())
                            + ", which would run into the moat";
                    }
                }
            }
        }
        return null;
    }

    private void move(StructureStart old, StructureStart laid) {
        BlockPos oldCentre = BindstoneRegenerator.pieceOf(old).centre();
        BlockPos newCentre = BindstoneRegenerator.pieceOf(laid).centre();

        String built = BindstoneTeardown.builtIn(this.level, oldCentre);
        if (built == null) {
            BindstoneTeardown.tearDown(this.level, oldCentre);
        } else if (this.level.getBlockEntity(oldCentre.above(2)) instanceof BindstoneCoreBlockEntity core) {
            core.standDown();
        }

        ChunkPos centreChunk = new ChunkPos(newCentre);
        laid.placeInChunk(this.level, this.level.structureManager(), this.level.getChunkSource().getGenerator(),
            this.random, column(centreChunk), centreChunk);
        moveStart(old, laid);

        BindstoneRegenerations.get(this.level.getServer()).finish(this.entry, laid.getChunkPos().toLong(), newCentre);
        HexwrightDebug.log(HexwrightDebug.BINDSTONE, "[bindstone] pillar at {} raised again at {}{}",
            oldCentre.toShortString(), newCentre.toShortString(),
            built == null ? "; old site taken down" : "; old site left standing, somebody has built there (" + built + ")");
        end();
    }

    private void moveStart(StructureStart old, StructureStart laid) {
        long oldKey = old.getChunkPos().toLong();
        long laidKey = laid.getChunkPos().toLong();
        for (long chunk : chunksOf(old.getBoundingBox())) {
            unreference(chunkAt(chunk), oldKey);
        }
        for (long chunk : chunksOf(laid.getBoundingBox())) {
            chunkAt(chunk).addReferenceForStructure(this.pillar, laidKey);
        }
        LevelChunk was = chunkAt(oldKey);
        Map<Structure, StructureStart> starts = new HashMap<>(was.getAllStarts());
        starts.remove(this.pillar);
        was.setAllStarts(starts);
        LevelChunk now = chunkAt(laidKey);
        now.setStartForStructure(this.pillar, laid);
        this.level.onStructureStartsAvailable(was);
        this.level.onStructureStartsAvailable(now);
    }

    private void unreference(LevelChunk chunk, long start) {
        LongSet references = chunk.getReferencesForStructure(this.pillar);
        if (!references.contains(start)) {
            return;
        }
        Map<Structure, LongSet> all = new HashMap<>(chunk.getAllReferences());
        LongSet remaining = new LongOpenHashSet(references);
        remaining.remove(start);
        if (remaining.isEmpty()) {
            all.remove(this.pillar);
        } else {
            all.put(this.pillar, remaining);
        }
        chunk.setAllReferences(all);
        chunk.setUnsaved(true);
    }

    private List<ChunkPos> gridSitesNear(ChunkPos chunk) {
        ChunkGeneratorStructureState state = this.level.getChunkSource().getGeneratorState();
        Holder<Structure> holder = this.level.registryAccess().registryOrThrow(Registries.STRUCTURE)
            .wrapAsHolder(this.pillar);
        List<ChunkPos> sites = new ArrayList<>();
        for (StructurePlacement placement : state.getPlacementsForStructure(holder)) {
            if (!(placement instanceof RandomSpreadStructurePlacement spread)) {
                continue;
            }
            int cells = NEIGHBOUR_CLEARANCE_CHUNKS / spread.spacing() + 1;
            for (int dx = -cells; dx <= cells; dx++) {
                for (int dz = -cells; dz <= cells; dz++) {
                    ChunkPos site = spread.getPotentialStructureChunk(state.getLevelSeed(),
                        chunk.x + dx * spread.spacing(), chunk.z + dz * spread.spacing());
                    if (site.toLong() != this.entry.start && distance(site, chunk) <= NEIGHBOUR_CLEARANCE_CHUNKS
                        && !sites.contains(site)) {
                        sites.add(site);
                    }
                }
            }
        }
        return sites;
    }

    private boolean nearMovedPillar(ChunkPos chunk) {
        for (BindstoneRegenerations.Entry other : BindstoneRegenerations.get(this.level.getServer()).entries()) {
            if (other != this.entry && other.start != other.home && other.dimension.equals(this.level.dimension())
                && distance(new ChunkPos(other.start), chunk) <= NEIGHBOUR_CLEARANCE_CHUNKS) {
                return true;
            }
        }
        return false;
    }

    private boolean withinBorder(BoundingBox box) {
        return this.level.getWorldBorder().isWithinBounds(box.minX(), box.minZ())
            && this.level.getWorldBorder().isWithinBounds(box.maxX(), box.maxZ());
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

    private void release(LongSet chunks) {
        for (long packed : chunks) {
            if (this.held.remove(packed)) {
                ChunkPos pos = new ChunkPos(packed);
                this.level.getChunkSource().removeRegionTicket(TICKET, pos, TICKET_DISTANCE, pos);
            }
        }
    }

    private void end() {
        release(new LongOpenHashSet(this.held));
        this.finished = true;
    }

    private boolean arrived(LongSet chunks) {
        for (long packed : chunks) {
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

    private static double distance(ChunkPos a, ChunkPos b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public String toString() {
        return "bindstone pillar at " + this.entry.centre.toShortString() + " (" + this.stage + ")";
    }
}
