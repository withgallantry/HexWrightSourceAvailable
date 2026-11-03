package com.bluup.hexwright.server.bindstone;

import com.bluup.hexwright.server.block.BindstoneBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class BindstoneCube {

    public static final int MEMBERS = 27;

    public static final int SURFACE_CELLS = 54;

    public record Cell(Vec3i offset, Direction face, int member) {
    }

    public static final Vec3i[] MEMBERS_BY_INDEX = new Vec3i[MEMBERS];

    public static final Cell[] SURFACE = new Cell[SURFACE_CELLS];

    static {
        int cell = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Vec3i offset = new Vec3i(dx, dy, dz);
                    int member = memberIndex(dx, dy, dz);
                    MEMBERS_BY_INDEX[member] = offset;
                    if (dx != 0) {
                        SURFACE[cell++] = new Cell(offset, dx > 0 ? Direction.EAST : Direction.WEST, member);
                    }
                    if (dy != 0) {
                        SURFACE[cell++] = new Cell(offset, dy > 0 ? Direction.UP : Direction.DOWN, member);
                    }
                    if (dz != 0) {
                        SURFACE[cell++] = new Cell(offset, dz > 0 ? Direction.SOUTH : Direction.NORTH, member);
                    }
                }
            }
        }
    }

    private BindstoneCube() {
    }

    public static int memberIndex(int dx, int dy, int dz) {
        return (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1);
    }

    public static BlockPos memberPos(BlockPos middle, int member) {
        return middle.offset(MEMBERS_BY_INDEX[member]);
    }

    public static boolean isFormed(BlockGetter level, BlockPos middle) {
        for (int member = 0; member < MEMBERS; member++) {
            BlockState state = level.getBlockState(memberPos(middle, member));
            if (!(state.getBlock() instanceof BindstoneBlock) || state.getValue(BindstoneBlock.CORE)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isPowered(Level level, BlockPos middle) {
        for (int member = 0; member < MEMBERS; member++) {
            if (member == memberIndex(0, 0, 0)) {
                continue;
            }
            if (level.hasNeighborSignal(memberPos(middle, member))) {
                return true;
            }
        }
        return false;
    }

    public static BlockPos findNewHeart(Level level, BlockPos placed) {
        for (int member = 0; member < MEMBERS; member++) {
            BlockPos candidate = memberPos(placed, member);
            if (!isFormed(level, candidate) || containsHeart(level, candidate)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private static boolean containsHeart(BlockGetter level, BlockPos middle) {
        for (int member = 0; member < MEMBERS; member++) {
            BlockState state = level.getBlockState(memberPos(middle, member));
            if (state.getBlock() instanceof BindstoneBlock && state.getValue(BindstoneBlock.HEART)) {
                return true;
            }
        }
        return false;
    }

    public static void dissolveAround(Level level, BlockPos broken) {
        for (int member = 0; member < MEMBERS; member++) {
            BlockPos candidate = memberPos(broken, member);
            BlockState state = level.getBlockState(candidate);
            if (state.getBlock() instanceof BindstoneBlock && state.getValue(BindstoneBlock.HEART)) {
                BindstoneBlock.dissolveHeart(level, candidate, state);
            }
        }
    }
}
