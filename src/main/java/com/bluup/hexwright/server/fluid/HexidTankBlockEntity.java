package com.bluup.hexwright.server.fluid;

import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
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

public class HexidTankBlockEntity extends BlockEntity {

    private static final String TAG_AMOUNT = "AmountMb";
    private static final String TAG_MEDIA_PER_MB = "MediaPerMb";
    private static final String TAG_REMAINDER = "MediaRemainder";
    private static final String TAG_LEGACY_MEDIA = "Media";

    private long amountMb;
    private int mediaPerMb;
    private long remainder;

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
        amountMb = Math.max(0, tag.getLong(TAG_AMOUNT));
        if (tag.contains(TAG_LEGACY_MEDIA)) {
            store(amountMb, tag.getLong(TAG_LEGACY_MEDIA));
            return;
        }
        mediaPerMb = HexidTank.clampMediaPerMb(tag.getInt(TAG_MEDIA_PER_MB));
        remainder = Math.max(0, tag.getLong(TAG_REMAINDER));
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
