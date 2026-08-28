package com.bluup.hexwright.server.worldgen;

import com.bluup.hexwright.server.bindstone.BindstonePillar;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.jetbrains.annotations.Nullable;

public class BindstonePillarFeature extends Feature<NoneFeatureConfiguration> {

    private static final int GRID_SPACING = 10;

    private static final int GRID_SEPARATION = 4;

    private static final long GRID_SALT = 0x8112D0B4A1FL;

    private static final int SEARCH_TOP = 20;
    private static final int SEARCH_BOTTOM = -59;

    private static final int LAKE_RADIUS = 7;
    private static final int WALL_RADIUS = 8;

    private static final int LAKE_CELLS = countLakeCells();

    private static int countLakeCells() {
        int cells = 0;
        for (int dx = -LAKE_RADIUS; dx <= LAKE_RADIUS; dx++) {
            for (int dz = -LAKE_RADIUS; dz <= LAKE_RADIUS; dz++) {
                if (dx * dx + dz * dz <= LAKE_RADIUS * LAKE_RADIUS) {
                    cells++;
                }
            }
        }
        return cells;
    }

    private static final int LAKE_DEPTH = 4;

    private static final int HEADROOM = 4;
    private static final int REQUIRED_CLEARANCE = BindstonePillar.RINGS + HEADROOM;

    private static final int LAKE_CLEARANCE = 3;

    private static final int MOAT_CLEARING = 8;

    private static final double LAKE_OPEN_FRACTION = 0.40;

    public BindstonePillarFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        if (!isGridChunk(level.getSeed(), origin.getX() >> 4, origin.getZ() >> 4)) {
            return false;
        }
        BlockPos centre = findChamber(level, origin);
        if (centre == null) {
            return false;
        }
        digLake(level, centre);
        BindstonePillar.build(level, centre.above(2));
        return true;
    }

    private static @Nullable BlockPos findChamber(WorldGenLevel level, BlockPos origin) {
        BlockPos best = null;
        int bestScore = -1;
        for (int lx : LATTICE) {
            for (int lz : LATTICE) {
                int x = (origin.getX() & ~15) + lx;
                int z = (origin.getZ() & ~15) + lz;
                int groundTop = findCavernFloor(level, x, z);
                if (groundTop == Integer.MIN_VALUE) {
                    continue;
                }
                BlockPos candidate = new BlockPos(x, groundTop, z);
                if (!hasHeadroom(level, candidate)) {
                    continue;
                }
                int open = openness(level, candidate);
                if (open < LAKE_CELLS * LAKE_OPEN_FRACTION) {
                    continue;
                }
                if (open > bestScore) {
                    bestScore = open;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static final int[] LATTICE = {2, 6, 9, 13};

    private static boolean isGridChunk(long seed, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, GRID_SPACING);
        int regionZ = Math.floorDiv(chunkZ, GRID_SPACING);
        long hash = seed + GRID_SALT
            + regionX * 341873128712L
            + regionZ * 132897987541L;
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        hash ^= hash >>> 31;

        int range = GRID_SPACING - GRID_SEPARATION;
        int offsetX = (int) Math.floorMod(hash, range);
        int offsetZ = (int) Math.floorMod(hash >>> 32, range);
        return chunkX == regionX * GRID_SPACING + offsetX
            && chunkZ == regionZ * GRID_SPACING + offsetZ;
    }

    private static int findCavernFloor(WorldGenLevel level, int x, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int top = SEARCH_TOP;
        int bottom = Math.max(SEARCH_BOTTOM, level.getMinBuildHeight() + LAKE_DEPTH + 2);

        int bestFloor = Integer.MIN_VALUE;
        int bestClearance = 0;
        int runStart = Integer.MIN_VALUE;

        for (int y = bottom; y <= top; y++) {
            cursor.set(x, y, z);
            if (level.getBlockState(cursor).isAir()) {
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
            bestFloor = runStart - 1;
        }
        return bestFloor;
    }

    private static boolean hasHeadroom(WorldGenLevel level, BlockPos centre) {
        if (centre.getY() + REQUIRED_CLEARANCE >= level.getMaxBuildHeight()) {
            return false;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= REQUIRED_CLEARANCE; dy++) {
            cursor.set(centre.getX(), centre.getY() + dy, centre.getZ());
            if (!level.getBlockState(cursor).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static int openness(WorldGenLevel level, BlockPos centre) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int open = 0;
        for (int dx = -LAKE_RADIUS; dx <= LAKE_RADIUS; dx++) {
            for (int dz = -LAKE_RADIUS; dz <= LAKE_RADIUS; dz++) {
                if (dx * dx + dz * dz > LAKE_RADIUS * LAKE_RADIUS) {
                    continue;
                }
                boolean clear = true;
                for (int dy = 1; dy <= LAKE_CLEARANCE && clear; dy++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    clear = level.getBlockState(cursor).isAir();
                }
                if (clear) {
                    open++;
                }
            }
        }
        return open;
    }

    private static void digLake(WorldGenLevel level, BlockPos centre) {
        BlockState rock = centre.getY() < 0
            ? Blocks.DEEPSLATE.defaultBlockState()
            : Blocks.STONE.defaultBlockState();
        BlockState lava = Blocks.LAVA.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int groundTop = centre.getY();
        int pan = groundTop - LAKE_DEPTH;

        for (int dx = -WALL_RADIUS; dx <= WALL_RADIUS; dx++) {
            for (int dz = -WALL_RADIUS; dz <= WALL_RADIUS; dz++) {
                int distanceSqr = dx * dx + dz * dz;
                if (distanceSqr > WALL_RADIUS * WALL_RADIUS) {
                    continue;
                }
                int x = centre.getX() + dx;
                int z = centre.getZ() + dz;

                if (distanceSqr > LAKE_RADIUS * LAKE_RADIUS) {
                    for (int y = pan; y <= groundTop; y++) {
                        cursor.set(x, y, z);
                        if (isClear(level.getBlockState(cursor))) {
                            level.setBlock(cursor, rock, Block.UPDATE_CLIENTS);
                        }
                    }
                    continue;
                }

                cursor.set(x, pan, z);
                level.setBlock(cursor, rock, Block.UPDATE_CLIENTS);

                boolean island = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
                for (int y = pan + 1; y <= groundTop; y++) {
                    cursor.set(x, y, z);
                    level.setBlock(cursor, island ? rock : lava, Block.UPDATE_CLIENTS);
                }

                if (island) {
                    cursor.set(x, groundTop + 1, z);
                    level.setBlock(cursor, rock, Block.UPDATE_CLIENTS);
                    continue;
                }

                for (int dy = 1; dy <= MOAT_CLEARING; dy++) {
                    cursor.set(x, groundTop + dy, z);
                    if (!level.getBlockState(cursor).isAir()) {
                        level.setBlock(cursor, air, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private static boolean isClear(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }
}
