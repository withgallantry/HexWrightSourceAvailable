package com.bluup.hexwright.server.worldgen.arena;

import com.bluup.hexwright.server.worldgen.HexwrightWorldgen;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class ArenaStructure extends Structure {

    private static final int WIDTH = 31;
    private static final int LENGTH = 54;

    private static final int ARENA_MIN = 1;
    private static final int ARENA_MAX = 27;

    private static final int FLAT_TOLERANCE = 4;

    private static final int SITE_TOLERANCE = 9;

    private static final int SAMPLES = 5;

    public static final Codec<ArenaStructure> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            settingsCodec(instance),
            Codec.intRange(-64, 320).optionalFieldOf("min_y", 55).forGetter(s -> s.minY),
            Codec.intRange(-64, 320).optionalFieldOf("max_y", 140).forGetter(s -> s.maxY),
            Codec.intRange(0, 32).optionalFieldOf("flatness", FLAT_TOLERANCE).forGetter(s -> s.flatness),
            Codec.intRange(0, 64).optionalFieldOf("site_flatness", SITE_TOLERANCE)
                .forGetter(s -> s.siteFlatness)
        ).apply(instance, ArenaStructure::new));

    private final int minY;
    private final int maxY;
    private final int flatness;
    private final int siteFlatness;

    public ArenaStructure(StructureSettings settings, int minY, int maxY, int flatness,
                          int siteFlatness) {
        super(settings);
        this.minY = minY;
        this.maxY = Math.max(minY, maxY);
        this.flatness = flatness;
        this.siteFlatness = Math.max(flatness, siteFlatness);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        WorldgenRandom random = context.random();
        Rotation rotation = Rotation.getRandom(random);
        boolean turned = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
        int width = turned ? LENGTH : WIDTH;
        int length = turned ? WIDTH : LENGTH;

        ChunkPos chunk = context.chunkPos();
        int originX = chunk.getMinBlockX();
        int originZ = chunk.getMinBlockZ();

        Survey site = survey(context, originX, originZ, 0, 0, width - 1, length - 1);
        if (site == null || site.spread() > this.siteFlatness) {
            return Optional.empty();
        }

        BoundingBox arena = arenaFootprint(rotation);
        Survey pit = survey(context, originX, originZ,
            arena.minX(), arena.minZ(), arena.maxX(), arena.maxZ());
        if (pit == null || pit.spread() > this.flatness) {
            return Optional.empty();
        }
        if (pit.mean() < this.minY || pit.mean() > this.maxY) {
            return Optional.empty();
        }

        BlockPos origin = new BlockPos(
            originX, pit.mean() - ArenaPiece.GROUND_Y + ArenaPiece.LIFT, originZ);
        return Optional.of(new GenerationStub(origin, builder ->
            builder.addPiece(new ArenaPiece(context.structureTemplateManager(), origin, rotation, chunk))));
    }

    private record Survey(int lowest, int highest, int mean) {
        int spread() {
            return this.highest - this.lowest;
        }
    }

    @Nullable
    private Survey survey(GenerationContext context, int originX, int originZ,
                          int fromX, int fromZ, int toX, int toZ) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        long total = 0L;
        int count = 0;
        for (int sx = 0; sx < SAMPLES; sx++) {
            for (int sz = 0; sz < SAMPLES; sz++) {
                int x = originX + fromX + sx * (toX - fromX) / (SAMPLES - 1);
                int z = originZ + fromZ + sz * (toZ - fromZ) / (SAMPLES - 1);
                int surface = context.chunkGenerator().getFirstOccupiedHeight(
                    x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(),
                    context.randomState());
                NoiseColumn column = context.chunkGenerator().getBaseColumn(
                    x, z, context.heightAccessor(), context.randomState());
                if (!column.getBlock(surface).getFluidState().isEmpty()) {
                    return null;
                }
                lowest = Math.min(lowest, surface);
                highest = Math.max(highest, surface);
                total += surface;
                count++;
            }
        }
        return new Survey(lowest, highest, (int) (total / count));
    }

    private static BoundingBox arenaFootprint(Rotation rotation) {
        int lowX = ARENA_MIN;
        int lowZ = ARENA_MIN;
        int highX = ARENA_MAX;
        int highZ = ARENA_MAX;
        return switch (rotation) {
            case NONE -> new BoundingBox(lowX, 0, lowZ, highX, 0, highZ);
            case CLOCKWISE_90 -> new BoundingBox(
                LENGTH - 1 - highZ, 0, lowX, LENGTH - 1 - lowZ, 0, highX);
            case CLOCKWISE_180 -> new BoundingBox(
                WIDTH - 1 - highX, 0, LENGTH - 1 - highZ, WIDTH - 1 - lowX, 0, LENGTH - 1 - lowZ);
            case COUNTERCLOCKWISE_90 -> new BoundingBox(
                lowZ, 0, WIDTH - 1 - highX, highZ, 0, WIDTH - 1 - lowX);
        };
    }

    @Override
    public StructureType<?> type() {
        return HexwrightWorldgen.ANCIENT_ARENA;
    }
}
