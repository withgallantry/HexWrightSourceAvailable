package com.bluup.hexwright.server.block;

import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.remnant.BottleData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PlacedBottleBlockEntity extends BlockEntity {

    private static final String TAG_BOTTLE = "Bottle";

    private ItemStack bottle = ItemStack.EMPTY;

    public PlacedBottleBlockEntity(BlockPos pos, BlockState state) {
        super(HexwrightBlocks.PLACED_BOTTLE_BLOCK_ENTITY, pos, state);
    }

    public ItemStack getBottle() {
        return bottle;
    }

    public boolean isEmpty() {
        return bottle.isEmpty();
    }

    public PocketCasterData.Quality grade() {
        return BottleData.getQuality(bottle);
    }

    public void setBottle(ItemStack stack) {
        bottle = stack;
        setChanged();
        sync();
    }

    public ItemStack takeBottle() {
        ItemStack taken = bottle;
        bottle = ItemStack.EMPTY;
        setChanged();
        sync();
        return taken;
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        bottle = tag.contains(TAG_BOTTLE)
            ? ItemStack.of(tag.getCompound(TAG_BOTTLE))
            : ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!bottle.isEmpty()) {
            tag.put(TAG_BOTTLE, bottle.save(new CompoundTag()));
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
