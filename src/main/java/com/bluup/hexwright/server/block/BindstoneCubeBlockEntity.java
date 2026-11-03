package com.bluup.hexwright.server.block;

import com.bluup.hexwright.server.bindstone.BindstoneCube;
import com.bluup.hexwright.server.bindstone.BindstonePillar;
import com.bluup.hexwright.server.bindstone.BindstoneRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BindstoneCubeBlockEntity extends BlockEntity {

    private static final int CHECK_INTERVAL_TICKS = 20;

    private static final double WATCH_RADIUS = 48.0;

    private static final int RUNE_MIN_TICKS = 40;
    private static final int RUNE_MAX_TICKS = 120;

    private static final int GAP_MIN_TICKS = 8;
    private static final int GAP_MAX_TICKS = 30;

    private static final int MAX_LIT = 6;

    private final int[] litTicks = new int[BindstoneCube.MEMBERS];

    private int litCount;
    private int nextRuneIn = 1;
    private int checkIn = 1;

    private boolean formed;
    private boolean powered;
    private boolean watched;
    private boolean warding;

    private boolean tidy = true;

    public BindstoneCubeBlockEntity(BlockPos pos, BlockState state) {
        super(HexwrightBlocks.BINDSTONE_CUBE_BLOCK_ENTITY, pos, state);
    }

    void serverTick() {
        if (level == null) {
            return;
        }

        if (--checkIn <= 0) {
            checkIn = CHECK_INTERVAL_TICKS;
            formed = BindstoneCube.isFormed(level, worldPosition);
            if (!formed) {
                BindstoneBlock.dissolveHeart(level, worldPosition, getBlockState());
                return;
            }
            if (tidy) {
                tidy = false;
                clearEveryRune();
            }
            powered = BindstoneCube.isPowered(level, worldPosition);
            watched = level.hasNearbyAlivePlayer(
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5,
                WATCH_RADIUS
            );
        }

        if (powered != warding) {
            if (powered) {
                BindstoneRegistry.registerCube(level, worldPosition);
            } else {
                BindstoneRegistry.unregister(level, worldPosition);
            }
            warding = powered;
        }

        if (!powered || !watched) {
            extinguish();
            return;
        }

        RandomSource random = level.getRandom();
        for (int member = 0; member < litTicks.length; member++) {
            if (litTicks[member] > 0 && --litTicks[member] == 0) {
                setRune(member, 0, null);
                litCount--;
            }
        }

        if (--nextRuneIn > 0) {
            return;
        }
        nextRuneIn = GAP_MIN_TICKS + random.nextInt(GAP_MAX_TICKS - GAP_MIN_TICKS + 1);
        if (litCount >= MAX_LIT) {
            return;
        }

        BindstoneCube.Cell cell = BindstoneCube.SURFACE[random.nextInt(BindstoneCube.SURFACE_CELLS)];
        if (litTicks[cell.member()] > 0 || isBuried(cell)) {
            return;
        }
        if (setRune(cell.member(), 1 + random.nextInt(BindstonePillar.RUNE_COUNT), cell.face())) {
            litTicks[cell.member()] = RUNE_MIN_TICKS + random.nextInt(RUNE_MAX_TICKS - RUNE_MIN_TICKS + 1);
            litCount++;
        }
    }

    private boolean isBuried(BindstoneCube.Cell cell) {
        BlockPos outside = BindstoneCube.memberPos(worldPosition, cell.member()).relative(cell.face());
        return level != null && level.getBlockState(outside).isSolidRender(level, outside);
    }

    private boolean setRune(int member, int rune, Direction face) {
        if (level == null) {
            return false;
        }
        BlockPos pos = BindstoneCube.memberPos(worldPosition, member);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BindstoneBlock)) {
            return false;
        }
        BlockState lit = state.setValue(BindstoneBlock.RUNE, rune);
        if (face != null) {
            lit = lit.setValue(BindstoneBlock.FACING, face);
        }
        if (lit == state) {
            return true;
        }
        level.setBlock(pos, lit, Block.UPDATE_CLIENTS);
        return true;
    }

    public void extinguish() {
        if (litCount == 0) {
            return;
        }
        for (int member = 0; member < litTicks.length; member++) {
            if (litTicks[member] > 0) {
                litTicks[member] = 0;
                setRune(member, 0, null);
            }
        }
        litCount = 0;
    }

    private void clearEveryRune() {
        java.util.Arrays.fill(litTicks, 0);
        litCount = 0;
        for (int member = 0; member < litTicks.length; member++) {
            setRune(member, 0, null);
        }
    }

    private void stopWarding() {
        if (warding && level != null) {
            BindstoneRegistry.unregister(level, worldPosition);
            warding = false;
        }
    }

    @Override
    public void setRemoved() {
        stopWarding();
        super.setRemoved();
    }
}
