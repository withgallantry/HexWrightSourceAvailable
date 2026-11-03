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
import net.minecraft.world.level.block.entity.BlockEntityType;
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

    protected record Clips(RawAnimation closedIdle, RawAnimation openedIdle, RawAnimation open, RawAnimation close) {

        protected static Clips of(String closedIdle, String openedIdle, String open, String close) {
            return new Clips(RawAnimation.begin().thenLoop(closedIdle), RawAnimation.begin().thenLoop(openedIdle),
                RawAnimation.begin().thenPlay(open).thenLoop(openedIdle),
                RawAnimation.begin().thenPlay(close).thenLoop(closedIdle));
        }
    }

    private static final Clips CLIPS = Clips.of("closed_idle", "opened_idle", "open_portal", "close_portal");

    private static final int OPEN_TICKS = 95;
    private static final int CLOSE_TICKS = 75;

    private static final int PART_REPAIR_TICKS = 200;

    private static final int IDLE_SOUND_TICKS = 32 * 20;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int instanceId = -1;
    private Phase phase = Phase.CLOSED;
    private long phaseSince;

    private boolean whole;

    private @Nullable Phase shown;

    public TravelStoneBlockEntity(BlockPos pos, BlockState state) {
        this(InstancedDungeons.TRAVEL_STONE_BLOCK_ENTITY, pos, state);
    }

    protected TravelStoneBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public int openTicks() {
        return OPEN_TICKS;
    }

    protected int closeTicks() {
        return CLOSE_TICKS;
    }

    protected Clips clips() {
        return CLIPS;
    }

    protected double windowSill() {
        return TravelStones.WINDOW_SILL;
    }

    public Vec3 windowCenter() {
        return TravelStones.windowCenter(this.worldPosition, windowSill());
    }

    TravelStoneCollision collision() {
        return getBlockState().getBlock() instanceof TravelStoneBlock block
            ? block.collision()
            : TravelStoneCollision.TRAVEL_STONE;
    }

    protected void phaseSounds(ServerLevel level, long elapsed) {
        Vec3 center = windowCenter();
        if (this.phase == Phase.OPENING && elapsed == 0) {
            level.playSound(null, center.x, center.y, center.z, TravelStoneSounds.OPENING.get(), SoundSource.BLOCKS,
                2.0F, 1.0F);
        } else if (this.phase == Phase.OPEN && elapsed % IDLE_SOUND_TICKS == 0) {
            level.playSound(null, center.x, center.y, center.z, TravelStoneSounds.IDLE.get(), SoundSource.BLOCKS,
                1.5F, 1.0F);
        }
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
        boolean partsDue = !this.whole || level.getGameTime() % PART_REPAIR_TICKS == 0;
        if (partsDue && TravelStoneBlock.placeParts(level, this.worldPosition, front(), collision()) && !this.whole) {
            this.whole = true;
            setChanged();
        }
        Phase target = TravelStones.phaseFor(level.getServer(), this.instanceId);
        long now = level.getGameTime();
        Phase next = switch (this.phase) {
            case CLOSED -> target;
            case OPENING, OPEN -> target == Phase.CLOSED ? Phase.CLOSING : target;
            case CLOSING -> target != Phase.CLOSED || now - this.phaseSince >= closeTicks() ? target : Phase.CLOSING;
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
        phaseSounds(level, now - this.phaseSince);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        Clips clips = clips();
        controllers.add(new AnimationController<>(this, "stone", 0, state -> {
            if (this.shown != this.phase) {
                RawAnimation next = switch (this.phase) {
                    case OPENING -> clips.open();
                    case CLOSING -> clips.close();
                    case OPEN -> this.shown == null ? clips.openedIdle() : clips.open();
                    case CLOSED -> this.shown == null ? clips.closedIdle() : clips.close();
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
