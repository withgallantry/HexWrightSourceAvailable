package com.bluup.hexwright.server.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class CrystalAltarBlockEntity extends BlockEntity implements GeoBlockEntity {

    public static final double KEY_HEIGHT = 1.45;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int instanceId = -1;
    private ItemStack key = ItemStack.EMPTY;
    private long closesAt;

    public CrystalAltarBlockEntity(BlockPos pos, BlockState state) {
        super(InstancedDungeons.CRYSTAL_ALTAR_BLOCK_ENTITY, pos, state);
    }

    public boolean lit() {
        return this.instanceId >= 0;
    }

    public ItemStack key() {
        return this.key;
    }

    public long closesAt() {
        return this.closesAt;
    }

    int instanceId() {
        return this.instanceId;
    }

    void bind(DungeonInstance instance) {
        this.instanceId = instance.id();
        this.key = InstancedDungeons.keyFor(instance.kind());
        this.closesAt = 0L;
        sync();
        if (this.level != null) {
            this.level.playSound(null, this.worldPosition, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS,
                1.0F, 0.8F);
        }
    }

    void shatterKey(ServerLevel level) {
        if (this.key.isEmpty()) {
            return;
        }
        level.playSound(null, this.worldPosition, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.7F, 0.9F);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, this.key),
            this.worldPosition.getX() + 0.5, this.worldPosition.getY() + KEY_HEIGHT, this.worldPosition.getZ() + 0.5,
            16, 0.12, 0.12, 0.12, 0.06);
    }

    void serverTick(ServerLevel level) {
        if (this.instanceId < 0) {
            return;
        }
        DungeonInstance instance = DungeonInstanceRegistry.get(level.getServer()).byId(this.instanceId);
        long now = level.getServer().overworld().getGameTime();
        boolean forming = instance != null && instance.windowForming(now);
        if (!forming && (instance == null || !instance.windowHolds(now))) {
            this.instanceId = -1;
            this.key = ItemStack.EMPTY;
            this.closesAt = 0L;
            level.playSound(null, this.worldPosition, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS,
                1.0F, 0.4F);
            if (DungeonDimension.isDungeonLevel(level)) {
                level.removeBlock(this.worldPosition, false);
            } else {
                sync();
            }
            return;
        }
        long closesAt = forming ? 0L : instance.riftClosesAt();
        if (closesAt != this.closesAt) {
            this.closesAt = closesAt;
            sync();
        }
    }

    private void sync() {
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.instanceId = tag.contains("Instance") ? tag.getInt("Instance") : -1;
        this.key = ItemStack.of(tag.getCompound("Key"));
        this.closesAt = tag.getLong("ClosesAt");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Instance", this.instanceId);
        if (!this.key.isEmpty()) {
            tag.put("Key", this.key.save(new CompoundTag()));
        }
        tag.putLong("ClosesAt", this.closesAt);
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
