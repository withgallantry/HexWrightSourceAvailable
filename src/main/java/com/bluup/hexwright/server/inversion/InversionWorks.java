package com.bluup.hexwright.server.inversion;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.boss.arbiter.ArbiterEntity;
import com.bluup.hexwright.server.hexahedron.Hexahedron;
import com.bluup.hexwright.server.hexahedron.HexahedronStore;
import com.bluup.hexwright.server.maintenance.PlanarIncursion;
import com.bluup.hexwright.server.worldgen.dungeon.PlayerBuilds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class InversionWorks {

    private static final TicketType<ChunkPos> TICKET =
        TicketType.create("hexwright_inversion", Comparator.comparingLong(ChunkPos::toLong));

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final short WET = Short.MIN_VALUE;
    private static final short GROUNDLESS = Short.MIN_VALUE + 1;
    private static final int DEEPEST = 64;
    private static final int CLIFF = 24;
    private static final int DEPTH = 4;
    private static final int PLATEAU = 64;
    private static final int SHOULDER = 112;
    private static final double WETTEST = 0.4;
    private static final int BURIED = 6;
    private static final int COLUMNS_PER_TICK = 400;
    private static final int BLOCKS_PER_TICK = 6000;
    private static final int SURVEY_CHUNKS_PER_TICK = 8;
    private static final int SURVEY_CHUNKS_IN_FLIGHT = 48;
    private static final int GROUND_CHUNKS_PER_TICK = 32;
    private static final int LOAD_TIMEOUT_TICKS = 20 * 180;
    private static final int CRACK_INTERVAL = 50;

    private static final double WITNESS_RANGE = 1024.0;
    private static final double BREAK_OUT_WIDENING = Inversion.HALF;
    private static final long HOLD_MILLIS = PlanarIncursion.DAY_MILLIS;

    private static final Map<UUID, Job> JOBS = new HashMap<>();
    @Nullable
    private static Survey survey;

    private InversionWorks() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(InversionWorks::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            JOBS.clear();
            survey = null;
        });
    }

    public static boolean begin(ServerLevel level, int x, int z, boolean force, Consumer<Component> reply) {
        if (survey != null) {
            return false;
        }
        survey = new Survey(level, x, z, force, null, 0, reply);
        return true;
    }

    public static void recede(ServerLevel level, UUID id) {
        Inversion inversion = InversionStore.get(level, id);
        if (inversion == null || inversion.phase() == Inversion.Phase.RECEDING) {
            return;
        }
        long now = level.getGameTime();
        double reached = inversion.front(now) / Inversion.REACH;
        long since = now - (long) ((1.0 - reached * reached) * Inversion.RECEDE_TICKS);
        Inversion receding = inversion.inPhase(Inversion.Phase.RECEDING, since);
        InversionStore.update(level, receding);
        Job spreading = JOBS.remove(id);
        if (spreading != null) {
            spreading.release();
        }
        level.playSound(null, receding.x() + 0.5, receding.groundY() + 8.0, receding.z() + 0.5,
            InversionSounds.INVERSION_RECEDE.get(), SoundSource.AMBIENT, 24.0f, 0.8f);
        announce(level, Component.translatable("message.hexwright.inversion.receding", receding.x(), receding.z()));
        HexwrightDebug.log(HexwrightDebug.INVERSION, "[inversion] {} receding from {} blocks", id,
            (int) (reached * Inversion.REACH));
    }

    private static void outlasted(ServerLevel level, Inversion inversion) {
        if (ArbiterEntity.withdraw(level, inversion)) {
            return;
        }
        announce(level, Component.translatable(ArbiterEntity.WITHDRAWN));
        recede(level, inversion.id());
        HexwrightDebug.log(HexwrightDebug.INVERSION, "[inversion] {} outlasted its day with no Arbiter loaded", inversion.id());
    }

    static @Nullable String surveying() {
        return survey == null ? null : "surveying " + survey.x + " " + survey.z;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                for (Inversion inversion : InversionStore.in(level)) {
                    if (inversion.phase() != Inversion.Phase.HELD && !JOBS.containsKey(inversion.id())) {
                        JOBS.put(inversion.id(), new Job(level, inversion, null, false));
                    }
                    if (inversion.phase() == Inversion.Phase.HELD && inversion.heldAt() >= 0L
                        && System.currentTimeMillis() - inversion.heldAt() >= HOLD_MILLIS) {
                        outlasted(level, inversion);
                    }
                }
                if (survey == null) {
                    breakOut(level);
                }
            }
        }
        Iterator<Job> jobs = JOBS.values().iterator();
        while (jobs.hasNext()) {
            Job job = jobs.next();
            if (job.tick()) {
                job.release();
                jobs.remove();
            }
        }
        if (survey != null) {
            Survey current = survey;
            Step step = current.tick();
            if (step != Step.WORKING) {
                survey = null;
                current.finish(step);
            }
        }
    }

    private static void breakOut(ServerLevel level) {
        long now = level.getGameTime();
        for (Hexahedron hexahedron : HexahedronStore.in(level)) {
            if (!hexahedron.opensInversion() || InversionStore.spent(level, hexahedron.id()) || !hexahedron.anchorsDown()
                || now < hexahedron.burstAt() + Hexahedron.CLEAR_TICKS || holdsInversion(level, hexahedron)) {
                continue;
            }
            if (witnessed(level, hexahedron)) {
                survey = Survey.under(level, hexahedron, 0);
                return;
            }
        }
    }

    private static boolean witnessed(ServerLevel level, Hexahedron hexahedron) {
        return level.players().stream().anyMatch(player -> !player.isSpectator()
            && Math.hypot(player.getX() - hexahedron.x(), player.getZ() - hexahedron.z()) < WITNESS_RANGE);
    }

    private static boolean holdsInversion(ServerLevel level, Hexahedron hexahedron) {
        return InversionStore.in(level).stream()
            .anyMatch(inversion -> hexahedron.covers(new Vec3(inversion.x(), inversion.groundY(), inversion.z())));
    }

    public static void announce(ServerLevel level, Component message) {
        Component styled = message.copy().withStyle(ChatFormatting.DARK_PURPLE);
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(styled);
        }
    }

    private static List<ChunkPos> chunksOf(int x, int z) {
        List<ChunkPos> chunks = new ArrayList<>();
        int minX = x - Inversion.HALF;
        int minZ = z - Inversion.HALF;
        for (int cx = minX >> 4; cx <= (minX + Inversion.SIZE - 1) >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= (minZ + Inversion.SIZE - 1) >> 4; cz++) {
                chunks.add(new ChunkPos(cx, cz));
            }
        }
        return chunks;
    }

    private static void ticket(ServerLevel level, List<ChunkPos> chunks) {
        for (ChunkPos chunk : chunks) {
            level.getChunkSource().addRegionTicket(TICKET, chunk, 0, chunk);
        }
    }

    private static void unticket(ServerLevel level, List<ChunkPos> chunks) {
        for (ChunkPos chunk : chunks) {
            level.getChunkSource().removeRegionTicket(TICKET, chunk, 0, chunk);
        }
    }

    private static boolean loaded(ServerLevel level, List<ChunkPos> chunks) {
        for (ChunkPos chunk : chunks) {
            if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null || !level.areEntitiesLoaded(chunk.toLong())) {
                return false;
            }
        }
        return true;
    }

    private static short groundOf(ServerLevel level, LevelChunk chunk, int x, int z, @Nullable Consumer<String> artificial) {
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        for (int y = top; y > top - DEEPEST && y >= level.getMinBuildHeight(); y--) {
            pos.setY(y);
            BlockState state = chunk.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (artificial != null && state.is(InvertedBlocks.ARTIFICIAL)) {
                artificial.accept(PlayerBuilds.describe(state, pos));
            }
            if (!state.getFluidState().isEmpty()) {
                return WET;
            }
            if (InvertedBlocks.isTerrain(state, level, pos)) {
                return (short) y;
            }
        }
        return GROUNDLESS;
    }

    private enum Step { WORKING, ACCEPTED, REFUSED }

    private static final class Survey {
        final ServerLevel level;
        final int x;
        final int z;
        final boolean force;
        @Nullable
        final UUID source;
        final int attempt;
        final Consumer<Component> reply;
        final List<ChunkPos> chunks;
        final short[] ground = new short[Inversion.SIZE * Inversion.SIZE];
        int ticketed;
        int scanned;
        int waited;
        int wet;
        @Nullable
        String refusal;

        Survey(ServerLevel level, int x, int z, boolean force, @Nullable UUID source, int attempt,
               Consumer<Component> reply) {
            this.level = level;
            this.x = x;
            this.z = z;
            this.force = force;
            this.source = source;
            this.attempt = attempt;
            this.reply = reply;
            this.chunks = chunksOf(x, z);
            if (InversionStore.overlapsAny(level, x, z)) {
                this.refusal = "it would overlap an Inversion already there";
            }
            HexwrightDebug.log(HexwrightDebug.INVERSION, "[inversion] surveying {} {} (attempt {})", x, z, attempt);
        }

        static Survey under(ServerLevel level, Hexahedron hexahedron, int attempt) {
            RandomSource random = level.getRandom();
            double angle = random.nextDouble() * Math.PI * 2.0;
            double reach = Math.min(hexahedron.radius() * 0.75 + attempt * BREAK_OUT_WIDENING, WITNESS_RANGE);
            double distance = Math.sqrt(random.nextDouble()) * reach;
            return new Survey(level, Mth.floor(hexahedron.x() + Math.cos(angle) * distance),
                Mth.floor(hexahedron.z() + Math.sin(angle) * distance), false, hexahedron.id(), attempt,
                message -> HexwrightDebug.log(HexwrightDebug.INVERSION, "[inversion] {}", message.getString()));
        }

        Step tick() {
            if (this.refusal != null) {
                return Step.REFUSED;
            }
            while (this.ticketed < this.chunks.size() && this.ticketed < this.scanned + SURVEY_CHUNKS_IN_FLIGHT) {
                ChunkPos pos = this.chunks.get(this.ticketed++);
                this.level.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);
            }
            int before = this.scanned;
            for (int i = 0; i < SURVEY_CHUNKS_PER_TICK && this.scanned < this.chunks.size(); i++) {
                ChunkPos pos = this.chunks.get(this.scanned);
                LevelChunk chunk = this.level.getChunkSource().getChunkNow(pos.x, pos.z);
                if (chunk == null || !this.level.areEntitiesLoaded(pos.toLong())) {
                    break;
                }
                this.scanned++;
                look(chunk);
                if (this.refusal != null) {
                    return Step.REFUSED;
                }
            }
            if (!this.force && this.wet > this.ground.length * WETTEST) {
                this.refusal = "too much of it is water (" + this.wet * 100 / this.ground.length + "% or more)";
                return Step.REFUSED;
            }
            if (this.scanned < this.chunks.size()) {
                this.waited = this.scanned > before ? 0 : this.waited + 1;
                if (this.waited >= LOAD_TIMEOUT_TICKS) {
                    this.refusal = "its ground never finished loading";
                    return Step.REFUSED;
                }
                return Step.WORKING;
            }
            return Step.ACCEPTED;
        }

        private void look(LevelChunk chunk) {
            int minX = this.x - Inversion.HALF;
            int minZ = this.z - Inversion.HALF;
            ChunkPos pos = chunk.getPos();
            int lowest = Integer.MAX_VALUE;
            int highest = Integer.MIN_VALUE;
            Consumer<String> built = this.force ? null : found -> {
                if (this.refusal == null) {
                    this.refusal = "somebody has built there: " + found;
                }
            };
            for (int bx = Math.max(pos.getMinBlockX(), minX); bx <= Math.min(pos.getMaxBlockX(), minX + Inversion.SIZE - 1); bx++) {
                for (int bz = Math.max(pos.getMinBlockZ(), minZ); bz <= Math.min(pos.getMaxBlockZ(), minZ + Inversion.SIZE - 1); bz++) {
                    short ground = groundOf(this.level, chunk, bx, bz, built);
                    this.ground[(bz - minZ) * Inversion.SIZE + (bx - minX)] = ground;
                    if (ground == WET) {
                        this.wet++;
                    } else if (ground != GROUNDLESS) {
                        lowest = Math.min(lowest, ground);
                    }
                    highest = Math.max(highest, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, bx & 15, bz & 15));
                }
            }
            if (this.force || this.refusal != null || lowest == Integer.MAX_VALUE) {
                return;
            }
            for (BlockEntity entity : chunk.getBlockEntities().values()) {
                BlockPos at = entity.getBlockPos();
                if (at.getY() >= lowest - BURIED && covers(at.getX(), at.getZ()) && !PlayerBuilds.isWorldgen(entity)
                    && !entity.getBlockState().is(Blocks.BEE_NEST)) {
                    this.refusal = "somebody keeps something there: " + PlayerBuilds.describe(entity.getBlockState(), at);
                    return;
                }
            }
            BoundingBox box = new BoundingBox(minX, lowest - BURIED, minZ, minX + Inversion.SIZE - 1, highest + 2,
                minZ + Inversion.SIZE - 1);
            String stranger = PlayerBuilds.strangerIn(this.level, chunk, box);
            if (stranger != null) {
                this.refusal = "somebody's things are there: " + stranger;
            }
        }

        private int floor() {
            int[] heights = new int[Inversion.SIZE * Inversion.SIZE];
            int count = 0;
            for (int dz = -PLATEAU; dz <= PLATEAU; dz++) {
                for (int dx = -PLATEAU; dx <= PLATEAU; dx++) {
                    short ground = this.ground[(Inversion.HALF + dz) * Inversion.SIZE + Inversion.HALF + dx];
                    if (dx * dx + dz * dz <= PLATEAU * PLATEAU && ground != WET && ground != GROUNDLESS) {
                        heights[count++] = ground;
                    }
                }
            }
            if (count == 0) {
                return this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.x, this.z) - 1;
            }
            Arrays.sort(heights, 0, count);
            return heights[count / 2];
        }

        private boolean covers(int bx, int bz) {
            return bx >= this.x - Inversion.HALF && bx < this.x + Inversion.HALF
                && bz >= this.z - Inversion.HALF && bz < this.z + Inversion.HALF;
        }

        void finish(Step step) {
            if (step == Step.ACCEPTED) {
                open();
                return;
            }
            unticket(this.level, this.chunks.subList(0, this.ticketed));
            HexwrightDebug.log(HexwrightDebug.INVERSION, "[inversion] refused {} {} after {} of {} chunks: {}",
                this.x, this.z, this.scanned, this.chunks.size(), this.refusal);
            this.reply.accept(Component.literal("Not at " + this.x + " " + this.z + ": " + this.refusal + "."));
            if (this.source == null) {
                return;
            }
            Hexahedron hexahedron = HexahedronStore.in(this.level).stream()
                .filter(each -> each.id().equals(this.source)).findFirst().orElse(null);
            if (hexahedron != null && witnessed(this.level, hexahedron)) {
                survey = under(this.level, hexahedron, this.attempt + 1);
            }
        }

        private void open() {
            int groundY = floor();
            Inversion inversion = new Inversion(UUID.randomUUID(), this.x, this.z, groundY,
                Inversion.Phase.FRACTURING, this.level.getGameTime(), this.source, -1L);
            InversionStore.add(this.level, inversion);
            if (this.source != null) {
                HexahedronStore.remove(this.level, this.source);
            }
            JOBS.put(inversion.id(), new Job(this.level, inversion, this.ground, true));
            this.level.playSound(null, this.x + 0.5, groundY + 16.0, this.z + 0.5, InversionSounds.INVERSION_TEAR.get(),
                SoundSource.AMBIENT, 32.0f, 0.8f);
            announce(this.level, Component.translatable("message.hexwright.inversion.begun", this.x, this.z));
            this.reply.accept(Component.literal("The Inversion is breaking open at " + this.x + " " + this.z + "."));
            HexwrightDebug.log(HexwrightDebug.INVERSION, "[inversion] opened {} at {} {}, ground {}, {}% water",
                inversion.id(), this.x, this.z, groundY, this.wet * 100 / this.ground.length);
        }
    }

    private static final class Job {
        final ServerLevel level;
        final UUID id;
        final List<ChunkPos> chunks;
        final int[] order;
        @Nullable
        short[] ground;
        int groundChunks;
        int cursor;
        int waited;
        boolean ticketed;
        boolean loaded;

        Job(ServerLevel level, Inversion inversion, @Nullable short[] ground, boolean ticketed) {
            this.level = level;
            this.id = inversion.id();
            this.chunks = chunksOf(inversion.x(), inversion.z());
            this.order = nearestFirst();
            this.ground = ground;
            this.ticketed = ticketed;
            this.loaded = ticketed;
            if (!ticketed) {
                ticket(level, this.chunks);
                this.ticketed = true;
            }
        }

        private static int[] nearestFirst() {
            int size = Inversion.SIZE;
            long[] keyed = new long[size * size];
            for (int dz = 0; dz < size; dz++) {
                for (int dx = 0; dx < size; dx++) {
                    long ox = 2L * (dx - Inversion.HALF) + 1L;
                    long oz = 2L * (dz - Inversion.HALF) + 1L;
                    keyed[dz * size + dx] = (ox * ox + oz * oz) << 20 | (dz * size + dx);
                }
            }
            Arrays.sort(keyed);
            int[] order = new int[keyed.length];
            for (int i = 0; i < keyed.length; i++) {
                order[i] = (int) (keyed[i] & 0xFFFFF);
            }
            return order;
        }

        void release() {
            if (this.ticketed) {
                unticket(this.level, this.chunks);
                this.ticketed = false;
            }
        }

        boolean tick() {
            Inversion inversion = InversionStore.get(this.level, this.id);
            if (inversion == null || inversion.phase() == Inversion.Phase.HELD) {
                return true;
            }
            if (!this.loaded) {
                if (!loaded(this.level, this.chunks)) {
                    if (++this.waited % LOAD_TIMEOUT_TICKS == 0) {
                        Hexwright.LOGGER.warn("The Inversion at {} {} is still waiting for its ground to load",
                            inversion.x(), inversion.z());
                    }
                    return false;
                }
                this.loaded = true;
            }
            return inversion.phase() == Inversion.Phase.FRACTURING ? swallowing(inversion) : givingBack(inversion);
        }

        private boolean swallowing(Inversion inversion) {
            if (this.ground == null) {
                this.ground = new short[Inversion.SIZE * Inversion.SIZE];
            }
            if (this.groundChunks < this.chunks.size()) {
                readGround(inversion);
                return false;
            }
            InversionOriginals originals = InversionStore.originals(this.level, this.id);
            double front = inversion.front(this.level.getGameTime());
            int done = 0;
            int moved = 0;
            while (this.cursor < this.order.length && done < COLUMNS_PER_TICK && moved < BLOCKS_PER_TICK) {
                int column = this.order[this.cursor];
                int bx = inversion.minX() + column % Inversion.SIZE;
                int bz = inversion.minZ() + column / Inversion.SIZE;
                if (inversion.distanceTo(bx, bz) > front) {
                    break;
                }
                if (!originals.isDone(bx, bz)) {
                    moved += swallow(inversion, originals, column, bx, bz);
                    done++;
                }
                this.cursor++;
            }
            if (done > 0) {
                InversionStore.changed(this.level);
            }
            crackle(inversion, front);
            if (this.cursor < this.order.length) {
                return false;
            }
            InversionStore.update(this.level, inversion.held(this.level.getGameTime(), System.currentTimeMillis()));
            ArbiterEntity.arrive(this.level, inversion);
            HexwrightDebug.log(HexwrightDebug.INVERSION, "[inversion] {} swallowed whole", this.id);
            return true;
        }

        private void readGround(Inversion inversion) {
            for (int i = 0; i < GROUND_CHUNKS_PER_TICK && this.groundChunks < this.chunks.size(); i++) {
                ChunkPos pos = this.chunks.get(this.groundChunks++);
                LevelChunk chunk = this.level.getChunkSource().getChunkNow(pos.x, pos.z);
                if (chunk == null) {
                    continue;
                }
                for (int bx = pos.getMinBlockX(); bx <= pos.getMaxBlockX(); bx++) {
                    for (int bz = pos.getMinBlockZ(); bz <= pos.getMaxBlockZ(); bz++) {
                        if (inversion.covers(bx, bz)) {
                            this.ground[(bz - inversion.minZ()) * Inversion.SIZE + (bx - inversion.minX())] =
                                groundOf(this.level, chunk, bx, bz, null);
                        }
                    }
                }
            }
        }

        private int swallow(Inversion inversion, InversionOriginals originals, int column, int bx, int bz) {
            LevelChunk chunk = this.level.getChunkSource().getChunkNow(bx >> 4, bz >> 4);
            if (chunk == null) {
                return 0;
            }
            int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, bx & 15, bz & 15);
            short ground = this.ground[column];
            List<InversionOriginals.Original> taken = new ArrayList<>();
            double distance = inversion.distanceTo(bx, bz);
            if (distance < SHOULDER && ground != GROUNDLESS) {
                flatten(inversion, chunk, bx, bz, top, ground, distance, taken);
            } else {
                copy(chunk, column, bx, bz, top, ground, taken);
            }
            originals.swallowed(bx, bz, taken);
            return taken.size();
        }

        private void copy(LevelChunk chunk, int column, int bx, int bz, int top, short ground,
                          List<InversionOriginals.Original> taken) {
            int floor;
            if (ground == WET || ground == GROUNDLESS) {
                floor = top - DEEPEST;
            } else {
                int lowest = ground;
                int dx = column % Inversion.SIZE;
                int dz = column / Inversion.SIZE;
                for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = dx + step[0];
                    int nz = dz + step[1];
                    if (nx < 0 || nz < 0 || nx >= Inversion.SIZE || nz >= Inversion.SIZE) {
                        continue;
                    }
                    short beside = this.ground[nz * Inversion.SIZE + nx];
                    if (beside != WET && beside != GROUNDLESS) {
                        lowest = Math.min(lowest, beside + 1);
                    }
                }
                floor = Math.min(Math.max(lowest, ground - CLIFF), ground - DEPTH);
            }
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, top, bz);
            for (int y = top; y >= floor && y >= this.level.getMinBuildHeight(); y--) {
                pos.setY(y);
                BlockState state = chunk.getBlockState(pos);
                if (state.isAir()) {
                    continue;
                }
                if (!state.getFluidState().isEmpty()) {
                    break;
                }
                BlockState copy = InvertedBlocks.mimic(state, this.level, pos);
                if (copy != null) {
                    replace(chunk, pos, state, copy, taken);
                }
            }
        }

        private void flatten(Inversion inversion, LevelChunk chunk, int bx, int bz, int top, short ground, double distance,
                           List<InversionOriginals.Original> taken) {
            int natural = ground == WET ? top : ground;
            double out = Mth.clamp((distance - PLATEAU) / (SHOULDER - PLATEAU), 0.0, 1.0);
            int floor = (int) Math.round(Mth.lerp(out * out * (3.0 - 2.0 * out), inversion.groundY(), natural));
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, natural, bz);
            InvertedBlocks.Skin surface = ground == WET ? InvertedBlocks.Skin.SAND
                : InvertedBlocks.skinOf(chunk.getBlockState(pos));
            InvertedBlocks.Skin under = surface == InvertedBlocks.Skin.GRASS || surface == InvertedBlocks.Skin.MOSS
                || surface == InvertedBlocks.Skin.SNOW ? InvertedBlocks.Skin.DIRT : surface;
            BlockState air = Blocks.AIR.defaultBlockState();
            for (int y = top; y > floor; y--) {
                pos.setY(y);
                BlockState state = chunk.getBlockState(pos);
                if (!state.isAir()) {
                    replace(chunk, pos, state, air, taken);
                }
            }
            for (int y = floor; y >= floor - DEPTH && y >= this.level.getMinBuildHeight(); y--) {
                pos.setY(y);
                replace(chunk, pos, chunk.getBlockState(pos), InvertedBlocks.ground(y == floor ? surface : under), taken);
            }
        }

        private void replace(LevelChunk chunk, BlockPos.MutableBlockPos pos, BlockState state, BlockState with,
                             List<InversionOriginals.Original> taken) {
            CompoundTag blockEntity = null;
            if (state.hasBlockEntity()) {
                BlockEntity entity = chunk.getBlockEntity(pos);
                if (entity != null) {
                    blockEntity = entity.saveWithoutMetadata();
                    Clearable.tryClear(entity);
                }
            }
            taken.add(new InversionOriginals.Original(pos.immutable(), state, blockEntity));
            this.level.setBlock(pos, with, FLAGS);
        }

        private boolean givingBack(Inversion inversion) {
            InversionOriginals originals = InversionStore.originals(this.level, this.id);
            double front = inversion.front(this.level.getGameTime());
            int done = 0;
            int moved = 0;
            while (this.cursor < this.order.length && done < COLUMNS_PER_TICK && moved < BLOCKS_PER_TICK) {
                int column = this.order[this.order.length - 1 - this.cursor];
                int bx = inversion.minX() + column % Inversion.SIZE;
                int bz = inversion.minZ() + column / Inversion.SIZE;
                if (inversion.distanceTo(bx, bz) <= front) {
                    break;
                }
                moved += giveBack(originals, bx, bz);
                done++;
                this.cursor++;
            }
            if (done > 0) {
                InversionStore.changed(this.level);
            }
            if (this.cursor < this.order.length) {
                return false;
            }
            InversionStore.remove(this.level, this.id);
            HexwrightDebug.log(HexwrightDebug.INVERSION, "[inversion] {} gone; every column given back", this.id);
            return true;
        }

        private int giveBack(InversionOriginals originals, int bx, int bz) {
            List<InversionOriginals.Original> back = originals.giveBack(bx, bz);
            for (InversionOriginals.Original original : back) {
                BlockState now = this.level.getBlockState(original.pos());
                if (!InvertedBlocks.is(now) && !now.isAir()) {
                    continue;
                }
                this.level.setBlock(original.pos(), original.state(), FLAGS);
                if (original.blockEntity() != null) {
                    BlockEntity entity = this.level.getBlockEntity(original.pos());
                    if (entity != null) {
                        entity.load(original.blockEntity());
                        entity.setChanged();
                    }
                }
            }
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, 0, bz);
            int top = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1;
            for (int y = top; y > top - DEEPEST && y >= this.level.getMinBuildHeight(); y--) {
                pos.setY(y);
                BlockState state = this.level.getBlockState(pos);
                if (InvertedBlocks.is(state)) {
                    this.level.setBlock(pos, InvertedBlocks.fallback(state), FLAGS);
                } else if (InvertedBlocks.isTerrain(state, this.level, pos)) {
                    break;
                }
            }
            return back.size();
        }

        private void crackle(Inversion inversion, double front) {
            if (this.level.getGameTime() % CRACK_INTERVAL != 0 || front >= Inversion.REACH) {
                return;
            }
            RandomSource random = this.level.getRandom();
            double angle = random.nextDouble() * Math.PI * 2.0;
            double x = inversion.x() + Math.cos(angle) * front;
            double z = inversion.z() + Math.sin(angle) * front;
            if (!inversion.covers(x, z)) {
                return;
            }
            int y = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(x), Mth.floor(z));
            this.level.playSound(null, x, y + 4.0, z, InversionSounds.INVERSION_RUMBLE.get(), SoundSource.AMBIENT,
                8.0f, 0.8f + random.nextFloat() * 0.2f);
        }
    }
}
