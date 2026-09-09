package com.bluup.hexwright.server.worldgen.bindstone;

import com.bluup.hexwright.server.bindstone.BindstonePillar;
import com.bluup.hexwright.server.worldgen.HexwrightWorldgen;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BindstonePillarStructure extends Structure {

    static final int LAKE_RADIUS = 7;
    static final int WALL_RADIUS = 8;

    static final int LAKE_DEPTH = 4;

    private static final int HEADROOM = 4;
    static final int REQUIRED_CLEARANCE = BindstonePillar.RINGS + HEADROOM;

    private static final int LAKE_CLEARANCE = 3;

    private static final int[] LATTICE = {2, 6, 9, 13};

    private static final double OPEN_FRACTION = 0.40;

    private static final int MAX_SURVEYS = 3;

    public static final Codec<BindstonePillarStructure> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            settingsCodec(instance),
            Codec.intRange(-64, 320).optionalFieldOf("min_y", -59).forGetter(s -> s.minY),
            Codec.intRange(-64, 320).optionalFieldOf("max_y", 20).forGetter(s -> s.maxY)
        ).apply(instance, BindstonePillarStructure::new));

    private final int minY;
    private final int maxY;

    public BindstonePillarStructure(StructureSettings settings, int minY, int maxY) {
        super(settings);
        this.minY = minY;
        this.maxY = Math.max(minY, maxY);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        BlockPos centre = findChamber(context, context.chunkPos());
        if (centre == null) {
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(centre, builder ->
            builder.addPiece(new BindstonePillarPiece(centre))));
    }

    private @Nullable BlockPos findChamber(GenerationContext context, ChunkPos chunk) {
        List<Candidate> candidates = new ArrayList<>();
        for (int lx : LATTICE) {
            for (int lz : LATTICE) {
                int x = chunk.getMinBlockX() + lx;
                int z = chunk.getMinBlockZ() + lz;
                NoiseColumn column = column(context, x, z);
                Floor floor = findCavernFloor(context, column);
                if (floor == null || !hasHeadroom(context, column, floor.y())) {
                    continue;
                }
                candidates.add(new Candidate(new BlockPos(x, floor.y(), z), floor.clearance()));
            }
        }
        candidates.sort((a, b) -> Integer.compare(b.clearance(), a.clearance()));

        int surveyed = 0;
        for (Candidate candidate : candidates) {
            if (surveyed++ >= MAX_SURVEYS) {
                break;
            }
            if (openness(context, candidate.centre()) >= RING_SAMPLES.length * OPEN_FRACTION) {
                return candidate.centre();
            }
        }
        return null;
    }

    private record Candidate(BlockPos centre, int clearance) {
    }

    private record Floor(int y, int clearance) {
    }

    private static NoiseColumn column(GenerationContext context, int x, int z) {
        return context.chunkGenerator().getBaseColumn(
            x, z, context.heightAccessor(), context.randomState());
    }

    private @Nullable Floor findCavernFloor(GenerationContext context, NoiseColumn column) {
        int bottom = Math.max(this.minY, context.heightAccessor().getMinBuildHeight() + LAKE_DEPTH + 2);
        int top = this.maxY;

        int bestFloor = Integer.MIN_VALUE;
        int bestClearance = 0;
        int runStart = Integer.MIN_VALUE;

        for (int y = bottom; y <= top; y++) {
            if (column.getBlock(y).isAir()) {
                if (runStart == Integer.MIN_VALUE) {
                    runStart = y;
                }
                continue;
            }
            if (runStart > bottom && y - runStart > bestClearance) {
                bestClearance = y - runStart;
                bestFloor = runStart - 1;
            }
            runStart = Integer.MIN_VALUE;
        }
        if (runStart > bottom && top + 1 - runStart > bestClearance) {
            bestClearance = top + 1 - runStart;
            bestFloor = runStart - 1;
        }
        return bestFloor == Integer.MIN_VALUE ? null : new Floor(bestFloor, bestClearance);
    }

    private static boolean hasHeadroom(GenerationContext context, NoiseColumn column, int floorY) {
        if (floorY + REQUIRED_CLEARANCE >= context.heightAccessor().getMaxBuildHeight()) {
            return false;
        }
        for (int dy = 1; dy <= REQUIRED_CLEARANCE; dy++) {
            if (!column.getBlock(floorY + dy).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static final int[][] RING_SAMPLES = ringSamples();

    private static int[][] ringSamples() {
        int[] radii = {LAKE_RADIUS - 1, (LAKE_RADIUS - 1) / 2};
        int[][] samples = new int[radii.length * 8][];
        int index = 0;
        for (int radius : radii) {
            for (int step = 0; step < 8; step++) {
                double angle = step * Math.PI / 4.0;
                samples[index++] = new int[]{
                    (int) Math.round(Math.cos(angle) * radius),
                    (int) Math.round(Math.sin(angle) * radius)
                };
            }
        }
        return samples;
    }

    private static int openness(GenerationContext context, BlockPos centre) {
        int open = 0;
        for (int[] sample : RING_SAMPLES) {
            NoiseColumn column = column(context, centre.getX() + sample[0], centre.getZ() + sample[1]);
            boolean clear = true;
            for (int dy = 1; dy <= LAKE_CLEARANCE && clear; dy++) {
                clear = column.getBlock(centre.getY() + dy).isAir();
            }
            if (clear) {
                open++;
            }
        }
        return open;
    }

    @Override
    public StructureType<?> type() {
        return HexwrightWorldgen.BINDSTONE_PILLAR;
    }
}
