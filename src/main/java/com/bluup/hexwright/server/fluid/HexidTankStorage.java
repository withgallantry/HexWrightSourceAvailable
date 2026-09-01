package com.bluup.hexwright.server.fluid;

import com.bluup.hexwright.server.block.HexwrightBlocks;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;

@SuppressWarnings("UnstableApiUsage")
public class HexidTankStorage extends SnapshotParticipant<Long> implements SingleSlotStorage<FluidVariant> {

    public static final long DROPLETS_PER_MB = FluidConstants.BUCKET / HexidTank.BUCKET_MB;

    private static final FluidVariant WATER = FluidVariant.of(Fluids.WATER);

    private final HexidTankBlockEntity tank;

    private long amountMb;
    private long committedMb;
    private long totalMedia;

    private HexidTankStorage(HexidTankBlockEntity tank) {
        this.tank = tank;
        this.amountMb = tank.amountMb();
        this.committedMb = this.amountMb;
        this.totalMedia = tank.totalMedia();
    }

    public static void register() {
        FluidStorage.SIDED.registerForBlockEntity((tank, direction) -> {
            Level level = tank.getLevel();
            if (level == null) {
                return null;
            }
            HexidTankBlockEntity controller = HexidTankColumn.controller(level, tank.getBlockPos());
            return controller == null ? null : new HexidTankStorage(controller);
        }, HexwrightBlocks.HEXID_TANK_BLOCK_ENTITY);
    }

    private void refresh() {
        if (committedMb != tank.amountMb() || totalMedia != tank.totalMedia()) {
            amountMb = tank.amountMb();
            committedMb = amountMb;
            totalMedia = tank.totalMedia();
        }
    }

    private boolean isWater() {
        return amountMb > 0 && totalMedia <= 0;
    }

    private static boolean isPlainWater(FluidVariant resource) {
        return resource.isOf(Fluids.WATER) && !resource.hasNbt();
    }


    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        if (!isPlainWater(resource)) {
            return 0;
        }
        refresh();
        long acceptMb = Math.min(tank.capacityMb() - amountMb, maxAmount / DROPLETS_PER_MB);
        if (acceptMb <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        amountMb += acceptMb;
        return acceptMb * DROPLETS_PER_MB;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        if (!isPlainWater(resource)) {
            return 0;
        }
        refresh();
        if (!isWater()) {
            return 0;
        }
        long giveMb = Math.min(amountMb, maxAmount / DROPLETS_PER_MB);
        if (giveMb <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        amountMb -= giveMb;
        return giveMb * DROPLETS_PER_MB;
    }

    @Override
    public boolean supportsExtraction() {
        refresh();
        return isWater();
    }


    @Override
    public FluidVariant getResource() {
        refresh();
        return isWater() ? WATER : FluidVariant.blank();
    }

    @Override
    public boolean isResourceBlank() {
        refresh();
        return !isWater();
    }

    @Override
    public long getAmount() {
        refresh();
        return isWater() ? amountMb * DROPLETS_PER_MB : 0;
    }

    @Override
    public long getCapacity() {
        return tank.capacityMb() * DROPLETS_PER_MB;
    }


    @Override
    protected Long createSnapshot() {
        return amountMb;
    }

    @Override
    protected void readSnapshot(Long snapshot) {
        amountMb = snapshot;
    }

    @Override
    protected void onFinalCommit() {
        tank.store(amountMb, totalMedia);
        committedMb = tank.amountMb();
        totalMedia = tank.totalMedia();
    }
}
