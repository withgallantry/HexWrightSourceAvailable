package com.bluup.hexwright.server.worldgen.ruinedportal;

import com.bluup.hexwright.server.worldgen.HexwrightWorldgen;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class RuinedPortalStructure extends Structure {

    static final int FOOTPRINT_RADIUS = 7;
    static final int FOOTPRINT_SIZE = FOOTPRINT_RADIUS * 2 + 1;

    private static final int FLAT_TOLERANCE = 5;
    private static final int SAMPLES = 5;

    public static final Codec<RuinedPortalStructure> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            settingsCodec(instance),
            Codec.intRange(-64, 320).optionalFieldOf("min_y", 50).forGetter(s -> s.minY),
            Codec.intRange(-64, 320).optionalFieldOf("max_y", 200).forGetter(s -> s.maxY),
            Codec.intRange(0, 32).optionalFieldOf("flatness", FLAT_TOLERANCE).forGetter(s -> s.flatness)
        ).apply(instance, RuinedPortalStructure::new));

    private final int minY;
    private final int maxY;
    private final int flatness;

    public RuinedPortalStructure(StructureSettings settings, int minY, int maxY, int flatness) {
        super(settings);
        this.minY = minY;
        this.maxY = Math.max(minY, maxY);
        this.flatness = flatness;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int originX = chunk.getMinBlockX();
        int originZ = chunk.getMinBlockZ();

        Survey site = survey(context, originX, originZ);
        if (site == null || site.spread() > this.flatness) {
            return Optional.empty();
        }
        if (site.mean() < this.minY || site.mean() > this.maxY) {
            return Optional.empty();
        }

        BlockPos origin = new BlockPos(originX, site.mean(), originZ);
        return Optional.of(new GenerationStub(origin, builder ->
            builder.addPiece(new RuinedPortalPiece(origin))));
    }

    private record Survey(int lowest, int highest, int mean) {
        int spread() {
            return this.highest - this.lowest;
        }
    }

    @Nullable
    private Survey survey(GenerationContext context, int originX, int originZ) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        long total = 0L;
        int count = 0;
        for (int sx = 0; sx < SAMPLES; sx++) {
            for (int sz = 0; sz < SAMPLES; sz++) {
                int x = originX - FOOTPRINT_RADIUS + sx * (FOOTPRINT_SIZE - 1) / (SAMPLES - 1);
                int z = originZ - FOOTPRINT_RADIUS + sz * (FOOTPRINT_SIZE - 1) / (SAMPLES - 1);
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

    @Override
    public StructureType<?> type() {
        return HexwrightWorldgen.RUINED_PORTAL;
    }
}
