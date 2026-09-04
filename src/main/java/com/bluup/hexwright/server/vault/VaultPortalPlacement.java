package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.server.portal.PortalManager;
import com.bluup.hexwright.server.portal.PortalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class VaultPortalPlacement {

    private static final int PLACEMENT_DISTANCE = 2;

    private static final int SIDESTEP_RADIUS = 2;

    private static final int[] CANDIDATE_DY = {0, 1, -1, -2};

    private VaultPortalPlacement() {
    }

    public static @Nullable PortalWindow windowInFrontOf(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Direction facing = player.getDirection();

        BlockPos anchor = player.blockPosition();
        BlockState anchorState = level.getBlockState(anchor);
        if (!anchorState.isAir() && !anchorState.canBeReplaced()) {
            anchor = anchor.above();
        }
        return windowNear(level, anchor.relative(facing, PLACEMENT_DISTANCE), facing, SIDESTEP_RADIUS);
    }

    public static @Nullable PortalWindow windowAt(ServerLevel level, BlockPos base, Direction facing) {
        return windowAt(level, base, facing, true);
    }

    public static @Nullable PortalWindow windowAt(ServerLevel level, BlockPos base, Direction facing,
                                                  boolean requireFree) {
        Vec3i d = facing.getNormal();
        int ux = -d.getZ();
        int uz = d.getX();

        for (int dy : CANDIDATE_DY) {
            BlockPos first = base.above(dy);
            if (!paneClear(level, first, ux, uz) || !hasSturdyFloor(level, first, ux, uz)) {
                continue;
            }
            PortalWindow window = window(level, first, ux, uz, requireFree);
            if (window != null) {
                return window;
            }
        }

        if (paneClear(level, base, ux, uz)) {
            return window(level, base, ux, uz, requireFree);
        }
        return null;
    }

    public static @Nullable PortalWindow windowNear(ServerLevel level, BlockPos base, Direction facing,
                                                    int radius) {
        return windowNear(level, base, facing, radius, true);
    }

    public static @Nullable PortalWindow windowNear(ServerLevel level, BlockPos base, Direction facing,
                                                    int radius, boolean requireFree) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                candidates.add(base.offset(dx, 0, dz));
            }
        }
        candidates.sort(Comparator.comparingInt(pos -> pos.distManhattan(base)));
        for (BlockPos candidate : candidates) {
            PortalWindow window = windowAt(level, candidate, facing, requireFree);
            if (window != null) {
                return window;
            }
        }
        return null;
    }

    public static BlockPos anchorOf(PortalWindow window) {
        Vec3 first = window.origin().add(window.uHat().scale(0.5));
        return BlockPos.containing(first.x, window.origin().y + 0.5, first.z);
    }

    public static Direction facingOf(PortalWindow window) {
        Vec3 normal = window.normal();
        return Direction.getNearest(-normal.x, 0.0, -normal.z);
    }

    private static @Nullable PortalWindow window(ServerLevel level, BlockPos first, int ux, int uz,
                                                 boolean requireFree) {
        PortalWindow window = PortalWindow.fromCorners(
            Vec3.atCenterOf(first), Vec3.atCenterOf(first.offset(ux, 2, uz)));
        if (window == null) {
            return null;
        }
        if (requireFree && PortalManager.get(level).occupied(window)) {
            return null;
        }
        return window;
    }

    private static boolean paneClear(ServerLevel level, BlockPos first, int ux, int uz) {
        for (int column = 0; column < 2; column++) {
            for (int row = 0; row < 3; row++) {
                BlockPos pos = first.offset(ux * column, row, uz * column);
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && !state.canBeReplaced()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasSturdyFloor(ServerLevel level, BlockPos first, int ux, int uz) {
        for (int column = 0; column < 2; column++) {
            BlockPos floor = first.offset(ux * column, -1, uz * column);
            if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                return false;
            }
        }
        return true;
    }
}
