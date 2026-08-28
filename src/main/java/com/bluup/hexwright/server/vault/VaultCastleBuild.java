package com.bluup.hexwright.server.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;

public final class VaultCastleBuild implements VaultBuild {

    private static final int KEEP_X0 = 17;
    private static final int KEEP_Z0 = 16;
    private static final int KEEP_SIZE = 14;
    private static final int KEEP_X1 = KEEP_X0 + KEEP_SIZE - 1;
    private static final int KEEP_Z1 = KEEP_Z0 + KEEP_SIZE - 1;

    private static final int UPPER_FLOOR_Y = 17;
    private static final int ROOF_Y = 22;
    private static final int TOWER_TOP_Y = 26;

    private static final int TOWER_HALF = 2;

    private static final int GATE_X = 24;

    private static final int GROUND_Y = VaultGrounds.GROUND_Y;

    @Override
    public String id() {
        return "castle";
    }

    @Override
    public int groundsSize() {
        return 48;
    }

    @Override
    public int topY() {
        return TOWER_TOP_Y + 2;
    }

    @Override
    public Rect reserved() {
        return new Rect(KEEP_X0 - TOWER_HALF, KEEP_Z0 - TOWER_HALF,
            KEEP_X1 + TOWER_HALF, KEEP_Z1 + TOWER_HALF);
    }

    @Override
    public BlockPos entrance() {
        return new BlockPos(GATE_X, GROUND_Y, KEEP_Z0);
    }

    @Override
    public void place(ServerLevel level, BlockPos groundsMin, VaultRecord record, RandomSource random) {
        Plot plot = new Plot(level, groundsMin);
        BlockState floor = Blocks.STONE_BRICKS.defaultBlockState();

        for (int x = KEEP_X0 + 1; x < KEEP_X1; x++) {
            for (int z = KEEP_Z0 + 1; z < KEEP_Z1; z++) {
                plot.set(x, GROUND_Y, z, floor);
            }
        }

        for (int x = GATE_X - 4; x <= GATE_X + 3; x++) {
            for (int z = KEEP_Z0 - TOWER_HALF; z < KEEP_Z0; z++) {
                plot.set(x, GROUND_Y, z, ((x * 5 + z * 11) & 3) == 0
                    ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                    : Blocks.STONE_BRICKS.defaultBlockState());
            }
        }

        keepWalls(plot);
        keepRoof(plot);
        for (int[] corner : new int[][]{{KEEP_X0, KEEP_Z0}, {KEEP_X1, KEEP_Z0}, {KEEP_X0, KEEP_Z1}, {KEEP_X1, KEEP_Z1}}) {
            tower(plot, corner[0], corner[1]);
        }
        keepFloors(plot);
        gate(plot);
        fittings(plot);
    }

    private static void keepWalls(Plot plot) {
        for (int y = GROUND_Y + 1; y < ROOF_Y; y++) {
            for (int x = KEEP_X0; x <= KEEP_X1; x++) {
                for (int z = KEEP_Z0; z <= KEEP_Z1; z++) {
                    if (x != KEEP_X0 && x != KEEP_X1 && z != KEEP_Z0 && z != KEEP_Z1) {
                        continue;
                    }
                    plot.set(x, y, z, masonry(x, y, z));
                }
            }
        }
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        for (int storeyY : new int[]{GROUND_Y + 3, UPPER_FLOOR_Y + 2}) {
            for (int offset = 3; offset < KEEP_SIZE - 2; offset += 4) {
                for (int y = storeyY; y <= storeyY + 1; y++) {
                    plot.set(KEEP_X0 + offset, y, KEEP_Z0, pane);
                    plot.set(KEEP_X0 + offset, y, KEEP_Z1, pane);
                    plot.set(KEEP_X0, y, KEEP_Z0 + offset, pane);
                    plot.set(KEEP_X1, y, KEEP_Z0 + offset, pane);
                }
            }
        }
    }

    private static void keepRoof(Plot plot) {
        for (int x = KEEP_X0; x <= KEEP_X1; x++) {
            for (int z = KEEP_Z0; z <= KEEP_Z1; z++) {
                plot.set(x, ROOF_Y, z, masonry(x, ROOF_Y, z));
                boolean edge = x == KEEP_X0 || x == KEEP_X1 || z == KEEP_Z0 || z == KEEP_Z1;
                if (edge && ((x + z) & 1) == 0) {
                    plot.set(x, ROOF_Y + 1, z, Blocks.STONE_BRICKS.defaultBlockState());
                }
            }
        }
    }

    private static void tower(Plot plot, int cornerX, int cornerZ) {
        int signX = cornerX == KEEP_X0 ? 1 : -1;
        int signZ = cornerZ == KEEP_Z0 ? 1 : -1;
        int x0 = cornerX - TOWER_HALF;
        int z0 = cornerZ - TOWER_HALF;
        int x1 = cornerX + TOWER_HALF;
        int z1 = cornerZ + TOWER_HALF;

        for (int y = GROUND_Y; y <= TOWER_TOP_Y; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                    if (y == GROUND_Y) {
                        plot.set(x, y, z, Blocks.STONE_BRICKS.defaultBlockState());
                    } else if (edge) {
                        plot.set(x, y, z, masonry(x, y, z));
                    } else {
                        plot.set(x, y, z, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }

        for (int x = x0 + 1; x < x1; x++) {
            for (int z = z0 + 1; z < z1; z++) {
                for (int y : new int[]{UPPER_FLOOR_Y, ROOF_Y, TOWER_TOP_Y}) {
                    plot.set(x, y, z, Blocks.STONE_BRICKS.defaultBlockState());
                }
            }
        }

        int ladderX = cornerX - signX;
        int ladderZ = cornerZ - signZ;
        Direction ladderFacing = signX > 0 ? Direction.EAST : Direction.WEST;
        for (int y = GROUND_Y + 1; y < TOWER_TOP_Y; y++) {
            plot.set(ladderX, y, ladderZ,
                Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, ladderFacing));
        }
        plot.set(ladderX, TOWER_TOP_Y, ladderZ, Blocks.AIR.defaultBlockState());

        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (edge) {
                    plot.set(x, TOWER_TOP_Y + 1, z, Blocks.STONE_BRICKS.defaultBlockState());
                    if (((x + z) & 1) == 0) {
                        plot.set(x, TOWER_TOP_Y + 2, z, Blocks.STONE_BRICKS.defaultBlockState());
                    }
                }
            }
        }
        plot.set(cornerX, TOWER_TOP_Y + 1, cornerZ, Blocks.LANTERN.defaultBlockState());

        int doorX = cornerX + signX;
        int doorZ = cornerZ + signZ * TOWER_HALF;
        for (int y : new int[]{GROUND_Y + 1, GROUND_Y + 2, UPPER_FLOOR_Y + 1, UPPER_FLOOR_Y + 2,
            ROOF_Y + 1, ROOF_Y + 2}) {
            plot.set(doorX, y, doorZ, Blocks.AIR.defaultBlockState());
        }
    }

    private static void keepFloors(Plot plot) {
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        for (int x = KEEP_X0 + 1; x < KEEP_X1; x++) {
            for (int z = KEEP_Z0 + 1; z < KEEP_Z1; z++) {
                if (plot.isAir(x, UPPER_FLOOR_Y, z)) {
                    plot.set(x, UPPER_FLOOR_Y, z, planks);
                }
            }
        }
        int stairX = KEEP_X0 + 2;
        for (int step = 0; step < 5; step++) {
            int z = KEEP_Z1 - 3 - step;
            plot.set(stairX, GROUND_Y + 1 + step, z, Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.NORTH)
                .setValue(StairBlock.HALF, Half.BOTTOM));
            for (int y = GROUND_Y + 2 + step; y < UPPER_FLOOR_Y; y++) {
                plot.set(stairX, y, z, Blocks.AIR.defaultBlockState());
            }
            plot.set(stairX, UPPER_FLOOR_Y, z, step >= 3 ? Blocks.AIR.defaultBlockState() : planks);
        }
    }

    private static void gate(Plot plot) {
        int leftX = GATE_X - 1;
        int rightX = GATE_X;
        for (int x = leftX; x <= rightX; x++) {
            for (int y = GROUND_Y + 1; y <= GROUND_Y + 2; y++) {
                plot.set(x, y, KEEP_Z0, Blocks.AIR.defaultBlockState());
            }
        }
        plot.set(leftX, GROUND_Y + 1, KEEP_Z0, door(DoorHingeSide.RIGHT, DoubleBlockHalf.LOWER));
        plot.set(leftX, GROUND_Y + 2, KEEP_Z0, door(DoorHingeSide.RIGHT, DoubleBlockHalf.UPPER));
        plot.set(rightX, GROUND_Y + 1, KEEP_Z0, door(DoorHingeSide.LEFT, DoubleBlockHalf.LOWER));
        plot.set(rightX, GROUND_Y + 2, KEEP_Z0, door(DoorHingeSide.LEFT, DoubleBlockHalf.UPPER));
        for (int x : new int[]{leftX - 1, rightX + 1}) {
            plot.set(x, GROUND_Y + 3, KEEP_Z0 - 1, Blocks.WALL_TORCH.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        }
    }

    private static BlockState door(DoorHingeSide hinge, DoubleBlockHalf half) {
        return Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HINGE, hinge)
            .setValue(DoorBlock.HALF, half);
    }

    private static void fittings(Plot plot) {
        BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        for (int x : new int[]{KEEP_X0 + 4, KEEP_X1 - 4}) {
            for (int z : new int[]{KEEP_Z0 + 4, KEEP_Z1 - 4}) {
                plot.set(x, UPPER_FLOOR_Y - 1, z, hanging);
                plot.set(x, ROOF_Y - 1, z, hanging);
            }
        }

        int shelfZ = KEEP_Z1 - 1;
        for (int x = KEEP_X0 + 8; x <= KEEP_X1 - 3; x++) {
            plot.set(x, GROUND_Y + 1, shelfZ, Blocks.BOOKSHELF.defaultBlockState());
            plot.set(x, GROUND_Y + 2, shelfZ, Blocks.BOOKSHELF.defaultBlockState());
        }
        plot.set(KEEP_X0 + 5, GROUND_Y + 1, shelfZ, chest());
        plot.set(KEEP_X0 + 6, GROUND_Y + 1, shelfZ, chest());
        plot.set(KEEP_X0 + 4, GROUND_Y + 1, shelfZ, Blocks.CRAFTING_TABLE.defaultBlockState());
        plot.set(KEEP_X1 - 1, GROUND_Y + 1, KEEP_Z0 + 4,
            Blocks.FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));

        for (int x = KEEP_X0 + 5; x <= KEEP_X0 + 8; x++) {
            for (int z = KEEP_Z0 + 5; z <= KEEP_Z0 + 8; z++) {
                plot.set(x, GROUND_Y + 1, z, Blocks.RED_CARPET.defaultBlockState());
            }
        }
    }

    private static BlockState chest() {
        return Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
    }

    private static BlockState masonry(int x, int y, int z) {
        int hash = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        int bucket = Math.floorMod(hash, 16);
        if (bucket == 0) {
            return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        }
        if (bucket == 1) {
            return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        }
        return Blocks.STONE_BRICKS.defaultBlockState();
    }

    private record Plot(ServerLevel level, BlockPos min) {

        void set(int x, int y, int z, BlockState state) {
            level.setBlock(new BlockPos(min.getX() + x, y, min.getZ() + z), state, Block.UPDATE_CLIENTS);
        }

        boolean isAir(int x, int y, int z) {
            return level.getBlockState(new BlockPos(min.getX() + x, y, min.getZ() + z)).isAir();
        }
    }
}
