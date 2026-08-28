package com.bluup.hexwright.server.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

public final class NearLavaFilter extends PlacementFilter {

    public static final Codec<NearLavaFilter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.intRange(1, 8).fieldOf("radius").forGetter(filter -> filter.radius)
    ).apply(instance, NearLavaFilter::new));

    private final int radius;

    private NearLavaFilter(int radius) {
        this.radius = radius;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minY = Math.max(context.getMinBuildHeight(), pos.getY() - radius);
        int maxY = Math.min(context.getLevel().getMaxBuildHeight() - 1, pos.getY() + radius);
        for (int y = minY; y <= maxY; y++) {
            for (int x = pos.getX() - radius; x <= pos.getX() + radius; x++) {
                for (int z = pos.getZ() - radius; z <= pos.getZ() + radius; z++) {
                    cursor.set(x, y, z);
                    if (context.getBlockState(cursor).getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public PlacementModifierType<?> type() {
        return HexwrightWorldgen.NEAR_LAVA;
    }
}
