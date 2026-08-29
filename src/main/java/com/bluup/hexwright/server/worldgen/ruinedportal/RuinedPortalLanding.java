package com.bluup.hexwright.server.worldgen.ruinedportal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class RuinedPortalLanding {

    private static final int RADIUS = 12;
    private static final int RISE = 8;

    private static final int ROOM_FLOOR = 96;

    private RuinedPortalLanding() {
    }

    static BlockPos onAnchor(ServerLevel level, BlockPos anchor) {
        BlockPos preferred = anchor.above();
        int minY = Math.max(level.getMinBuildHeight(), anchor.getY() - RISE);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, anchor.getY() + RISE);
        Set<BlockPos> open = openBlocks(level, anchor, minY, maxY);

        Set<BlockPos> best = null;
        int bestFloor = -1;
        boolean bestLeadsOut = false;
        for (Set<BlockPos> pocket : pockets(open)) {
            List<BlockPos> floor = standingSpots(level, pocket);
            boolean out = leadsOut(pocket, anchor, minY, maxY);
            if (pocket.contains(preferred) && standable(level, pocket, preferred)
                && floor.size() >= ROOM_FLOOR && out) {
                return preferred;
            }
            if (floor.isEmpty()) {
                continue;
            }
            if (best == null || (out && !bestLeadsOut)
                || (out == bestLeadsOut && floor.size() > bestFloor)) {
                best = pocket;
                bestFloor = floor.size();
                bestLeadsOut = out;
            }
        }

        BlockPos landing = best == null ? null : nearest(standingSpots(level, best), anchor);
        return landing == null ? preferred : landing;
    }

    private static Set<BlockPos> openBlocks(ServerLevel level, BlockPos anchor, int minY, int maxY) {
        Set<BlockPos> open = new HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = anchor.getX() - RADIUS; x <= anchor.getX() + RADIUS; x++) {
            for (int z = anchor.getZ() - RADIUS; z <= anchor.getZ() + RADIUS; z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (!state.blocksMotion() && state.getFluidState().isEmpty()) {
                        open.add(cursor.immutable());
                    }
                }
            }
        }
        return open;
    }

    private static List<Set<BlockPos>> pockets(Set<BlockPos> open) {
        List<Set<BlockPos>> pockets = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos seed : open) {
            if (!visited.add(seed)) {
                continue;
            }
            Set<BlockPos> pocket = new HashSet<>();
            pocket.add(seed);
            Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(seed);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.removeFirst();
                for (Direction side : Direction.values()) {
                    BlockPos next = pos.relative(side);
                    if (open.contains(next) && visited.add(next)) {
                        pocket.add(next);
                        queue.add(next);
                    }
                }
            }
            pockets.add(pocket);
        }
        return pockets;
    }

    private static boolean leadsOut(Set<BlockPos> pocket, BlockPos anchor, int minY, int maxY) {
        for (BlockPos pos : pocket) {
            if (Math.abs(pos.getX() - anchor.getX()) == RADIUS || Math.abs(pos.getZ() - anchor.getZ()) == RADIUS
                || pos.getY() == minY || pos.getY() == maxY) {
                return true;
            }
        }
        return false;
    }

    private static List<BlockPos> standingSpots(ServerLevel level, Set<BlockPos> pocket) {
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos pos : pocket) {
            if (standable(level, pocket, pos)) {
                spots.add(pos);
            }
        }
        return spots;
    }

    private static boolean standable(ServerLevel level, Set<BlockPos> open, BlockPos pos) {
        if (!open.contains(pos) || !open.contains(pos.above())) {
            return false;
        }
        BlockPos floor = pos.below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    private static BlockPos nearest(List<BlockPos> spots, BlockPos anchor) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : spots) {
            double distance = pos.distSqr(anchor);
            if (best == null || distance < bestDistance
                || (distance == bestDistance && compare(pos, best) < 0)) {
                best = pos;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static int compare(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX()) {
            return Integer.compare(a.getX(), b.getX());
        }
        if (a.getY() != b.getY()) {
            return Integer.compare(a.getY(), b.getY());
        }
        return Integer.compare(a.getZ(), b.getZ());
    }
}
