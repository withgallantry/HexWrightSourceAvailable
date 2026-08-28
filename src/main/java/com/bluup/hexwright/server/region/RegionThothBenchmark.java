package com.bluup.hexwright.server.region;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.PatternShapeMatch;
import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.common.casting.PatternRegistryManifest;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class RegionThothBenchmark {

    private static final String PROPERTY = "hexwright.benchmark.regionThoth";

    private static final CastingEnvironmentComponent.Key<CastingEnvironmentComponent.IsVecInRange> AMBIT_KEY =
        new CastingEnvironmentComponent.Key<>() {
        };
    private static final CastingEnvironmentComponent.Key<CastingEnvironmentComponent.ExtractMedia.Pre> MEDIA_KEY =
        new CastingEnvironmentComponent.Key<>() {
        };
    private static final CastingEnvironmentComponent.Key<CastingEnvironmentComponent.PostExecution> DIAG_KEY =
        new CastingEnvironmentComponent.Key<>() {
        };

    private static final int[] SIZES = {1000, 10000, 30000};

    private static final int[] BULK_SIZES = {1000, 10000, 30000, 60000};
    private static final int[] NOOP_SIZES = {200, 500, 900};
    private static final int TRIALS = 3;

    private static final BlockPos BASE = new BlockPos(200, 5, 200);
    private static final int PREGEN_SIDE = 256;

    private RegionThothBenchmark() {
    }

    public static void register() {
        if (!Boolean.getBoolean(PROPERTY)) {
            return;
        }
        ServerLifecycleEvents.SERVER_STARTED.register(RegionThothBenchmark::runSuiteSafely);
    }

    private static void runSuiteSafely(MinecraftServer server) {
        try {
            runSuite(server);
        } catch (Throwable t) {
            Hexwright.LOGGER.error("[RegionThothBenchmark] suite failed", t);
        } finally {
            server.halt(false);
        }
    }

    private enum Spread {
        CONCENTRATED, WIDE
    }

    private enum Workload {
        NOOP, CONJURE, PLACE, BREAK, BREAK_NO_FX, BULK_FILL, BULK_BREAK
    }

    private static void runSuite(MinecraftServer server) {
        ServerLevel level = server.overworld();
        pregenerate(level);

        FakePlayer player = FakePlayer.get(level);
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(Blocks.STONE, 64));

        Registry<ActionRegistryEntry> actions = IXplatAbstractions.INSTANCE.getActionRegistry();
        HexPattern regionThoth = actions.get(Hexwright.id("region_thoth")).prototype();
        HexPattern breakBlock = actions.get(new ResourceLocation("hexcasting", "break_block")).prototype();
        HexPattern conjureBlock = actions.get(new ResourceLocation("hexcasting", "conjure_block")).prototype();
        HexPattern placeBlock = actions.get(new ResourceLocation("hexcasting", "place_block")).prototype();
        HexPattern regionFill = actions.get(Hexwright.id("region_fill")).prototype();
        HexPattern regionBreak = actions.get(Hexwright.id("region_break")).prototype();

        Hexwright.LOGGER.info("[RegionThothBenchmark] starting; sizes={}, noopSizes={}, trials={}",
            SIZES, NOOP_SIZES, TRIALS);

        PatternRegistryManifest.processRegistry(level);
        CastingEnvironment probeEnv = new StaffCastEnv(player, InteractionHand.MAIN_HAND);
        for (HexPattern probe : List.of(regionThoth, regionFill, regionBreak)) {
            PatternShapeMatch match = PatternRegistryManifest.matchPattern(probe, probeEnv, false);
            if (match instanceof PatternShapeMatch.Nothing) {
                throw new IllegalStateException(
                    "pattern " + probe.anglesSignature() + " does not resolve; aborting rather than measuring noise");
            }
        }

        int overBulk = RegionBlocks.MAX_BULK_BLOCKS + 4096;
        int overThoth = RegionBlocks.MAX_ITERATIONS + 4096;
        Hexwright.LOGGER.info("[RegionThothBenchmark] CAPCHECK bulk>{} and thoth>{} should both refuse",
            RegionBlocks.MAX_BULK_BLOCKS, RegionBlocks.MAX_ITERATIONS);
        runOne(level, player, regionThoth, breakBlock, conjureBlock, placeBlock, regionFill, regionBreak,
            Workload.BULK_BREAK, Spread.WIDE, overBulk, -2);
        runOne(level, player, regionThoth, breakBlock, conjureBlock, placeBlock, regionFill, regionBreak,
            Workload.BULK_BREAK, Spread.WIDE, RegionBlocks.MAX_BULK_BLOCKS - 4096, -3);
        runOne(level, player, regionThoth, breakBlock, conjureBlock, placeBlock, regionFill, regionBreak,
            Workload.BREAK, Spread.WIDE, overThoth, -2);

        Hexwright.LOGGER.info("[RegionThothBenchmark] warming up...");
        for (int i = 0; i < 8; i++) {
            runOne(level, player, regionThoth, breakBlock, conjureBlock, placeBlock, regionFill, regionBreak, Workload.CONJURE,
                Spread.CONCENTRATED, 30000, -1);
            runOne(level, player, regionThoth, breakBlock, conjureBlock, placeBlock, regionFill, regionBreak, Workload.BREAK,
                Spread.WIDE, 30000, -1);
        }
        Hexwright.LOGGER.info("[RegionThothBenchmark] warm-up done.");

        for (Spread spread : Spread.values()) {
            for (int size : NOOP_SIZES) {
                for (int trial = 0; trial < TRIALS; trial++) {
                    runOne(level, player, regionThoth, breakBlock, conjureBlock, placeBlock, regionFill, regionBreak, Workload.NOOP, spread, size, trial);
                }
            }
            for (int size : SIZES) {
                for (Workload workload : new Workload[]{Workload.CONJURE, Workload.PLACE,
                    Workload.BREAK, Workload.BREAK_NO_FX}) {
                    for (int trial = 0; trial < TRIALS; trial++) {
                        runOne(level, player, regionThoth, breakBlock, conjureBlock, placeBlock, regionFill, regionBreak, workload, spread, size, trial);
                    }
                }
            }
            for (int size : BULK_SIZES) {
                for (Workload workload : new Workload[]{Workload.BULK_FILL, Workload.BULK_BREAK}) {
                    for (int trial = 0; trial < TRIALS; trial++) {
                        runOne(level, player, regionThoth, breakBlock, conjureBlock, placeBlock, regionFill, regionBreak, workload, spread, size, trial);
                    }
                }
            }
        }
        Hexwright.LOGGER.info("[RegionThothBenchmark] done.");
    }

    private static void pregenerate(ServerLevel level) {
        int chunkSpan = (PREGEN_SIDE / 16) + 2;
        Hexwright.LOGGER.info("[RegionThothBenchmark] pre-generating {}x{} chunks...", chunkSpan, chunkSpan);
        long start = System.nanoTime();
        for (int cx = 0; cx < chunkSpan; cx++) {
            for (int cz = 0; cz < chunkSpan; cz++) {
                level.getChunk(BASE.getX() / 16 + cx, BASE.getZ() / 16 + cz, ChunkStatus.FULL, true);
            }
        }
        Hexwright.LOGGER.info("[RegionThothBenchmark] pre-generation done in {}ms",
            (System.nanoTime() - start) / 1_000_000);
    }

    private static void runOne(ServerLevel level, FakePlayer player, HexPattern regionThoth, HexPattern breakBlock,
                               HexPattern conjureBlock, HexPattern placeBlock,
                               HexPattern regionFill, HexPattern regionBreak,
                               Workload workload, Spread spread, int targetSize, int trial) {
        int[] dims = boxDims(targetSize, spread);
        int sizeX = dims[0];
        int sizeY = dims[1];
        int sizeZ = dims[2];
        int actualBlocks = sizeX * sizeY * sizeZ;
        int chunkColumns = ((sizeX + 15) / 16) * ((sizeZ + 15) / 16);

        boolean needsStone = workload == Workload.BREAK || workload == Workload.BREAK_NO_FX
            || workload == Workload.BULK_BREAK;
        BlockState prepState = needsStone ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        fillBox(level, BASE, sizeX, sizeY, sizeZ, prepState);

        Iota code = switch (workload) {
            case NOOP -> new ListIota(List.of());
            case CONJURE -> new ListIota(List.of(new PatternIota(conjureBlock)));
            case PLACE -> new ListIota(List.of(new PatternIota(placeBlock)));
            case BREAK, BREAK_NO_FX -> new ListIota(List.of(new PatternIota(breakBlock)));
            case BULK_FILL, BULK_BREAK -> new ListIota(List.of());
        };

        Vec3 min = new Vec3(BASE.getX(), BASE.getY(), BASE.getZ());
        Vec3 max = new Vec3(BASE.getX() + sizeX, BASE.getY() + sizeY, BASE.getZ() + sizeZ);
        boolean bulk = workload == Workload.BULK_FILL || workload == Workload.BULK_BREAK;
        List<Iota> seededStack = bulk
            ? List.of(new RegionIota(Region.box(min, max)))
            : List.of(new RegionIota(Region.box(min, max)), code);
        List<Iota> hex = List.of(new PatternIota(
            switch (workload) {
                case BULK_FILL -> regionFill;
                case BULK_BREAK -> regionBreak;
                default -> regionThoth;
            }));

        CastingEnvironment env = new StaffCastEnv(player, InteractionHand.MAIN_HAND);
        env.addExtension(alwaysInRange());
        env.addExtension(freeMedia());
        int[] mishaps = {0};
        String[] firstMishap = {"none"};
        env.addExtension(mishapWatch(mishaps, firstMishap));

        boolean fxActive = workload == Workload.BREAK || workload == Workload.BREAK_NO_FX
            || workload == Workload.BULK_BREAK;
        if (fxActive) {
            BreakFxProfiling.begin(workload == Workload.BREAK_NO_FX);
        }
        boolean spellTimed = workload != Workload.NOOP;
        if (spellTimed) {
            SpellTiming.begin();
        }

        CastingImage seededImage = new CastingImage().copy(
            seededStack, 0, List.of(), false, 0L, new CompoundTag());
        CastingVM vm = new CastingVM(seededImage, env);
        long startNanos = System.nanoTime();
        try {
            vm.queueExecuteAndWrapIotas(hex, level);
        } catch (Throwable t) {
            Hexwright.LOGGER.error("[RegionThothBenchmark] run threw: {} size={} spread={} trial={}",
                workload, targetSize, spread, trial, t);
        }
        long elapsedNanos = System.nanoTime() - startNanos;

        long fxCount = fxActive ? BreakFxProfiling.end() : -1;
        long spellNanos = spellTimed ? SpellTiming.end() : -1;

        env.removeExtension(AMBIT_KEY);
        env.removeExtension(MEDIA_KEY);
        env.removeExtension(DIAG_KEY);

        double ms = elapsedNanos / 1_000_000.0;
        double spellMs = spellNanos / 1_000_000.0;
        double restMs = spellTimed ? ms - spellMs : ms;
        Hexwright.LOGGER.info(
            "[RegionThothBenchmark] {} spread={} target={} actual={} chunks={} trial={} total={}ms "
                + "spellCast={}ms vmAndConsequences={}ms levelEvent2001={} mishaps={}({})",
            workload, spread, targetSize, actualBlocks, chunkColumns, trial,
            String.format("%.3f", ms),
            spellTimed ? String.format("%.3f", spellMs) : "n/a",
            spellTimed ? String.format("%.3f", restMs) : String.format("%.3f", ms),
            fxCount, mishaps[0], firstMishap[0]);
    }

    private static int[] boxDims(int targetSize, Spread spread) {
        if (spread == Spread.CONCENTRATED) {
            int side = 16;
            int height = Math.max(1, (int) Math.ceil(targetSize / (double) (side * side)));
            return new int[]{side, height, side};
        } else {
            int side = Math.max(1, (int) Math.ceil(Math.sqrt(targetSize)));
            return new int[]{side, 1, side};
        }
    }

    private static void fillBox(ServerLevel level, BlockPos base, int sizeX, int sizeY, int sizeZ, BlockState state) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    pos.set(base.getX() + x, base.getY() + y, base.getZ() + z);
                    level.setBlock(pos, state, 2);
                }
            }
        }
    }

    private static CastingEnvironmentComponent.PostExecution mishapWatch(int[] count, String[] first) {
        return new CastingEnvironmentComponent.PostExecution() {
            @Override
            public CastingEnvironmentComponent.Key<?> getKey() {
                return DIAG_KEY;
            }

            @Override
            public void onPostExecution(CastResult result) {
                for (var effect : result.getSideEffects()) {
                    if (effect instanceof OperatorSideEffect.DoMishap m) {
                        if (count[0]++ == 0) {
                            first[0] = m.getMishap().getClass().getSimpleName();
                        }
                    }
                }
            }
        };
    }

    private static CastingEnvironmentComponent.IsVecInRange alwaysInRange() {
        return new CastingEnvironmentComponent.IsVecInRange() {
            @Override
            public CastingEnvironmentComponent.Key<?> getKey() {
                return AMBIT_KEY;
            }

            @Override
            public boolean onIsVecInRange(Vec3 vec, boolean current) {
                return true;
            }
        };
    }

    private static CastingEnvironmentComponent.ExtractMedia.Pre freeMedia() {
        return new CastingEnvironmentComponent.ExtractMedia.Pre() {
            @Override
            public CastingEnvironmentComponent.Key<?> getKey() {
                return MEDIA_KEY;
            }

            @Override
            public long onExtractMedia(long cost, boolean simulate) {
                return 0L;
            }
        };
    }

    public static final class SpellTiming {
        private static boolean active = false;
        private static long total = 0L;
        private static long enteredAt = 0L;

        private SpellTiming() {
        }

        static void begin() {
            total = 0L;
            active = true;
        }

        static long end() {
            active = false;
            return total;
        }

        public static void enter() {
            if (active) {
                enteredAt = System.nanoTime();
            }
        }

        public static void exit() {
            if (active) {
                total += System.nanoTime() - enteredAt;
            }
        }
    }
}
