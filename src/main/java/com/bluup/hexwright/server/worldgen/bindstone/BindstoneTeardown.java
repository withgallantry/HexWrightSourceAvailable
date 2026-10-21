package com.bluup.hexwright.server.worldgen.bindstone;

import com.bluup.hexwright.server.bindstone.BindstonePillar;
import com.bluup.hexwright.server.worldgen.dungeon.PlayerBuilds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class BindstoneTeardown {

    private static final int BULK_LIMIT = 32;

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final int LAKE_RADIUS = BindstonePillarStructure.LAKE_RADIUS;
    private static final int LAKE_DEPTH = BindstonePillarStructure.LAKE_DEPTH;
    private static final int MOAT_CLEARING = BindstonePillarPiece.MOAT_CLEARING;

    private BindstoneTeardown() {
    }

    static @Nullable String builtIn(ServerLevel level, BlockPos centre) {
        BlockPos origin = centre.above(2);
        int bulk = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -LAKE_RADIUS; dx <= LAKE_RADIUS; dx++) {
            for (int dz = -LAKE_RADIUS; dz <= LAKE_RADIUS; dz++) {
                if (dx * dx + dz * dz > LAKE_RADIUS * LAKE_RADIUS) {
                    continue;
                }
                for (int y = centre.getY() + 1; y <= centre.getY() + MOAT_CLEARING; y++) {
                    pos.set(centre.getX() + dx, y, centre.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.getBlock() instanceof LiquidBlock
                        || laidBySite(level, state, pos, centre, origin)) {
                        continue;
                    }
                    if (!state.is(PlayerBuilds.DEBRIS) && !state.is(PlayerBuilds.NATURAL)) {
                        return PlayerBuilds.describe(state, pos.immutable());
                    }
                    if (state.isCollisionShapeFullBlock(level, pos)) {
                        bulk++;
                    }
                }
            }
        }
        List<Entity> kept = level.getEntities((Entity) null, aboveFloor(centre),
            entity -> inBasin(centre, entity.blockPosition()) && PlayerBuilds.placedByPlayer(entity));
        if (!kept.isEmpty()) {
            return PlayerBuilds.describe(kept.get(0));
        }
        return bulk > BULK_LIMIT ? bulk + " blocks set down around it" : null;
    }

    static void tearDown(ServerLevel level, BlockPos centre) {
        BlockState rock = BindstonePillarPiece.rock(centre);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos origin = centre.above(2);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -LAKE_RADIUS; dx <= LAKE_RADIUS; dx++) {
            for (int dz = -LAKE_RADIUS; dz <= LAKE_RADIUS; dz++) {
                if (dx * dx + dz * dz > LAKE_RADIUS * LAKE_RADIUS) {
                    continue;
                }
                for (int y = centre.getY() - LAKE_DEPTH + 1; y <= centre.getY(); y++) {
                    pos.set(centre.getX() + dx, y, centre.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof LiquidBlock || state.is(PlayerBuilds.DEBRIS)) {
                        level.setBlock(pos, rock, FLAGS);
                    }
                }
                for (int y = centre.getY() + 1; y <= centre.getY() + MOAT_CLEARING; y++) {
                    pos.set(centre.getX() + dx, y, centre.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    if (state.getBlock() instanceof LiquidBlock || state.is(PlayerBuilds.DEBRIS)
                        || laidBySite(level, state, pos, centre, origin)) {
                        level.setBlock(pos, air, FLAGS);
                    }
                }
            }
        }
    }

    private static boolean laidBySite(ServerLevel level, BlockState state, BlockPos pos, BlockPos centre,
                                      BlockPos origin) {
        if (BindstonePillar.occupies(origin, pos) && BindstonePillar.isPillarBlock(state)) {
            return true;
        }
        if (pos.equals(origin) && state.is(Blocks.CHEST)) {
            return !(level.getBlockEntity(pos) instanceof Container chest) || chest.isEmpty();
        }
        return state.is(PlayerBuilds.NATURAL) && pos.getY() == centre.getY() + 1
            && Math.abs(pos.getX() - centre.getX()) <= 2 && Math.abs(pos.getZ() - centre.getZ()) <= 2;
    }

    private static boolean inBasin(BlockPos centre, BlockPos pos) {
        int dx = pos.getX() - centre.getX();
        int dz = pos.getZ() - centre.getZ();
        return dx * dx + dz * dz <= LAKE_RADIUS * LAKE_RADIUS
            && pos.getY() > centre.getY() && pos.getY() <= centre.getY() + MOAT_CLEARING;
    }

    private static AABB aboveFloor(BlockPos centre) {
        return new AABB(
            centre.getX() - LAKE_RADIUS, centre.getY() + 1, centre.getZ() - LAKE_RADIUS,
            centre.getX() + LAKE_RADIUS + 1, centre.getY() + MOAT_CLEARING + 1, centre.getZ() + LAKE_RADIUS + 1);
    }
}
