package com.bluup.hexwright.server.hexweave;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class HexLoomBlockEntity extends BlockEntity {

    private static final String TAG_ORB = "Orb";

    static final int DISPLAY_PATTERNS = 8;

    private ItemStack orb = ItemStack.EMPTY;
    private int revision;

    public HexLoomBlockEntity(BlockPos pos, BlockState state) {
        super(HexLoom.BLOCK_ENTITY, pos, state);
    }

    public ItemStack orb() {
        return orb;
    }

    public int revision() {
        return revision;
    }

    public void setOrb(ItemStack stack) {
        orb = stack;
        changed();
    }

    public void changed() {
        revision++;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public void dropContents(Level level, BlockPos pos) {
        if (!orb.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, orb);
            orb = ItemStack.EMPTY;
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        orb = tag.contains(TAG_ORB) ? ItemStack.of(tag.getCompound(TAG_ORB)) : ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!orb.isEmpty()) {
            tag.put(TAG_ORB, orb.save(new CompoundTag()));
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_ORB, forDisplay(orb).save(new CompoundTag()));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static ItemStack forDisplay(ItemStack orb) {
        if (!WovenOrbItem.hasWeave(orb)) {
            return orb;
        }
        ItemStack shown = orb.copy();
        shown.getOrCreateTag().put(WovenOrbItem.TAG_WEAVE,
            HexweaveNbt.displaySample(orb.getTag().getCompound(WovenOrbItem.TAG_WEAVE), DISPLAY_PATTERNS));
        return shown;
    }
}
