package com.bluup.hexwright.server.worldgen.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DungeonFittings {

    private static final int MAX_RISE = 30;

    private static final int FIXTURE_MARGIN = 5;

    private static final int MINIBOSS_MARGIN = 7;

    private static final int MINIBOSS_HEADROOM = 3;

    private static final Block[] LINING = {
        Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS,
        Blocks.CRACKED_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
        Blocks.MOSSY_STONE_BRICKS, Blocks.COBBLESTONE,
    };

    private static final Block[] COLLAR = {
        Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.COBBLESTONE,
        Blocks.STONE_BRICKS, Blocks.MOSSY_COBBLESTONE,
    };

    private DungeonFittings() {
    }

    static boolean carveCaveTap(ServerLevelAccessor level, BoundingBox box, RandomSource random,
                                BlockPos column, int roomCeiling, int moduleTop) {
        int caveFloor = -1;
        for (int y = moduleTop + 1; y <= moduleTop + MAX_RISE; y++) {
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            if (!box.isInside(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                if (level.getBlockState(pos.above()).isAir()) {
                    caveFloor = y;
                    break;
                }
            } else if (!state.getFluidState().isEmpty()) {
                return false;
            }
        }
        if (caveFloor < 0) {
            return false;
        }

        Direction ladderFace = Direction.from2DDataValue(Math.floorMod(column.getX() + column.getZ(), 4));
        for (int y = roomCeiling; y < caveFloor; y++) {
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            if (!box.isInside(pos)) {
                continue;
            }
            line(level, box, random, pos, ladderFace);
            level.setBlock(pos, Blocks.LADDER.defaultBlockState()
                .setValue(LadderBlock.FACING, ladderFace), Block.UPDATE_CLIENTS);
        }

        collar(level, box, random, column, caveFloor);
        return true;
    }

    private static void line(ServerLevelAccessor level, BoundingBox box, RandomSource random,
                             BlockPos pos, Direction ladderFace) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighbour = pos.relative(side);
            if (!box.isInside(neighbour)) {
                continue;
            }
            BlockState state = level.getBlockState(neighbour);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                if (side == ladderFace.getOpposite()) {
                    level.setBlock(neighbour, LINING[random.nextInt(LINING.length)].defaultBlockState(),
                        Block.UPDATE_CLIENTS);
                }
                continue;
            }
            if (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.TUFF)
                || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.DIRT)) {
                level.setBlock(neighbour, LINING[random.nextInt(LINING.length)].defaultBlockState(),
                    Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void collar(ServerLevelAccessor level, BoundingBox box, RandomSource random,
                               BlockPos column, int caveFloor) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int distance = Math.max(Math.abs(dx), Math.abs(dz));
                if (distance == 0) {
                    continue;
                }
                BlockPos rim = new BlockPos(column.getX() + dx, caveFloor - 1, column.getZ() + dz);
                if (!box.isInside(rim)) {
                    continue;
                }
                if (distance == 2 && random.nextInt(3) != 0) {
                    continue;
                }
                BlockState state = level.getBlockState(rim);
                if (state.isAir() || !state.getFluidState().isEmpty()) {
                    continue;
                }
                level.setBlock(rim, COLLAR[random.nextInt(COLLAR.length)].defaultBlockState(),
                    Block.UPDATE_CLIENTS);
            }
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos rubble = new BlockPos(column.getX(), caveFloor, column.getZ()).relative(side);
            if (!box.isInside(rubble) || random.nextInt(3) == 0) {
                continue;
            }
            if (level.getBlockState(rubble).isAir()
                && level.getBlockState(rubble.below()).isFaceSturdy(level, rubble.below(), Direction.UP)) {
                level.setBlock(rubble, COLLAR[random.nextInt(COLLAR.length)].defaultBlockState(),
                    Block.UPDATE_CLIENTS);
            }
        }
    }

    static List<BlockPos> fixtureSpots(Set<BlockPos> air, BoundingBox module) {
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos pos : air) {
            if (pos.getX() - module.minX() < FIXTURE_MARGIN || module.maxX() - pos.getX() < FIXTURE_MARGIN
                || pos.getZ() - module.minZ() < FIXTURE_MARGIN || module.maxZ() - pos.getZ() < FIXTURE_MARGIN) {
                continue;
            }
            if (air.contains(pos.below()) || !air.contains(pos.above()) || !air.contains(pos.above(2))) {
                continue;
            }
            boolean backed = false;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (!air.contains(pos.relative(side))) {
                    backed = true;
                    break;
                }
            }
            if (backed) {
                spots.add(pos);
            }
        }
        spots.sort(DungeonFittings::compare);
        return spots;
    }

    static List<BlockPos> trapSpots(Set<BlockPos> air, BoundingBox module) {
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos pos : air) {
            if (pos.getX() - module.minX() < FIXTURE_MARGIN || module.maxX() - pos.getX() < FIXTURE_MARGIN
                || pos.getZ() - module.minZ() < FIXTURE_MARGIN || module.maxZ() - pos.getZ() < FIXTURE_MARGIN) {
                continue;
            }
            if (air.contains(pos.below()) || !air.contains(pos.above()) || !air.contains(pos.above(2))) {
                continue;
            }
            boolean open = true;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (!air.contains(pos.relative(side))) {
                    open = false;
                    break;
                }
            }
            if (open) {
                spots.add(pos.below());
            }
        }
        spots.sort(DungeonFittings::compare);
        return spots;
    }

    static BlockPos minibossSpot(Set<BlockPos> air, BoundingBox module) {
        double centreX = (module.minX() + module.maxX()) / 2.0;
        double centreZ = (module.minZ() + module.maxZ()) / 2.0;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : air) {
            if (pos.getX() - module.minX() < MINIBOSS_MARGIN || module.maxX() - pos.getX() < MINIBOSS_MARGIN
                || pos.getZ() - module.minZ() < MINIBOSS_MARGIN || module.maxZ() - pos.getZ() < MINIBOSS_MARGIN) {
                continue;
            }
            if (air.contains(pos.below())) {
                continue;
            }
            boolean clear = true;
            for (int rise = 1; rise <= MINIBOSS_HEADROOM && clear; rise++) {
                clear = air.contains(pos.above(rise));
            }
            if (!clear) {
                continue;
            }
            double dx = pos.getX() + 0.5 - centreX;
            double dz = pos.getZ() + 0.5 - centreZ;
            double distance = dx * dx + dz * dz;
            if (best == null || distance < bestDistance
                || (distance == bestDistance && compare(pos, best) < 0)) {
                best = pos;
                bestDistance = distance;
            }
        }
        return best;
    }

    static BlockPos tapColumn(Set<BlockPos> air, BoundingBox module) {
        BlockPos best = null;
        for (BlockPos pos : air) {
            if (pos.getX() - module.minX() < FIXTURE_MARGIN || module.maxX() - pos.getX() < FIXTURE_MARGIN
                || pos.getZ() - module.minZ() < FIXTURE_MARGIN || module.maxZ() - pos.getZ() < FIXTURE_MARGIN) {
                continue;
            }
            if (air.contains(pos.above()) || !air.contains(pos.below()) || !air.contains(pos.below(2))) {
                continue;
            }
            if (best == null || pos.getY() > best.getY()
                || (pos.getY() == best.getY() && compare(pos, best) < 0)) {
                best = pos;
            }
        }
        return best;
    }

    static BlockPos anchorSpot(Set<BlockPos> air, BoundingBox module, BlockPos column, BlockPos trap) {
        BlockPos foot = shaftFoot(air, column);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : air) {
            if (pos.getX() - module.minX() < FIXTURE_MARGIN || module.maxX() - pos.getX() < FIXTURE_MARGIN
                || pos.getZ() - module.minZ() < FIXTURE_MARGIN || module.maxZ() - pos.getZ() < FIXTURE_MARGIN) {
                continue;
            }
            if (air.contains(pos.below()) || !air.contains(pos.above()) || !air.contains(pos.above(2))) {
                continue;
            }
            if (trap != null && trap.equals(pos.below())) {
                continue;
            }
            if (!standingRoom(air, pos)) {
                continue;
            }
            double dx = pos.getX() - foot.getX();
            double dy = pos.getY() - foot.getY();
            double dz = pos.getZ() - foot.getZ();
            double distance = dx * dx + dy * dy + dz * dz;
            if (best == null || distance < bestDistance
                || (distance == bestDistance && compare(pos, best) < 0)) {
                best = pos;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static BlockPos shaftFoot(Set<BlockPos> air, BlockPos column) {
        BlockPos foot = column;
        while (air.contains(foot.below())) {
            foot = foot.below();
        }
        return foot;
    }

    private static boolean standingRoom(Set<BlockPos> air, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!air.contains(pos.relative(side)) || !air.contains(pos.above().relative(side))) {
                return false;
            }
        }
        return true;
    }

    static Set<BlockPos> roomAir(Set<BlockPos> air) {
        List<BlockPos> seeds = new ArrayList<>(air);
        seeds.sort(DungeonFittings::compare);
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> best = Set.of();
        for (BlockPos seed : seeds) {
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
                    if (air.contains(next) && visited.add(next)) {
                        pocket.add(next);
                        queue.add(next);
                    }
                }
            }
            if (pocket.size() > best.size()) {
                best = pocket;
            }
        }
        return best;
    }

    static int compare(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX()) {
            return Integer.compare(a.getX(), b.getX());
        }
        if (a.getY() != b.getY()) {
            return Integer.compare(a.getY(), b.getY());
        }
        return Integer.compare(a.getZ(), b.getZ());
    }

    static void placeFixture(ServerLevelAccessor level, DungeonModules.Fixture fixture,
                             BlockPos pos, Set<BlockPos> air) {
        Direction facing = Direction.NORTH;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!air.contains(pos.relative(side))) {
                facing = side.getOpposite();
                break;
            }
        }
        BlockState state = fixture.block().defaultBlockState();
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            state = state.setValue(HorizontalDirectionalBlock.FACING, facing);
        }
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
