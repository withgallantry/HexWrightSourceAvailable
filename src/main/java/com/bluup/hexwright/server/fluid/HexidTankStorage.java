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
public class HexidTankStorage extends SnapshotParticipant<HexidTankStorage.Staged>
    implements SingleSlotStorage<FluidVariant> {

    public static final long DROPLETS_PER_MB = FluidConstants.BUCKET / HexidTank.BUCKET_MB;

    private static final FluidVariant WATER = FluidVariant.of(Fluids.WATER);
    private static final FluidVariant HEXID = FluidVariant.of(HexidFluids.HEXID);

    protected record Staged(long amountMb, long totalMedia) {
    }

    private final HexidTankBlockEntity tank;

    private long amountMb;
    private long totalMedia;
    private long committedMb;
    private long committedMedia;
    private boolean remnants;

    private HexidTankStorage(HexidTankBlockEntity tank) {
        this.tank = tank;
        this.amountMb = tank.amountMb();
        this.totalMedia = tank.totalMedia();
        this.committedMb = this.amountMb;
        this.committedMedia = this.totalMedia;
        this.remnants = tank.isRemnantStore();
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
        if (committedMb != tank.amountMb() || committedMedia != tank.totalMedia()
            || remnants != tank.isRemnantStore()) {
            amountMb = tank.amountMb();
            totalMedia = tank.totalMedia();
            committedMb = amountMb;
            committedMedia = totalMedia;
            remnants = tank.isRemnantStore();
        }
    }

    private boolean isWater() {
        return !remnants && amountMb > 0 && totalMedia <= 0;
    }

    private boolean isWorldHexid() {
        return !remnants && amountMb > 0 && totalMedia == HexidFluids.mediaIn(amountMb);
    }

    private static boolean isPlainWater(FluidVariant resource) {
        return resource.isOf(Fluids.WATER) && !resource.hasNbt();
    }


    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        boolean hexid = HexidFluids.isHexid(resource);
        if (!hexid && !isPlainWater(resource)) {
            return 0;
        }
        refresh();
        if (remnants) {
            return 0;
        }
        long acceptMb = Math.min(tank.capacityMb() - amountMb, maxAmount / DROPLETS_PER_MB);
        if (acceptMb <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        amountMb += acceptMb;
        if (hexid) {
            totalMedia += HexidFluids.mediaIn(acceptMb);
        }
        return acceptMb * DROPLETS_PER_MB;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        refresh();
        boolean hexid = HexidFluids.isHexid(resource);
        if (hexid ? !isWorldHexid() : !(isPlainWater(resource) && isWater())) {
            return 0;
        }
        long giveMb = Math.min(amountMb, maxAmount / DROPLETS_PER_MB);
        if (giveMb <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        amountMb -= giveMb;
        if (hexid) {
            totalMedia -= HexidFluids.mediaIn(giveMb);
        }
        return giveMb * DROPLETS_PER_MB;
    }

    @Override
    public boolean supportsExtraction() {
        refresh();
        return isWater() || isWorldHexid();
    }

    @Override
    public boolean supportsInsertion() {
        refresh();
        return !remnants;
    }


    @Override
    public FluidVariant getResource() {
        refresh();
        if (isWater()) {
            return WATER;
        }
        return isWorldHexid() ? HEXID : FluidVariant.blank();
    }

    @Override
    public boolean isResourceBlank() {
        refresh();
        return !isWater() && !isWorldHexid();
    }

    @Override
    public long getAmount() {
        refresh();
        return isWater() || isWorldHexid() ? amountMb * DROPLETS_PER_MB : 0;
    }

    @Override
    public long getCapacity() {
        refresh();
        return remnants ? 0 : tank.capacityMb() * DROPLETS_PER_MB;
    }


    @Override
    protected Staged createSnapshot() {
        return new Staged(amountMb, totalMedia);
    }

    @Override
    protected void readSnapshot(Staged snapshot) {
        amountMb = snapshot.amountMb();
        totalMedia = snapshot.totalMedia();
    }

    @Override
    protected void onFinalCommit() {
        tank.store(amountMb, totalMedia);
        committedMb = tank.amountMb();
        committedMedia = tank.totalMedia();
        amountMb = committedMb;
        totalMedia = committedMedia;
    }
}
