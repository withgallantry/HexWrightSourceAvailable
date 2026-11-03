package com.bluup.hexwright.server.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class DungeonPropBlockEntity extends BlockEntity implements GeoBlockEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private float turn;
    private float offsetX;
    private float offsetY;
    private float offsetZ;

    public DungeonPropBlockEntity(BlockPos pos, BlockState state) {
        super(DungeonProp.BLOCK_ENTITY, pos, state);
    }

    public DungeonProp prop() {
        return getBlockState().getBlock() instanceof DungeonPropBlock prop
            ? prop.prop()
            : DungeonProp.values()[0];
    }

    public float turn() {
        return this.turn;
    }

    public float offsetX() {
        return this.offsetX;
    }

    public float offsetY() {
        return this.offsetY;
    }

    public float offsetZ() {
        return this.offsetZ;
    }

    public DungeonPropCollision.Placement placement() {
        return new DungeonPropCollision.Placement(prop(),
            getBlockState().getValue(HorizontalDirectionalBlock.FACING),
            this.turn, this.offsetX, this.offsetY, this.offsetZ);
    }

    public void placeByHand() {
        DungeonPropCollision.Placement placement = DungeonPropCollision.Placement.byHand(
            prop(), getBlockState().getValue(HorizontalDirectionalBlock.FACING));
        this.turn = placement.turn();
        this.offsetX = placement.x();
        this.offsetY = placement.y();
        this.offsetZ = placement.z();
        setChanged();
    }

    private boolean placed() {
        return this.turn != 0 || this.offsetX != 0 || this.offsetY != 0 || this.offsetZ != 0;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.turn = tag.getFloat("Turn");
        ListTag offset = tag.getList("Offset", Tag.TAG_FLOAT);
        this.offsetX = offset.size() == 3 ? offset.getFloat(0) : 0;
        this.offsetY = offset.size() == 3 ? offset.getFloat(1) : 0;
        this.offsetZ = offset.size() == 3 ? offset.getFloat(2) : 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (placed()) {
            tag.putFloat("Turn", this.turn);
            ListTag offset = new ListTag();
            offset.add(FloatTag.valueOf(this.offsetX));
            offset.add(FloatTag.valueOf(this.offsetY));
            offset.add(FloatTag.valueOf(this.offsetZ));
            tag.put("Offset", offset);
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

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
