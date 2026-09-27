package com.bluup.hexwright.server.worldgen;

import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;

public record SiteSurvey(int lowest, int highest, int mean) {

    public int spread() {
        return this.highest - this.lowest;
    }

    @Nullable
    public static SiteSurvey of(Structure.GenerationContext context, int minX, int minZ, int maxX, int maxZ,
                                int samples) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        long total = 0L;
        int count = 0;
        for (int sx = 0; sx < samples; sx++) {
            for (int sz = 0; sz < samples; sz++) {
                int x = minX + sx * (maxX - minX) / (samples - 1);
                int z = minZ + sz * (maxZ - minZ) / (samples - 1);
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
        return new SiteSurvey(lowest, highest, (int) (total / count));
    }
}
