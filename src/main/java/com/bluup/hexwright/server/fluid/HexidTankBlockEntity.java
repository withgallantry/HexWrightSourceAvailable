package com.bluup.hexwright.server.fluid;

import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.common.remnant.Remnant;
import com.bluup.hexwright.common.remnant.RemnantType;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class HexidTankBlockEntity extends BlockEntity {

    private static final String TAG_AMOUNT = "AmountMb";
    private static final String TAG_MEDIA_PER_MB = "MediaPerMb";
    private static final String TAG_REMAINDER = "MediaRemainder";
    private static final String TAG_REMNANTS = "Remnants";
    private static final String TAG_LEGACY_MEDIA = "Media";

    private long amountMb;
    private int mediaPerMb;
    private long remainder;
    private TankRemnants remnants = TankRemnants.EMPTY;

    public HexidTankBlockEntity(BlockPos pos, BlockState state) {
        super(HexwrightBlocks.HEXID_TANK_BLOCK_ENTITY, pos, state);
    }

    public long amountMb() {
        return amountMb;
    }

    public int mediaPerMb() {
        return mediaPerMb;
    }

    public long totalMedia() {
        return HexidTank.totalMedia(amountMb, mediaPerMb) + remainder;
    }

    public double saturation() {
        return HexidTank.saturation(mediaPerMb);
    }

    public boolean isHexid() {
        return amountMb > 0 && totalMedia() > 0;
    }

    public boolean holdsFluid() {
        return amountMb > 0 || totalMedia() > 0;
    }


    public TankRemnants remnants() {
        return remnants;
    }

    public boolean isRemnantStore() {
        return !remnants.isEmpty();
    }

    public double remnantCapacity() {
        return HexidTank.dramCapacity(columnHeight());
    }

    public double remnantHeadroom() {
        return Math.max(0.0, remnantCapacity() - remnants.total());
    }

    public boolean canAcceptRemnants() {
        return isRemnantStore() || !holdsFluid();
    }

    public boolean canAcceptRemnants(RemnantType type) {
        return canAcceptRemnants() && (remnants.isEmpty() || remnants.has(type));
    }

    public double addRemnant(@Nullable Remnant remnant) {
        if (remnant == null || remnant.isEmpty() || !canAcceptRemnants(remnant.type())) {
            return 0.0;
        }
        double room = remnantHeadroom();
        double poured = Math.min(room, remnant.drams());
        if (poured < TankRemnants.MIN_DRAMS) {
            return 0.0;
        }
        storeRemnants(remnants.plus(remnant.withDrams(poured)));
        return poured;
    }

    public double drawRemnant(RemnantType type, double drams) {
        double held = remnants.drams(type);
        double taken = Math.min(held, Math.max(0.0, drams));
        if (taken < TankRemnants.MIN_DRAMS) {
            return 0.0;
        }
        storeRemnants(remnants.minus(type, taken));
        return taken;
    }

    public double pourMixture(TankRemnants blend) {
        if (blend.isEmpty() || holdsFluid()) {
            return 0.0;
        }
        TankRemnants fits = blend.cappedTo(remnantHeadroom());
        if (fits.isEmpty()) {
            return 0.0;
        }
        storeRemnants(remnants.plusAll(fits));
        return fits.total();
    }

    public TankRemnants drawMixture(double drams) {
        double total = remnants.total();
        if (total <= 0.0 || drams < TankRemnants.MIN_DRAMS) {
            return TankRemnants.EMPTY;
        }
        TankRemnants taken = drams >= total ? remnants : remnants.portion(drams / total);
        if (taken.isEmpty()) {
            return TankRemnants.EMPTY;
        }
        storeRemnants(remnants.minusAll(taken));
        return taken;
    }

    public void storeRemnants(TankRemnants next) {
        if (!next.isEmpty() && holdsFluid()) {
            return;
        }
        if (remnants.equals(next)) {
            return;
        }
        remnants = next;
        setChanged();
        sync();
        HexidPipeNetwork.spread(level, getBlockPos());
    }

    public void clear() {
        storeRemnants(TankRemnants.EMPTY);
        store(0, 0);
    }

    public int columnHeight() {
        return level == null ? 1 : HexidTankColumn.height(level, getBlockPos());
    }

    public long capacityMb() {
        return HexidTank.capacityMb(columnHeight());
    }

    public long mediaHeadroom() {
        return Math.max(0, HexidTank.mediaCeiling(amountMb) - totalMedia());
    }

    public long drinkAreaMedia(ServerLevel level) {
        if (amountMb <= 0) {
            return 0;
        }
        long ceiling = HexidTank.mediaCeiling(amountMb);
        long media = totalMedia();
        long absorbed = 0;

        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, drinkArea(), ItemEntity::isAlive)) {
            long headroom = ceiling - media;
            if (headroom <= 0) {
                break;
            }
            ItemStack stack = entity.getItem().copy();
            ADMediaHolder holder = IXplatAbstractions.INSTANCE.findMediaHolder(stack);
            if (holder == null || !holder.canProvide() || holder.getMedia() <= 0) {
                continue;
            }
            long offered = holder.withdrawMedia(headroom, true);
            if (offered <= 0 || offered > headroom) {
                continue;
            }
            long taken = holder.withdrawMedia(headroom, false);
            if (taken <= 0) {
                continue;
            }
            media += taken;
            absorbed += taken;
            entity.setItem(stack);
            if (stack.isEmpty()) {
                entity.discard();
            }
        }

        if (absorbed > 0) {
            store(amountMb, media);
        }
        return absorbed;
    }

    private AABB drinkArea() {
        BlockPos pos = getBlockPos();
        return new AABB(
            pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1, pos.getY() + columnHeight() + 1, pos.getZ() + 1)
            .inflate(0.25, 0.0, 0.25);
    }

    public void store(long amountMb, long totalMedia) {
        long amount = Math.max(0, amountMb);
        long media = Math.max(0, totalMedia);
        if (isRemnantStore() && (amount > 0 || media > 0)) {
            return;
        }
        int density = amount <= 0 ? 0 : HexidTank.clampMediaPerMb(media / amount);
        long left = media - HexidTank.totalMedia(amount, density);

        if (this.amountMb == amount && this.mediaPerMb == density && this.remainder == left) {
            return;
        }
        this.amountMb = amount;
        this.mediaPerMb = density;
        this.remainder = left;
        setChanged();
        sync();
        HexidPipeNetwork.spread(level, getBlockPos());
    }

    public void storeAt(long amountMb, int mediaPerMb) {
        store(amountMb, HexidTank.totalMedia(amountMb, HexidTank.clampMediaPerMb(mediaPerMb)));
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        remnants = TankRemnants.load(tag.get(TAG_REMNANTS));
        amountMb = Math.max(0, tag.getLong(TAG_AMOUNT));
        if (tag.contains(TAG_LEGACY_MEDIA)) {
            long media = Math.max(0, tag.getLong(TAG_LEGACY_MEDIA));
            mediaPerMb = amountMb <= 0 ? 0 : HexidTank.clampMediaPerMb(media / amountMb);
            remainder = media - HexidTank.totalMedia(amountMb, mediaPerMb);
        } else {
            mediaPerMb = HexidTank.clampMediaPerMb(tag.getInt(TAG_MEDIA_PER_MB));
            remainder = Math.max(0, tag.getLong(TAG_REMAINDER));
        }
        if (isRemnantStore() && holdsFluid()) {
            amountMb = 0;
            mediaPerMb = 0;
            remainder = 0;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (amountMb > 0) {
            tag.putLong(TAG_AMOUNT, amountMb);
        }
        if (mediaPerMb > 0) {
            tag.putInt(TAG_MEDIA_PER_MB, mediaPerMb);
        }
        if (remainder > 0) {
            tag.putLong(TAG_REMAINDER, remainder);
        }
        if (isRemnantStore()) {
            tag.put(TAG_REMNANTS, remnants.save());
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
