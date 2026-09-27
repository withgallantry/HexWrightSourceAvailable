package com.bluup.hexwright.server.cloak;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

public final class CloakFieldShell {

    public static final CloakBarrierBlock BARRIER = new CloakBarrierBlock(
        FabricBlockSettings.copyOf(Blocks.BARRIER)
            .dynamicShape()
            .isViewBlocking((state, level, pos) -> false)
            .isSuffocating((state, level, pos) -> false));

    private static final int LEGACY_SKIRT = 24;

    private CloakFieldShell() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("cloak_barrier"), BARRIER);
    }

    public static boolean isBarrier(BlockState state) {
        return state.is(BARRIER);
    }

    public static void raise(ServerLevel level, CloakField field) {
        BlockState barrier = BARRIER.defaultBlockState();
        forEachWallCell(level, field, pos -> {
            if (canHold(level.getBlockState(pos))) {
                level.setBlock(pos, barrier, Block.UPDATE_CLIENTS);
            }
        });
    }

    public static void lower(ServerLevel level, CloakField field) {
        int baseY = Mth.floor(field.center().y);
        forEachWallCell(level, field, pos -> {
            clear(level, pos);
            if (pos.getY() == baseY) {
                BlockPos.MutableBlockPos below = pos.mutable();
                for (int i = 0; i < LEGACY_SKIRT; i++) {
                    below.move(Direction.DOWN);
                    if (!clear(level, below)) {
                        break;
                    }
                }
            }
        });
    }

    private static boolean clear(ServerLevel level, BlockPos pos) {
        if (!isBarrier(level.getBlockState(pos))) {
            return false;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    private static boolean canHold(BlockState state) {
        return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }

    public static boolean holds(ServerLevel level, BlockPos pos) {
        for (CloakField field : CloakFieldStore.fieldsIn(level)) {
            if (onWall(field, pos.getX(), pos.getY(), pos.getZ())) {
                return true;
            }
        }
        return false;
    }

    private static void forEachWallCell(ServerLevel level, CloakField field, Consumer<BlockPos> action) {
        Vec3 c = field.center();
        int baseY = Mth.floor(c.y);
        int reach = Mth.ceil(field.radius()) + 1;
        int depth = Mth.ceil(field.height()) + 1;
        int bottom = Math.max(level.getMinBuildHeight(), baseY - depth);
        int top = Math.min(level.getMaxBuildHeight() - 1, baseY + depth);
        int cx = Mth.floor(c.x);
        int cz = Mth.floor(c.z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = cx - reach; x <= cx + reach; x++) {
            for (int z = cz - reach; z <= cz + reach; z++) {
                for (int y = bottom; y <= top; y++) {
                    if (onWall(field, x, y, z)) {
                        action.accept(pos.set(x, y, z));
                    }
                }
            }
        }
    }

    private static boolean onWall(CloakField field, int x, int y, int z) {
        if (!inside(field, x, y, z)) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            if (!inside(field, x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ())) {
                return true;
            }
        }
        return false;
    }

    private static boolean inside(CloakField field, int x, int y, int z) {
        return field.encloses(x + 0.5, y + 0.5, z + 0.5);
    }
}
