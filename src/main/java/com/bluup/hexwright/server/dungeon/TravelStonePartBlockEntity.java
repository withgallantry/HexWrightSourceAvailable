package com.bluup.hexwright.server.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TravelStonePartBlockEntity extends BlockEntity {

    private BlockPos offset = BlockPos.ZERO;

    public TravelStonePartBlockEntity(BlockPos pos, BlockState state) {
        super(InstancedDungeons.TRAVEL_STONE_PART_BLOCK_ENTITY, pos, state);
    }

    void bind(BlockPos offset) {
        this.offset = offset;
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public BlockPos stone() {
        return this.worldPosition.subtract(this.offset);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putIntArray("Offset", new int[] {this.offset.getX(), this.offset.getY(), this.offset.getZ()});
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        int[] o = tag.getIntArray("Offset");
        this.offset = o.length == 3 ? new BlockPos(o[0], o[1], o[2]) : BlockPos.ZERO;
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
