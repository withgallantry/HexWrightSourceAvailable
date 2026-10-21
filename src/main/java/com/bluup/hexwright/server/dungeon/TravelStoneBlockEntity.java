package com.bluup.hexwright.server.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class TravelStoneBlockEntity extends BlockEntity implements GeoBlockEntity {

    public enum Phase {
        CLOSED,
        OPENING,
        OPEN,
        CLOSING
    }

    static final int OPEN_TICKS = 95;

    private static final int CLOSE_TICKS = 75;

    private static final int IDLE_SOUND_TICKS = 32 * 20;

    private static final RawAnimation CLOSED_IDLE = RawAnimation.begin().thenLoop("closed_idle");
    private static final RawAnimation OPENED_IDLE = RawAnimation.begin().thenLoop("opened_idle");
    private static final RawAnimation OPEN = RawAnimation.begin().thenPlay("open_portal").thenLoop("opened_idle");
    private static final RawAnimation CLOSE = RawAnimation.begin().thenPlay("close_portal").thenLoop("closed_idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int instanceId = -1;
    private Phase phase = Phase.CLOSED;
    private long phaseSince;

    private boolean whole;

    private @Nullable Phase shown;

    public TravelStoneBlockEntity(BlockPos pos, BlockState state) {
        super(InstancedDungeons.TRAVEL_STONE_BLOCK_ENTITY, pos, state);
    }

    public Phase phase() {
        return this.phase;
    }

    public Direction front() {
        return getBlockState().getValue(HorizontalDirectionalBlock.FACING);
    }

    boolean isOpen(MinecraftServer server) {
        return TravelStones.phaseFor(server, this.instanceId) != Phase.CLOSED;
    }

    void bind(int instanceId) {
        this.instanceId = instanceId;
        setChanged();
    }

    void serverTick(ServerLevel level) {
        if (!this.whole && TravelStoneBlock.placeParts(level, this.worldPosition, front())) {
            this.whole = true;
            setChanged();
        }
        Phase target = TravelStones.phaseFor(level.getServer(), this.instanceId);
        long now = level.getGameTime();
        Phase next = switch (this.phase) {
            case CLOSED -> target;
            case OPENING, OPEN -> target == Phase.CLOSED ? Phase.CLOSING : target;
            case CLOSING -> target != Phase.CLOSED || now - this.phaseSince >= CLOSE_TICKS ? target : Phase.CLOSING;
        };
        if (next != this.phase) {
            this.phase = next;
            this.phaseSince = now;
            if (next == Phase.CLOSED) {
                this.instanceId = -1;
            }
            setChanged();
            level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        if (this.phase == Phase.OPEN && (now - this.phaseSince) % IDLE_SOUND_TICKS == 0) {
            Vec3 center = TravelStones.windowCenter(this.worldPosition);
            level.playSound(null, center.x, center.y, center.z, TravelStoneSounds.IDLE.get(), SoundSource.BLOCKS,
                1.5F, 1.0F);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "stone", 0, state -> {
            if (this.shown != this.phase) {
                RawAnimation next = switch (this.phase) {
                    case OPENING -> OPEN;
                    case CLOSING -> CLOSE;
                    case OPEN -> this.shown == null ? OPENED_IDLE : OPEN;
                    case CLOSED -> this.shown == null ? CLOSED_IDLE : CLOSE;
                };
                state.getController().setAnimation(next);
                this.shown = this.phase;
            }
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.instanceId = tag.contains("Instance") ? tag.getInt("Instance") : -1;
        try {
            this.phase = Phase.valueOf(tag.getString("Phase"));
        } catch (IllegalArgumentException e) {
            this.phase = Phase.CLOSED;
        }
        this.phaseSince = tag.getLong("PhaseSince");
        this.whole = tag.getBoolean("Whole");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Instance", this.instanceId);
        tag.putString("Phase", this.phase.name());
        tag.putLong("PhaseSince", this.phaseSince);
        tag.putBoolean("Whole", this.whole);
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
