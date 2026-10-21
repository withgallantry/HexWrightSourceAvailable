package com.bluup.hexwright.server.constructshand;

import at.petrak.hexcasting.api.casting.math.HexPattern;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ConstructsHandBlockEntity extends BlockEntity {

    private static final String TAG_HELD = "Held";
    private static final String TAG_KEY = "Key";
    private static final String TAG_IDENTITY = "Identity";

    private ItemStack held = ItemStack.EMPTY;
    private @Nullable HexPattern key;
    private @Nullable UUID identity;

    public ConstructsHandBlockEntity(BlockPos pos, BlockState state) {
        super(HexwrightBlocks.CONSTRUCTS_HAND_BLOCK_ENTITY, pos, state);
    }

    public ItemStack getHeld() {
        return held;
    }

    public void setHeld(ItemStack stack) {
        held = stack;
        changed();
    }

    public ItemStack takeHeld() {
        ItemStack taken = held;
        held = ItemStack.EMPTY;
        changed();
        return taken;
    }

    public @Nullable HexPattern getKey() {
        return key;
    }

    public void setKey(@Nullable HexPattern key) {
        this.key = key;
        if (level != null && !level.isClientSide) {
            ConstructsHands.track(level, worldPosition, key);
        }
        changed();
    }

    public UUID identity() {
        if (identity == null) {
            identity = UUID.randomUUID();
            setChanged();
        }
        return identity;
    }

    void lend() {
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level != null && !level.isClientSide) {
            ConstructsHands.track(level, worldPosition, key);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            ConstructsHands.untrack(level, worldPosition);
        }
        super.setRemoved();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        held = tag.contains(TAG_HELD, Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound(TAG_HELD)) : ItemStack.EMPTY;
        key = tag.contains(TAG_KEY, Tag.TAG_COMPOUND) && HexPattern.isPattern(tag.getCompound(TAG_KEY))
            ? HexPattern.fromNBT(tag.getCompound(TAG_KEY))
            : null;
        identity = tag.hasUUID(TAG_IDENTITY) ? tag.getUUID(TAG_IDENTITY) : null;
        if (level != null && !level.isClientSide && !isRemoved()) {
            ConstructsHands.track(level, worldPosition, key);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!held.isEmpty()) {
            tag.put(TAG_HELD, held.save(new CompoundTag()));
        }
        if (key != null) {
            tag.put(TAG_KEY, key.serializeToNBT());
        }
        if (identity != null) {
            tag.putUUID(TAG_IDENTITY, identity);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = saveWithoutMetadata();
        tag.put(TAG_HELD, held.save(new CompoundTag()));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
