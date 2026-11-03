package com.bluup.hexwright.server.worldgen.dungeon;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

final class DungeonSeal {

    private static final BlockState FILL = Blocks.DEEPSLATE.defaultBlockState();

    private DungeonSeal() {
    }

    static void seal(WorldGenLevel level, BoundingBox module, BoundingBox chunk, LongSet claimed) {
        int minX = Math.max(module.minX(), chunk.minX());
        int maxX = Math.min(module.maxX(), chunk.maxX());
        int minY = Math.max(module.minY(), chunk.minY());
        int maxY = Math.min(module.maxY(), chunk.maxY());
        int minZ = Math.max(module.minZ(), chunk.minZ());
        int maxZ = Math.min(module.maxZ(), chunk.maxZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    if (claimed.contains(pos.asLong())) {
                        continue;
                    }
                    if (intrudes(level.getBlockState(pos))) {
                        level.setBlock(pos.immutable(), FILL, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private static boolean intrudes(BlockState state) {
        return state.isAir()
            || !state.getFluidState().isEmpty()
            || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)
            || state.is(Blocks.SUSPICIOUS_SAND) || state.is(Blocks.SUSPICIOUS_GRAVEL);
    }
}
