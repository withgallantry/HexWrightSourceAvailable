package com.bluup.hexwright.server.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

public final class BuddingKyaniteBlock extends AmethystBlock {

    private static final int GROWTH_CHANCE = BuddingAmethystBlock.GROWTH_CHANCE * 4;

    private static final Direction[] DIRECTIONS = Direction.values();

    public BuddingKyaniteBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(GROWTH_CHANCE) != 0) {
            return;
        }
        Direction direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        BlockPos target = pos.relative(direction);
        BlockState existing = level.getBlockState(target);
        if (!BuddingAmethystBlock.canClusterGrowAtState(existing)) {
            return;
        }
        level.setBlockAndUpdate(target, HexwrightBlocks.KYANITE_CLUSTER_BLOCK.defaultBlockState()
            .setValue(AmethystClusterBlock.FACING, direction)
            .setValue(BlockStateProperties.WATERLOGGED, existing.getFluidState().getType() == Fluids.WATER));
    }
}
