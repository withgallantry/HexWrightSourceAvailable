package com.bluup.hexwright.server.fluid;

import com.bluup.hexwright.server.block.HexwrightBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class HexidTankColumn {

    public static boolean isTank(BlockState state) {
        return state.is(HexwrightBlocks.HEXID_TANK_BLOCK);
    }

    private static int run(BlockGetter level, BlockPos pos, int step) {
        int found = 0;
        BlockPos.MutableBlockPos cursor = pos.mutable();
        while (found < HexidTank.MAX_HEIGHT) {
            cursor.move(0, step, 0);
            if (!isTank(level.getBlockState(cursor))) {
                break;
            }
            found++;
        }
        return found;
    }

    public static int below(BlockGetter level, BlockPos pos) {
        return run(level, pos, -1);
    }

    public static int above(BlockGetter level, BlockPos pos) {
        return run(level, pos, 1);
    }

    public static BlockPos controllerPos(BlockGetter level, BlockPos pos) {
        return pos.below(below(level, pos));
    }

    public static int height(BlockGetter level, BlockPos pos) {
        return below(level, pos) + 1 + above(level, pos);
    }

    @Nullable
    public static HexidTankBlockEntity controller(BlockGetter level, BlockPos pos) {
        if (!isTank(level.getBlockState(pos))) {
            return null;
        }
        return level.getBlockEntity(controllerPos(level, pos)) instanceof HexidTankBlockEntity tank
            ? tank
            : null;
    }

    public static void normalise(Level level, BlockPos pos) {
        if (level.isClientSide || !isTank(level.getBlockState(pos))) {
            return;
        }
        BlockPos bottom = controllerPos(level, pos);
        int height = 1 + above(level, bottom);

        long amount = 0;
        long media = 0;
        TankRemnants remnants = TankRemnants.EMPTY;
        HexidTankBlockEntity owner = null;
        BlockPos.MutableBlockPos cursor = bottom.mutable();
        for (int i = 0; i < height; i++, cursor.move(0, 1, 0)) {
            if (level.getBlockEntity(cursor) instanceof HexidTankBlockEntity tank) {
                amount += tank.amountMb();
                media += tank.totalMedia();
                remnants = remnants.plusAll(tank.remnants());
                if (owner == null) {
                    owner = tank;
                } else {
                    tank.clear();
                }
            }
        }
        if (owner == null) {
            return;
        }

        if (!remnants.isEmpty()) {
            owner.clear();
            owner.storeRemnants(remnants.cappedTo(HexidTank.dramCapacity(height)));
            return;
        }

        long kept = Math.min(amount, HexidTank.capacityMb(height));
        owner.store(kept, kept == amount ? media : media * kept / Math.max(1, amount));
    }

    public static void split(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        int lowerHeight = below(level, pos);
        int upperHeight = above(level, pos);

        BlockPos ownerPos = lowerHeight > 0 ? pos.below(lowerHeight) : pos;
        if (!(level.getBlockEntity(ownerPos) instanceof HexidTankBlockEntity owner)) {
            return;
        }

        if (owner.isRemnantStore()) {
            splitRemnants(level, pos, owner, lowerHeight, upperHeight);
            return;
        }

        long amount = owner.amountMb();
        long total = owner.totalMedia();
        if (amount <= 0 && total <= 0) {
            return;
        }
        int density = owner.mediaPerMb();

        long toLower = Math.min(amount, HexidTank.capacityMb(lowerHeight));
        long toUpper = Math.min(amount - toLower, HexidTank.capacityMb(upperHeight));
        long lowerMedia = HexidTank.totalMedia(toLower, density);
        long upperMedia = HexidTank.totalMedia(toUpper, density);
        long spilledMedia = HexidTank.totalMedia(amount - toLower - toUpper, density);

        long leftover = Math.max(0, total - lowerMedia - upperMedia - spilledMedia);
        if (lowerHeight > 0) {
            lowerMedia += leftover;
        } else if (upperHeight > 0) {
            upperMedia += leftover;
        }

        if (lowerHeight > 0) {
            owner.store(toLower, lowerMedia);
        } else {
            owner.store(0, 0);
        }
        if (upperHeight > 0
            && level.getBlockEntity(pos.above()) instanceof HexidTankBlockEntity upper) {
            upper.store(toUpper, upperMedia);
        }
    }

    private static void splitRemnants(Level level, BlockPos pos, HexidTankBlockEntity owner,
                                      int lowerHeight, int upperHeight) {
        TankRemnants held = owner.remnants();
        double total = held.total();
        if (total <= 0.0) {
            return;
        }
        double toLower = Math.min(total, HexidTank.dramCapacity(lowerHeight));
        double toUpper = Math.min(total - toLower, HexidTank.dramCapacity(upperHeight));

        TankRemnants upperShare = held.portion(toUpper / total);
        owner.storeRemnants(lowerHeight > 0 ? held.portion(toLower / total) : TankRemnants.EMPTY);
        if (upperHeight > 0
            && level.getBlockEntity(pos.above()) instanceof HexidTankBlockEntity upper) {
            upper.storeRemnants(upperShare);
        }
    }

    public static boolean joinWouldClash(Level level, BlockPos pos) {
        int lowerRun = below(level, pos);
        int upperRun = above(level, pos);
        if (lowerRun <= 0 || upperRun <= 0) {
            return false;
        }
        if (!(level.getBlockEntity(pos.below(lowerRun)) instanceof HexidTankBlockEntity lower)
            || !(level.getBlockEntity(pos.above()) instanceof HexidTankBlockEntity upper)) {
            return false;
        }
        if (lower.isRemnantStore() && upper.isRemnantStore()) {
            return !lower.remnants().types().equals(upper.remnants().types());
        }
        return (lower.isRemnantStore() && upper.holdsFluid())
            || (lower.holdsFluid() && upper.isRemnantStore());
    }

    private HexidTankColumn() {
    }
}
