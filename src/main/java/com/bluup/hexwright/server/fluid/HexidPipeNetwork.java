package com.bluup.hexwright.server.fluid;

import com.bluup.hexwright.server.block.HexwrightBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class HexidPipeNetwork {

    public static final int MAX_PIPES = 1024;

    public static final int SETTLE_PERIOD = 2;

    public static final long FLOW_PER_STEP = 200;

    private static boolean settling;

    private record Reach(BlockPos driver, List<BlockPos> columns) {
    }

    public static void spread(Level level, BlockPos pos) {
        if (level == null || level.isClientSide || settling) {
            return;
        }
        BlockPos bottom = HexidTankColumn.controllerPos(level, pos);
        int height = 1 + HexidTankColumn.above(level, bottom);
        BlockPos.MutableBlockPos cursor = bottom.mutable();
        for (int i = 0; i < height; i++, cursor.move(0, 1, 0)) {
            BlockState tank = level.getBlockState(cursor);
            for (Direction side : Direction.values()) {
                BlockPos next = cursor.relative(side);
                if (isPipe(level, next)
                    && HexidPipeBlock.jointWith(tank, side.getOpposite()) == PipeJoint.TANK) {
                    wake(level, next);
                }
            }
        }
    }

    public static void wake(Level level, BlockPos pos) {
        if (level == null || level.isClientSide || settling || !isPipe(level, pos)) {
            return;
        }
        level.scheduleTick(pos, HexwrightBlocks.HEXID_PIPE_BLOCK, SETTLE_PERIOD);
    }

    public static void settle(ServerLevel level, BlockPos pos) {
        if (settling || !isPipe(level, pos)) {
            return;
        }
        Reach reach = walk(level, pos);
        if (!pos.equals(reach.driver())) {
            level.scheduleTick(reach.driver(), HexwrightBlocks.HEXID_PIPE_BLOCK, SETTLE_PERIOD);
            return;
        }

        boolean moved;
        settling = true;
        try {
            moved = advance(resolve(level, reach.columns()));
        } finally {
            settling = false;
        }
        if (moved) {
            level.scheduleTick(pos, HexwrightBlocks.HEXID_PIPE_BLOCK, SETTLE_PERIOD);
        }
    }

    public static List<HexidTankBlockEntity> tanksOn(Level level, BlockPos pos) {
        if (level == null || !isPipe(level, pos)) {
            return List.of();
        }
        return resolve(level, walk(level, pos).columns());
    }

    private static Reach walk(Level level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        Set<BlockPos> columns = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        BlockPos driver = start;
        seen.add(start);
        queue.add(start);

        while (!queue.isEmpty() && seen.size() <= MAX_PIPES) {
            BlockPos pipe = queue.poll();
            for (Direction side : Direction.values()) {
                BlockPos next = pipe.relative(side);
                if (!level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.is(HexwrightBlocks.HEXID_PIPE_BLOCK)) {
                    if (seen.add(next)) {
                        queue.add(next);
                        if (next.compareTo(driver) < 0) {
                            driver = next.immutable();
                        }
                    }
                } else if (HexidPipeBlock.jointWith(state, side) == PipeJoint.TANK) {
                    columns.add(HexidTankColumn.controllerPos(level, next));
                }
            }
        }
        return new Reach(driver, List.copyOf(columns));
    }

    private static List<HexidTankBlockEntity> resolve(Level level, List<BlockPos> columns) {
        List<HexidTankBlockEntity> tanks = new ArrayList<>(columns.size());
        for (BlockPos pos : columns) {
            if (level.getBlockEntity(pos) instanceof HexidTankBlockEntity tank
                && !tank.isRemnantStore()) {
                tanks.add(tank);
            }
        }
        return tanks;
    }

    private static boolean advance(List<HexidTankBlockEntity> tanks) {
        int count = tanks.size();
        if (count < 2) {
            return false;
        }

        long[] amount = new long[count];
        long[] media = new long[count];
        long[] capacity = new long[count];
        long totalCapacity = 0;
        long totalAmount = 0;
        long totalMedia = 0;
        for (int i = 0; i < count; i++) {
            amount[i] = tanks.get(i).amountMb();
            media[i] = tanks.get(i).totalMedia();
            capacity[i] = tanks.get(i).capacityMb();
            totalCapacity += capacity[i];
            totalAmount += amount[i];
            totalMedia += media[i];
        }
        if (totalCapacity <= 0) {
            return false;
        }

        long[] byVolume = shares(totalAmount, capacity, totalCapacity);
        long[] byMedia = mediaFor(byVolume, totalAmount, totalMedia);
        long mediaBudget = totalAmount > 0
            ? Math.max(1, FLOW_PER_STEP * totalMedia / totalAmount)
            : 0;

        boolean moved = converge(amount, byVolume, FLOW_PER_STEP);
        moved |= converge(media, byMedia, mediaBudget);
        if (!moved) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            tanks.get(i).store(amount[i], media[i]);
        }
        return true;
    }

    private static long[] shares(long totalAmount, long[] capacity, long totalCapacity) {
        long[] share = new long[capacity.length];
        long unplaced = totalAmount;
        for (int i = 0; i < capacity.length; i++) {
            share[i] = Math.min(capacity[i], totalAmount * capacity[i] / totalCapacity);
            unplaced -= share[i];
        }
        for (int i = 0; i < capacity.length && unplaced > 0; i++) {
            long room = Math.min(unplaced, capacity[i] - share[i]);
            share[i] += room;
            unplaced -= room;
        }
        return share;
    }

    private static long[] mediaFor(long[] share, long totalAmount, long totalMedia) {
        long[] media = new long[share.length];
        long placed = 0;
        int fullest = 0;
        for (int i = 0; i < share.length; i++) {
            if (totalAmount > 0) {
                media[i] = totalMedia * share[i] / totalAmount;
                placed += media[i];
            }
            if (share[i] > share[fullest]) {
                fullest = i;
            }
        }
        media[fullest] += totalMedia - placed;
        return media;
    }

    private static boolean converge(long[] value, long[] target, long budget) {
        long surplus = 0;
        for (int i = 0; i < value.length; i++) {
            surplus += Math.max(0, value[i] - target[i]);
        }
        long moving = Math.min(budget, surplus);
        if (moving <= 0) {
            return false;
        }
        long[] taken = split(value, target, moving, true);
        long[] given = split(value, target, moving, false);
        for (int i = 0; i < value.length; i++) {
            value[i] += given[i] - taken[i];
        }
        return true;
    }

    private static long[] split(long[] value, long[] target, long moving, boolean fromSurplus) {
        long[] gap = new long[value.length];
        long total = 0;
        for (int i = 0; i < value.length; i++) {
            gap[i] = fromSurplus
                ? Math.max(0, value[i] - target[i])
                : Math.max(0, target[i] - value[i]);
            total += gap[i];
        }

        long[] part = new long[value.length];
        long placed = 0;
        for (int i = 0; i < value.length && total > 0; i++) {
            part[i] = gap[i] * moving / total;
            placed += part[i];
        }
        for (int i = 0; i < value.length && placed < moving; i++) {
            long room = Math.min(moving - placed, gap[i] - part[i]);
            part[i] += room;
            placed += room;
        }
        return part;
    }

    private static boolean isPipe(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).is(HexwrightBlocks.HEXID_PIPE_BLOCK);
    }

    private HexidPipeNetwork() {
    }
}
