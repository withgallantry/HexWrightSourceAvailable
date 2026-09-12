package com.bluup.hexwright.server.fluid;

import com.bluup.hexwright.common.remnant.RemnantType;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

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

    public static final double REM_FLOW_PER_STEP = HexidTank.DRAMS_PER_BLOCK / 20.0;

    private static boolean settling;

    private record Reach(BlockPos driver, List<BlockPos> columns, List<BlockPos> liquefactriums,
                         boolean feedsMixer) {
    }

    public static final String CLASH_MIXED_VESSEL = "hexwright.hexid_pipe.clash";

    public static final String CLASH_MIXTURE_INPUT = "hexwright.hexid_pipe.mixture_input";

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
                if (isPipe(level, next) && HexidPipeBlock.jointWith(
                    level.getBlockState(next), tank, side.getOpposite()) == PipeJoint.TANK) {
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

    public static @Nullable String settle(ServerLevel level, BlockPos pos) {
        if (settling || !isPipe(level, pos)) {
            return null;
        }
        Reach reach = walk(level, pos);
        List<HexidTankBlockEntity> tanks = resolve(level, reach.columns());
        String clash = clashReason(tanks, reach.feedsMixer());
        if (clash != null) {
            return clash;
        }
        if (!pos.equals(reach.driver())) {
            level.scheduleTick(reach.driver(), HexwrightBlocks.HEXID_PIPE_BLOCK, SETTLE_PERIOD);
            return null;
        }

        boolean suspension = carriesSuspension(tanks);
        boolean moved;
        settling = true;
        try {
            moved = suspension
                ? advanceSuspension(vessels(tanks, true))
                : advance(vessels(tanks, false));
        } finally {
            settling = false;
        }
        if (moved) {
            level.scheduleTick(pos, HexwrightBlocks.HEXID_PIPE_BLOCK, SETTLE_PERIOD);
        }
        return null;
    }

    public static List<HexidTankBlockEntity> tanksOn(Level level, BlockPos pos) {
        if (level == null || !isPipe(level, pos)) {
            return List.of();
        }
        return resolve(level, walk(level, pos).columns());
    }

    public static List<LiquefactriumBlockEntity> liquefactriumsOn(Level level, BlockPos pos) {
        if (level == null || !isPipe(level, pos)) {
            return List.of();
        }
        List<LiquefactriumBlockEntity> found = new ArrayList<>();
        for (BlockPos liquefactrium : walk(level, pos).liquefactriums()) {
            if (level.getBlockEntity(liquefactrium) instanceof LiquefactriumBlockEntity be) {
                found.add(be);
            }
        }
        return found;
    }

    public static boolean isSuspension(List<HexidTankBlockEntity> tanks) {
        return carriesSuspension(tanks);
    }

    public static List<HexidTankBlockEntity> sharing(Level level, BlockPos pos) {
        if (level == null || level.isClientSide) {
            return List.of();
        }
        BlockPos bottom = HexidTankColumn.controllerPos(level, pos);
        int height = 1 + HexidTankColumn.above(level, bottom);
        Set<BlockPos> columns = new LinkedHashSet<>();
        BlockPos.MutableBlockPos cursor = bottom.mutable();
        for (int i = 0; i < height; i++, cursor.move(0, 1, 0)) {
            BlockState tank = level.getBlockState(cursor);
            for (Direction side : Direction.values()) {
                BlockPos next = cursor.relative(side);
                if (isPipe(level, next) && HexidPipeBlock.jointWith(
                    level.getBlockState(next), tank, side.getOpposite()) == PipeJoint.TANK) {
                    columns.addAll(walk(level, next).columns());
                }
            }
        }
        return resolve(level, List.copyOf(columns));
    }

    private static Reach walk(Level level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        Set<BlockPos> columns = new LinkedHashSet<>();
        Set<BlockPos> liquefactriums = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        BlockPos driver = start;
        boolean feedsMixer = false;
        seen.add(start);
        queue.add(start);

        while (!queue.isEmpty() && seen.size() <= MAX_PIPES) {
            BlockPos pipe = queue.poll();
            BlockState here = level.getBlockState(pipe);
            for (Direction side : Direction.values()) {
                BlockPos next = pipe.relative(side);
                if (!level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                PipeJoint joint = HexidPipeBlock.jointWith(here, state, side);
                if (joint == PipeJoint.PIPE) {
                    if (seen.add(next)) {
                        queue.add(next);
                        if (next.compareTo(driver) < 0) {
                            driver = next.immutable();
                        }
                    }
                } else if (joint == PipeJoint.TANK) {
                    if (state.is(HexwrightBlocks.ALEMBIX_BLOCK)) {
                        feedsMixer |= side.getAxis().isHorizontal();
                    } else if (state.is(HexwrightBlocks.LIQUEFACTRIUM_BLOCK)) {
                        liquefactriums.add(next.immutable());
                    } else if (HexidTankColumn.isTank(state)) {
                        columns.add(HexidTankColumn.controllerPos(level, next));
                    }
                }
            }
        }
        return new Reach(driver, List.copyOf(columns), List.copyOf(liquefactriums), feedsMixer);
    }

    private static List<HexidTankBlockEntity> resolve(Level level, List<BlockPos> columns) {
        List<HexidTankBlockEntity> tanks = new ArrayList<>(columns.size());
        for (BlockPos pos : columns) {
            if (level.getBlockEntity(pos) instanceof HexidTankBlockEntity tank) {
                tanks.add(tank);
            }
        }
        return tanks;
    }

    private static boolean carriesSuspension(List<HexidTankBlockEntity> tanks) {
        for (HexidTankBlockEntity tank : tanks) {
            if (tank.isRemnantStore()) {
                return true;
            }
        }
        return false;
    }

    private static List<HexidTankBlockEntity> vessels(List<HexidTankBlockEntity> tanks, boolean suspension) {
        List<HexidTankBlockEntity> kept = new ArrayList<>(tanks.size());
        for (HexidTankBlockEntity tank : tanks) {
            if (suspension ? !tank.holdsFluid() : !tank.isRemnantStore()) {
                kept.add(tank);
            }
        }
        return kept;
    }

    public static @Nullable String clashReason(Level level, BlockPos pos) {
        if (level == null || level.isClientSide || !isPipe(level, pos)) {
            return null;
        }
        Reach reach = walk(level, pos);
        return clashReason(resolve(level, reach.columns()), reach.feedsMixer());
    }

    private static @Nullable String clashReason(List<HexidTankBlockEntity> tanks, boolean feedsMixer) {
        boolean fluid = false;
        Set<RemnantType> kinds = null;
        for (HexidTankBlockEntity tank : tanks) {
            if (tank.isRemnantStore()) {
                Set<RemnantType> here = tank.remnants().types();
                if (kinds != null && !kinds.equals(here)) {
                    return CLASH_MIXED_VESSEL;
                }
                kinds = here;
            } else if (tank.holdsFluid()) {
                fluid = true;
            }
        }
        if (fluid && kinds != null) {
            return CLASH_MIXED_VESSEL;
        }
        if (feedsMixer && kinds != null && kinds.size() > 1) {
            return CLASH_MIXTURE_INPUT;
        }
        return null;
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

    private static boolean advanceSuspension(List<HexidTankBlockEntity> tanks) {
        int count = tanks.size();
        if (count < 2) {
            return false;
        }

        double[] held = new double[count];
        double[] target = new double[count];
        double totalHeld = 0;
        double totalCapacity = 0;
        for (int i = 0; i < count; i++) {
            held[i] = tanks.get(i).remnants().total();
            target[i] = tanks.get(i).remnantCapacity();
            totalHeld += held[i];
            totalCapacity += target[i];
        }
        if (totalHeld <= 0 || totalCapacity <= 0) {
            return false;
        }

        double surplus = 0;
        for (int i = 0; i < count; i++) {
            target[i] = totalHeld * target[i] / totalCapacity;
            surplus += Math.max(0, held[i] - target[i]);
        }
        double moving = Math.min(REM_FLOW_PER_STEP, surplus);
        if (moving < TankRemnants.MIN_DRAMS) {
            return false;
        }

        TankRemnants pooled = TankRemnants.EMPTY;
        for (int i = 0; i < count; i++) {
            double over = held[i] - target[i];
            if (over <= 0) {
                continue;
            }
            pooled = pooled.plusAll(tanks.get(i).drawMixture(moving * over / surplus));
        }
        if (pooled.isEmpty()) {
            return false;
        }

        double deficit = 0;
        int last = -1;
        for (int i = 0; i < count; i++) {
            if (target[i] > held[i]) {
                deficit += target[i] - held[i];
                last = i;
            }
        }
        TankRemnants source = pooled;
        for (int i = 0; i < count && !pooled.isEmpty(); i++) {
            if (target[i] <= held[i]) {
                continue;
            }
            TankRemnants share = i == last ? pooled : source.portion((target[i] - held[i]) / deficit);
            if (share.total() > pooled.total()) {
                share = pooled;
            }
            double poured = tanks.get(i).pourMixture(share);
            if (poured <= 0) {
                continue;
            }
            pooled = pooled.minusAll(
                poured >= share.total() ? share : share.portion(poured / share.total()));
        }
        for (int i = 0; i < count && !pooled.isEmpty(); i++) {
            double poured = tanks.get(i).pourMixture(pooled);
            if (poured > 0) {
                pooled = pooled.portion(1.0 - poured / pooled.total());
            }
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
