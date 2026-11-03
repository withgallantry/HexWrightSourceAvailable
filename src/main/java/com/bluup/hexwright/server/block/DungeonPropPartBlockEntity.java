package com.bluup.hexwright.server.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DungeonPropPartBlockEntity extends BlockEntity {

    private DungeonPropCollision.Placement placement;
    private BlockPos offset = BlockPos.ZERO;
    private VoxelShape shape = Shapes.empty();

    public DungeonPropPartBlockEntity(BlockPos pos, BlockState state) {
        super(DungeonPropParts.BLOCK_ENTITY, pos, state);
    }

    public void bind(DungeonPropCollision.Placement placement, BlockPos offset) {
        this.placement = placement;
        this.offset = offset;
        this.shape = placement.cell(offset);
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public DungeonProp prop() {
        return this.placement == null ? null : this.placement.prop();
    }

    public BlockPos master() {
        return this.worldPosition.subtract(this.offset);
    }

    public VoxelShape shape() {
        return this.shape;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.placement != null) {
            tag.putString("prop", this.placement.prop().propName());
            tag.putString("facing", this.placement.facing().getSerializedName());
            tag.putIntArray("offset", new int[] {this.offset.getX(), this.offset.getY(), this.offset.getZ()});
            if (this.placement.turn() != 0) {
                tag.putFloat("turn", this.placement.turn());
            }
            if (this.placement.x() != 0 || this.placement.y() != 0 || this.placement.z() != 0) {
                ListTag shift = new ListTag();
                shift.add(FloatTag.valueOf(this.placement.x()));
                shift.add(FloatTag.valueOf(this.placement.y()));
                shift.add(FloatTag.valueOf(this.placement.z()));
                tag.put("shift", shift);
            }
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        DungeonProp prop = null;
        String name = tag.getString("prop");
        for (DungeonProp candidate : DungeonProp.values()) {
            if (candidate.propName().equals(name)) {
                prop = candidate;
                break;
            }
        }
        Direction read = Direction.byName(tag.getString("facing"));
        Direction facing = read != null && read.getAxis().isHorizontal() ? read : Direction.NORTH;
        ListTag shift = tag.getList("shift", Tag.TAG_FLOAT);
        this.placement = prop == null ? null : new DungeonPropCollision.Placement(prop, facing,
            tag.getFloat("turn"),
            shift.size() == 3 ? shift.getFloat(0) : 0,
            shift.size() == 3 ? shift.getFloat(1) : 0,
            shift.size() == 3 ? shift.getFloat(2) : 0);
        int[] o = tag.getIntArray("offset");
        this.offset = o.length == 3 ? new BlockPos(o[0], o[1], o[2]) : BlockPos.ZERO;
        this.shape = this.placement == null ? Shapes.empty() : this.placement.cell(this.offset);
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
