package com.bluup.hexwright.server.worldgen.quota;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.mixin.StructurePlacementAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class BorderQuotaJob {

    private static final TicketType<ChunkPos> TICKET =
        TicketType.create("hexwright_border_quota", Comparator.comparingLong(ChunkPos::toLong));

    private static final int TICKET_DISTANCE = 0;

    private static final int LOAD_TIMEOUT_TICKS = 20 * 120;

    private static final int PEOPLE_CLEARANCE_CHUNKS = 48;

    private static final int GRID_CLEARANCE_CHUNKS = 16;

    private static final int DRAWS = 4096;
    private static final int DRAWS_PER_TICK = 64;

    private static final int SURVEYS = 128;

    private enum Stage { VERIFYING, COUNTING, DRAWING, CHECKING_GROUND, GENERATING }

    record Quota(ResourceKey<Structure> key, Structure structure, StructurePlacement placement, int minimum) {
    }

    private record Found(ChunkPos start, BoundingBox box, String how) {
    }

    private final ServerLevel level;
    private final PlayableArea area;
    private final ChunkGeneratorStructureState state;
    private final PlantedSites planted;
    private final Deque<Quota> quotas;
    private final List<String> report = new ArrayList<>();
    private final RandomSource random = RandomSource.create();

    private final LongSet held = new LongOpenHashSet();

    private Quota quota;
    private final List<Found> found = new ArrayList<>();
    private final Deque<ChunkPos> cells = new ArrayDeque<>();
    private final Deque<PlantedSites.Site> unverified = new ArrayDeque<>();
    private int draws;
    private int surveys;
    private Stage stage = Stage.COUNTING;

    private ChunkPos cell;
    @Nullable
    private CompletableFuture<Optional<CompoundTag>> read;
    @Nullable
    private CompletableFuture<StructureStart> survey;

    @Nullable
    private StructureStart candidate;
    private final List<CompletableFuture<Optional<CompoundTag>>> ground = new ArrayList<>();
    @Nullable
    private PlantedSites.Site generating;
    private Stage afterGenerating = Stage.DRAWING;
    private int waited;

    private boolean finished;

    BorderQuotaJob(ServerLevel level, PlayableArea area, List<Quota> quotas) {
        this.level = level;
        this.area = area;
        this.state = level.getChunkSource().getGeneratorState();
        this.planted = PlantedSites.get(level);
        this.quotas = new ArrayDeque<>(quotas);
        HexwrightDebug.log(HexwrightDebug.QUOTA, "[quota] counting in {} inside {}", level.dimension().location(),
            area.description());
        nextQuota();
    }

    boolean tick() {
        if (this.finished) {
            return true;
        }
        switch (this.stage) {
            case VERIFYING -> verifyNext();
            case COUNTING -> count();
            case DRAWING -> draw();
            case CHECKING_GROUND -> checkGround();
            case GENERATING -> generate();
        }
        return this.finished;
    }

    void abandon(String reason) {
        this.report.add((this.quota == null ? "" : this.quota.key().location() + ": ") + "gave up counting - " + reason);
        finish();
    }

    private void nextQuota() {
        this.quota = this.quotas.poll();
        if (this.quota == null) {
            finish();
            return;
        }
        this.found.clear();
        this.cells.clear();
        this.unverified.clear();
        this.draws = 0;
        this.surveys = 0;
        for (PlantedSites.Site site : this.planted.sites()) {
            if (!site.structure().equals(this.quota.key())) {
                continue;
            }
            if (!site.standing()) {
                this.unverified.add(site);
            } else if (this.area.contains(site.box())) {
                this.found.add(new Found(site.start(), site.box(), "planted"));
            }
        }
        if (this.quota.placement() instanceof RandomSpreadStructurePlacement grid) {
            this.cells.addAll(gridChunksInside(grid));
        }
        this.stage = Stage.VERIFYING;
    }

    private boolean enough() {
        return this.found.size() >= this.quota.minimum();
    }

    private void verifyNext() {
        PlantedSites.Site site = this.unverified.poll();
        if (site == null) {
            this.stage = Stage.COUNTING;
            return;
        }
        startGenerating(site, Stage.VERIFYING);
    }

    private void count() {
        if (enough()) {
            met();
            return;
        }
        if (this.read != null) {
            if (this.read.isDone()) {
                readCell(this.read.join());
                this.read = null;
            }
            return;
        }
        if (this.survey != null) {
            if (this.survey.isDone()) {
                counted(this.survey.join(), "to come");
                this.survey = null;
            }
            return;
        }
        ChunkPos next = this.cells.poll();
        if (next == null) {
            this.stage = Stage.DRAWING;
            return;
        }
        this.cell = next;
        LevelChunk loaded = this.level.getChunkSource().getChunkNow(next.x, next.z);
        if (loaded != null) {
            counted(loaded.getStartForStructure(this.quota.structure()), "standing");
            return;
        }
        this.read = this.level.getChunkSource().chunkMap.read(next);
    }

    private void readCell(Optional<CompoundTag> saved) {
        if (saved.isPresent() && ChunkStatus.byName(saved.get().getString("Status")).isOrAfter(ChunkStatus.STRUCTURE_STARTS)) {
            counted(savedStart(saved.get()), "standing");
            return;
        }
        if (this.quota.placement().isStructureChunk(this.state, this.cell.x, this.cell.z)) {
            this.survey = surveyAsync(this.cell);
        } else {
            HexwrightDebug.log(HexwrightDebug.QUOTA, "[quota] {} grid chunk {}: kept clear by its exclusion zone",
                this.quota.key().location(), this.cell);
        }
    }

    private @Nullable StructureStart savedStart(CompoundTag chunk) {
        CompoundTag starts = chunk.getCompound("structures").getCompound("starts");
        String id = this.quota.key().location().toString();
        if (!starts.contains(id, Tag.TAG_COMPOUND)) {
            return null;
        }
        return StructureStart.loadStaticStart(StructurePieceSerializationContext.fromLevel(this.level),
            starts.getCompound(id), this.state.getLevelSeed());
    }

    private void counted(@Nullable StructureStart start, String how) {
        boolean counts = start != null && start.isValid() && this.area.contains(start.getBoundingBox());
        if (counts) {
            this.found.add(new Found(start.getChunkPos(), start.getBoundingBox(), how));
        }
        HexwrightDebug.log(HexwrightDebug.QUOTA, "[quota] {} grid chunk {}: {}", this.quota.key().location(), this.cell,
            counts ? how : start != null && start.isValid() ? "one " + how + ", but over the border" : "none " + how);
    }

    private void draw() {
        if (enough()) {
            met();
            return;
        }
        if (this.survey != null) {
            if (this.survey.isDone()) {
                StructureStart start = this.survey.join();
                this.survey = null;
                considerSite(start);
            }
            return;
        }
        if (this.surveys >= SURVEYS || this.draws >= DRAWS) {
            fallShort(this.surveys == 0
                ? "nowhere inside it is far enough from players, the spawn, the structure grid and the sites already there"
                : "no ground inside it that nobody has generated would take one (" + this.surveys + " surveyed)");
            return;
        }
        for (int tried = 0; tried < DRAWS_PER_TICK && this.draws < DRAWS; tried++) {
            this.draws++;
            ChunkPos chunk = new ChunkPos(
                SectionPos.blockToSectionCoord(this.area.minX() + this.random.nextInt(this.area.maxX() - this.area.minX() + 1)),
                SectionPos.blockToSectionCoord(this.area.minZ() + this.random.nextInt(this.area.maxZ() - this.area.minZ() + 1)));
            if (plausible(chunk)) {
                this.surveys++;
                this.survey = surveyAsync(chunk);
                return;
            }
        }
    }

    private boolean plausible(ChunkPos chunk) {
        if (!this.area.contains(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ())
            || nearPeople(chunk) || nearGrid(chunk) || nearSite(chunk)) {
            return false;
        }
        return ((StructurePlacementAccessor) this.quota.placement()).hexwright$exclusionZone()
            .map(zone -> !this.state.hasStructureChunkInRange(zone.otherSet(), chunk.x, chunk.z, zone.chunkCount()))
            .orElse(true);
    }

    private void considerSite(StructureStart start) {
        if (!start.isValid() || !this.area.contains(start.getBoundingBox()) || nearSite(start.getChunkPos())) {
            return;
        }
        LongSet chunks = withMargin(chunksOf(start.getBoundingBox()));
        chunks.add(start.getChunkPos().toLong());
        for (long packed : chunks) {
            if (this.level.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed)) != null) {
                return;
            }
        }
        this.candidate = start;
        for (long packed : chunks) {
            this.ground.add(this.level.getChunkSource().chunkMap.read(new ChunkPos(packed)));
        }
        this.stage = Stage.CHECKING_GROUND;
    }

    private void checkGround() {
        for (CompletableFuture<Optional<CompoundTag>> chunk : this.ground) {
            if (!chunk.isDone()) {
                return;
            }
        }
        boolean untouched = this.ground.stream().allMatch(chunk -> chunk.join().isEmpty());
        StructureStart site = this.candidate;
        this.ground.clear();
        this.candidate = null;
        this.stage = Stage.DRAWING;
        if (!untouched) {
            HexwrightDebug.log(HexwrightDebug.QUOTA, "[quota] {} at {} refused: some of its ground was generated already",
                this.quota.key().location(), site.getChunkPos());
            return;
        }
        PlantedSites.Site planned = this.planted.plan(this.level, this.quota.key(), site.getChunkPos(), site.getBoundingBox());
        HexwrightDebug.log(HexwrightDebug.QUOTA, "[quota] planting {} at {}", this.quota.key().location(),
            site.getBoundingBox().getCenter().toShortString());
        startGenerating(planned, Stage.DRAWING);
    }

    private void startGenerating(PlantedSites.Site site, Stage after) {
        LongSet chunks = chunksOf(site.box());
        chunks.add(site.start().toLong());
        hold(chunks);
        this.generating = site;
        this.afterGenerating = after;
        this.waited = 0;
        this.stage = Stage.GENERATING;
    }

    private void generate() {
        for (long packed : this.held) {
            if (this.level.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed)) == null) {
                if (++this.waited > LOAD_TIMEOUT_TICKS) {
                    HexwrightDebug.log(HexwrightDebug.QUOTA, "[quota] {} at {} never finished generating",
                        this.quota.key().location(), this.generating.start());
                    if (this.area.contains(this.generating.box())) {
                        this.found.add(new Found(this.generating.start(), this.generating.box(), "planted, not yet generated,"));
                    }
                    doneGenerating();
                }
                return;
            }
        }
        PlantedSites.Site site = this.generating;
        StructureStart start = this.level.getChunkSource().getChunkNow(site.start().x, site.start().z)
            .getStartForStructure(this.quota.structure());
        if (start != null && start.isValid()) {
            this.planted.stand(this.level, site, start.getBoundingBox());
            if (this.area.contains(start.getBoundingBox())) {
                this.found.add(new Found(site.start(), start.getBoundingBox(), "planted"));
            }
            HexwrightDebug.log(HexwrightDebug.QUOTA, "[quota] {} standing at {}", this.quota.key().location(),
                start.getBoundingBox().getCenter().toShortString());
        } else {
            this.planted.forget(this.level, site);
            Hexwright.LOGGER.warn("A {} planned at {} in {} did not generate; another site will be found",
                this.quota.key().location(), site.start(), this.level.dimension().location());
        }
        doneGenerating();
    }

    private void doneGenerating() {
        release();
        this.generating = null;
        this.stage = this.afterGenerating;
    }

    private void met() {
        this.report.add(this.quota.key().location() + ": at least " + this.found.size() + " within the border, of "
            + this.quota.minimum() + " needed");
        describeFound();
        nextQuota();
    }

    private void fallShort(String reason) {
        this.report.add(this.quota.key().location() + ": only " + this.found.size() + " of " + this.quota.minimum()
            + " within the border - " + reason);
        describeFound();
        Hexwright.LOGGER.warn("Only {} of the {} {} promised within {} in {} could be found or planted: {}",
            this.found.size(), this.quota.minimum(), this.quota.key().location(), this.area.description(),
            this.level.dimension().location(), reason);
        nextQuota();
    }

    private void describeFound() {
        for (Found site : this.found) {
            BlockPos centre = site.box().getCenter();
            this.report.add("  " + site.how() + " at " + centre.getX() + ", " + centre.getZ());
        }
    }

    private void finish() {
        release();
        this.finished = true;
        BorderQuotas.reported(this.level, this.area, this.report);
    }

    private CompletableFuture<StructureStart> surveyAsync(ChunkPos chunk) {
        Structure structure = this.quota.structure();
        RegistryAccess registryAccess = this.level.registryAccess();
        ChunkGenerator generator = this.level.getChunkSource().getGenerator();
        StructureTemplateManager templates = this.level.getStructureManager();
        ServerLevel heights = this.level;
        return CompletableFuture.supplyAsync(() -> structure.generate(registryAccess, generator, generator.getBiomeSource(),
            this.state.randomState(), templates, this.state.getLevelSeed(), chunk, 0, heights, structure.biomes()::contains),
            Util.backgroundExecutor());
    }

    private List<ChunkPos> gridChunksInside(RandomSpreadStructurePlacement grid) {
        int spacing = grid.spacing();
        List<ChunkPos> inside = new ArrayList<>();
        for (int regionX = Math.floorDiv(SectionPos.blockToSectionCoord(this.area.minX()), spacing);
             regionX <= Math.floorDiv(SectionPos.blockToSectionCoord(this.area.maxX()), spacing); regionX++) {
            for (int regionZ = Math.floorDiv(SectionPos.blockToSectionCoord(this.area.minZ()), spacing);
                 regionZ <= Math.floorDiv(SectionPos.blockToSectionCoord(this.area.maxZ()), spacing); regionZ++) {
                ChunkPos chunk = grid.getPotentialStructureChunk(this.state.getLevelSeed(), regionX * spacing, regionZ * spacing);
                if (this.area.contains(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ())) {
                    inside.add(chunk);
                }
            }
        }
        ChunkPos middle = new ChunkPos(new BlockPos(this.area.centreX(), 0, this.area.centreZ()));
        inside.sort(Comparator.comparingInt(chunk -> chunk.getChessboardDistance(middle)));
        return inside;
    }

    private boolean nearPeople(ChunkPos chunk) {
        if (chunk.getChessboardDistance(new ChunkPos(this.level.getSharedSpawnPos())) < PEOPLE_CLEARANCE_CHUNKS) {
            return true;
        }
        for (ServerPlayer player : this.level.players()) {
            if (chunk.getChessboardDistance(player.chunkPosition()) < PEOPLE_CLEARANCE_CHUNKS) {
                return true;
            }
        }
        return false;
    }

    private boolean nearGrid(ChunkPos chunk) {
        if (!(this.quota.placement() instanceof RandomSpreadStructurePlacement grid)) {
            return false;
        }
        int spacing = grid.spacing();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos site = grid.getPotentialStructureChunk(this.state.getLevelSeed(),
                    chunk.x + dx * spacing, chunk.z + dz * spacing);
                if (chunk.getChessboardDistance(site) < GRID_CLEARANCE_CHUNKS) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean nearSite(ChunkPos chunk) {
        int across = SectionPos.blockToSectionCoord(Math.min(this.area.maxX() - this.area.minX(), this.area.maxZ() - this.area.minZ()));
        int clearance = this.quota.placement() instanceof RandomSpreadStructurePlacement grid
            ? Math.min(grid.separation(), across / 4) : across / 4;
        for (Found site : this.found) {
            if (chunk.getChessboardDistance(site.start()) < clearance) {
                return true;
            }
        }
        for (PlantedSites.Site site : this.planted.sites()) {
            if (site.structure().equals(this.quota.key()) && chunk.getChessboardDistance(site.start()) < clearance) {
                return true;
            }
        }
        return false;
    }

    private void hold(LongSet chunks) {
        for (long packed : chunks) {
            if (this.held.add(packed)) {
                ChunkPos pos = new ChunkPos(packed);
                this.level.getChunkSource().addRegionTicket(TICKET, pos, TICKET_DISTANCE, pos);
            }
        }
    }

    private void release() {
        for (long packed : this.held) {
            ChunkPos pos = new ChunkPos(packed);
            this.level.getChunkSource().removeRegionTicket(TICKET, pos, TICKET_DISTANCE, pos);
        }
        this.held.clear();
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
        return "border quota census in " + this.level.dimension().location()
            + (this.quota == null ? "" : " (" + this.quota.key().location() + ", " + this.stage + ")");
    }
}
