package com.bluup.hexwright.server.vehicle;

import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.api.utils.MediaHelper;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.bindstone.BindstoneRegistry;
import com.bluup.hexwright.server.worldgen.AreaWard;
import com.bluup.hexwright.mixin.LivingEntityJumpingAccessor;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class VehicleEntity extends Entity {

    private static final EntityDataAccessor<String> DATA_VARIANT =
        SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_QUALITY =
        SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Vector3f> DATA_ACCEPTED_COMMAND =
        SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Boolean> DATA_STALLED =
        SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_OVERSPEED =
        SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> DATA_MEDIA =
        SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_MEDIA_COST =
        SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.LONG);

    private static final double GRAVITY_PER_TICK = 0.08;
    private static final double FALL_DRAG = 0.98;

    public enum ConversionReason {
        PACKED_AWAY,
        MISHAP
    }

    private @Nullable Iota storedHex;
    private ListIota persistentMemory = new ListIota(List.of());
    private Vec3 previousCommand = Vec3.ZERO;
    private int overspeedRuns = 0;
    private boolean converting = false;
    private int flightProgramCooldown = 0;
    private boolean hadControllingRider = false;
    private @Nullable UUID dismountedRider;
    private boolean dismountedToGround = false;
    private boolean dismountedWithNoRoom = false;
    private double parkedAnchorY = Double.NaN;

    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    private double lerpYRot, lerpXRot;

    private @Nullable Vec3 authoritativePos;
    private @Nullable Vec3 authoritativeVel;

    protected VehicleEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_VARIANT, VehicleData.DEFAULT_VARIANT);
        this.entityData.define(DATA_QUALITY, PocketCasterData.Quality.CRUDE.name());
        this.entityData.define(DATA_ACCEPTED_COMMAND, new Vector3f());
        this.entityData.define(DATA_STALLED, false);
        this.entityData.define(DATA_OVERSPEED, false);
        this.entityData.define(DATA_MEDIA, 0L);
        this.entityData.define(DATA_MEDIA_COST, 0L);
    }


    public abstract double getMaxHorizontalSpeed();

    public abstract double getMaxVerticalSpeed();

    public abstract double getMaxAcceleration();

    public abstract int getPassengerCapacity();

    public abstract double getMediaMultiplier();

    public abstract long getMediaCapacity();

    public double getLoadMultiplier() {
        return 1.0;
    }

    protected boolean packsAwayOnDismount() {
        return false;
    }

    protected boolean packsAwayOnPunch() {
        return true;
    }

    public abstract Item getItemForm();

    public abstract String variantNameKey();

    public abstract double getRiderHeightOffset();

    @Override
    public double getPassengersRidingOffset() {
        return getRiderHeightOffset();
    }

    public double getRiderForwardOffset() {
        return 0.0;
    }

    protected double seatForwardOffset(int seatIndex) {
        return getRiderForwardOffset();
    }

    public double getRenderScale() {
        return 1.0;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        double forwardOffset = seatForwardOffset(this.getPassengers().indexOf(passenger));
        if (forwardOffset == 0.0) {
            super.positionRider(passenger, callback);
        } else {
            Vec3 forward = VehicleMovementMath.horizontalForward(this.getYRot());
            super.positionRider(passenger, (seated, x, y, z) -> callback.accept(
                seated, x + forward.x * forwardOffset, y, z + forward.z * forwardOffset
            ));
        }
        if (passenger instanceof LivingEntity living) {
            living.setYBodyRot(this.getYRot());
        }
    }

    public double getVisualTiltDegrees() {
        return 0.0;
    }

    protected void writeExtra(CompoundTag data) {
    }

    protected void readExtra(CompoundTag data, ServerLevel level) {
    }


    public String getVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public void setVariant(String variant) {
        this.entityData.set(DATA_VARIANT, variant);
    }

    public PocketCasterData.Quality getQuality() {
        return PocketCasterData.Quality.byName(this.entityData.get(DATA_QUALITY));
    }

    public void setQuality(PocketCasterData.Quality quality) {
        this.entityData.set(DATA_QUALITY, quality.name());
    }

    public long getInternalMedia() {
        return this.entityData.get(DATA_MEDIA);
    }

    public void setInternalMedia(long media) {
        this.entityData.set(DATA_MEDIA, Math.max(0L, Math.min(getMediaCapacity(), media)));
    }

    public long getLastMediaCost() {
        return this.entityData.get(DATA_MEDIA_COST);
    }

    public @Nullable Iota getStoredHex() {
        return storedHex;
    }

    public void setStoredHex(@Nullable Iota hex) {
        this.storedHex = hex;
    }

    public ListIota getPersistentMemory() {
        return persistentMemory;
    }

    public int getTicksUntilNextEvaluation() {
        return Math.max(0, flightProgramCooldown);
    }

    public Vec3 getPreviousCommand() {
        return previousCommand;
    }

    public Vec3 getAcceptedCommand() {
        return new Vec3(this.entityData.get(DATA_ACCEPTED_COMMAND));
    }

    private void setAcceptedCommand(Vec3 command) {
        this.previousCommand = command;
        this.entityData.set(DATA_ACCEPTED_COMMAND, command.toVector3f());
    }

    public boolean isStalled() {
        return this.entityData.get(DATA_STALLED);
    }

    public boolean isOverspeed() {
        return this.entityData.get(DATA_OVERSPEED);
    }

    private void clearOverspeed() {
        overspeedRuns = 0;
        if (this.entityData.get(DATA_OVERSPEED)) {
            this.entityData.set(DATA_OVERSPEED, false);
        }
    }

    private void setStalled(boolean stalled) {
        this.entityData.set(DATA_STALLED, stalled);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < getPassengerCapacity();
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        List<Entity> passengers = this.getPassengers();
        if (passengers.isEmpty()) {
            return null;
        }
        Entity first = passengers.get(0);
        return first instanceof LivingEntity living ? living : null;
    }

    public @Nullable ServerPlayer getControllingRider() {
        Entity controlling = getControllingPassenger();
        return controlling instanceof ServerPlayer player ? player : null;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ServerPlayer serverPlayer = (ServerPlayer) player;
        ItemStack held = player.getItemInHand(hand);

        if (!held.isEmpty() && tryChargeFrom(held, serverPlayer)) {
            return InteractionResult.CONSUME;
        }

        if (player.isShiftKeyDown()) {
            boolean packed = convertToItemAndDiscard(ConversionReason.PACKED_AWAY, serverPlayer);
            return packed ? InteractionResult.CONSUME : InteractionResult.PASS;
        }

        return tryClaimSlot(player, held) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    protected boolean tryClaimSlot(Player player, ItemStack held) {
        if (this.getPassengers().size() < getPassengerCapacity()) {
            player.startRiding(this);
            return true;
        }
        return false;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        boolean wasControlling = passenger == getControllingPassenger();
        super.removePassenger(passenger);
        if (this.level().isClientSide || converting || !wasControlling || !(passenger instanceof Player player)) {
            return;
        }
        dismountedRider = player.getUUID();
        dismountedToGround = false;
        dismountedWithNoRoom = false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved() || !packsAwayOnPunch()) {
            return false;
        }
        if (!(source.getEntity() instanceof Player player)) {
            return false;
        }
        if (this.hasPassenger(player)) {
            return false;
        }
        return convertToItemAndDiscard(
            ConversionReason.PACKED_AWAY,
            player instanceof ServerPlayer serverPlayer ? serverPlayer : null
        );
    }


    private boolean tryChargeFrom(ItemStack stack, ServerPlayer player) {
        ADMediaHolder holder = IXplatAbstractions.INSTANCE.findMediaHolder(stack);
        if (holder == null || !holder.canProvide()) {
            return false;
        }
        long space = getMediaCapacity() - getInternalMedia();
        if (space <= 0L) {
            player.displayClientMessage(
                Component.translatable("message.hexwright.vehicle.media_full").withStyle(ChatFormatting.YELLOW), true);
            return true;
        }
        long want = Math.min(space, holder.getMedia());
        if (want <= 0L) {
            return false;
        }
        long got = holder.withdrawMedia(want, false);
        if (got <= 0L) {
            return false;
        }
        setInternalMedia(getInternalMedia() + got);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.7f, 1.1f);
        player.displayClientMessage(
            Component.translatable("message.hexwright.vehicle.charged",
                getInternalMedia() / MediaConstants.DUST_UNIT, getMediaCapacity() / MediaConstants.DUST_UNIT
            ).withStyle(ChatFormatting.AQUA), true);
        return true;
    }


    protected boolean convertToItemAndDiscard(ConversionReason reason, @Nullable ServerPlayer initiator) {
        if (converting || !this.isAlive()) {
            return false;
        }
        converting = true;
        try {
            setStalled(true);
            this.setDeltaMovement(Vec3.ZERO);
            ItemStack packed = toItemStack();
            ejectPassengersSafely(reason);

            boolean handedToInitiator = reason == ConversionReason.PACKED_AWAY
                && initiator != null
                && returnToInventory(initiator, packed);
            if (!handedToInitiator) {
                ItemEntity dropped = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), packed);
                dropped.setDeltaMovement(Vec3.ZERO);
                dropped.setPickUpDelay(10);
                this.level().addFreshEntity(dropped);
            }
            this.discard();
            return true;
        } finally {
            converting = false;
        }
    }

    private static boolean returnToInventory(ServerPlayer player, ItemStack packed) {
        Inventory inventory = player.getInventory();
        int selected = inventory.selected;
        if (inventory.getItem(selected).isEmpty()) {
            inventory.setItem(selected, packed);
            return true;
        }
        return inventory.add(packed);
    }

    private void ejectPassengersSafely(ConversionReason reason) {
        for (Entity passenger : new ArrayList<>(this.getPassengers())) {
            passenger.stopRiding();
            if (reason == ConversionReason.MISHAP && passenger instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
            }
        }
    }


    private void writeVehicleData(CompoundTag data) {
        VehicleData.setVariant(data, getVariant());
        VehicleData.setQuality(data, getQuality());
        VehicleData.setMedia(data, getInternalMedia(), getMediaCapacity());
        VehicleData.setHex(data, storedHex);
        VehicleData.setMemory(data, persistentMemory);
        VehicleData.setPreviousCommand(data, previousCommand);
        writeExtra(data);
    }

    private void readVehicleData(CompoundTag data, ServerLevel level) {
        setVariant(VehicleData.getVariant(data));
        setQuality(VehicleData.getQuality(data));
        setInternalMedia(VehicleData.getMedia(data));
        this.storedHex = VehicleData.getHex(data, level);
        this.persistentMemory = VehicleData.getMemory(data, level);
        setAcceptedCommand(VehicleData.getPreviousCommand(data));
        readExtra(data, level);
    }

    protected ItemStack toItemStack() {
        ItemStack stack = new ItemStack(getItemForm());
        writeVehicleData(stack.getOrCreateTagElement(VehicleData.ROOT_TAG));
        return stack;
    }

    public void readFromItemStack(ItemStack stack, ServerLevel level) {
        readVehicleData(stack.getOrCreateTagElement(VehicleData.ROOT_TAG), level);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        writeVehicleData(data);
        tag.put(VehicleData.ROOT_TAG, data);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (!tag.contains(VehicleData.ROOT_TAG) || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        readVehicleData(tag.getCompound(VehicleData.ROOT_TAG), level);
    }


    @Override
    public void tick() {
        this.setOldPosAndRot();
        super.tick();
        if (this.level().isClientSide) {
            clientTick();
            return;
        }

        for (Entity passenger : this.getPassengers()) {
            passenger.resetFallDistance();
        }

        ServerPlayer rider = getControllingRider();
        if (rider == null) {
            boolean justLostRider = hadControllingRider;
            hadControllingRider = false;
            if (tryPackAwayAfterDismount()) {
                return;
            }
            parkHovering();
            if (justLostRider) {
                broadcastAbsolutePosition();
            }
            return;
        }
        hadControllingRider = true;
        parkedAnchorY = Double.NaN;

        syncYawToRider(rider);

        String wardStall = null;
        if (BindstoneRegistry.isWarded(this.level(), this.position())) {
            wardStall = "message.hexwright.vehicle.stalled_bindstone";
        } else if (AreaWard.sealed(this.level(), this.blockPosition())) {
            wardStall = "message.hexwright.vehicle.stalled_dungeon";
        }
        if (wardStall != null) {
            if (!isStalled()) {
                rider.sendSystemMessage(Component.translatable(wardStall));
                setStalled(true);
            }
            setAcceptedCommand(Vec3.ZERO);
            clearOverspeed();
            this.setNoGravity(false);
            this.setDeltaMovement(fallStep(this.getDeltaMovement()));
            this.move(MoverType.SELF, this.getDeltaMovement());
            HexwrightNetworking.sendVehicleReconciliation(rider, this.getId(), this.position(), this.getDeltaMovement());
            return;
        }

        if (isStalled()) {
            tickStalledFalling(rider);
            return;
        }

        this.setNoGravity(true);
        if (flightProgramCooldown-- <= 0) {
            flightProgramCooldown = VehicleConfig.HEX_EXECUTION_INTERVAL_TICKS;
            runFlightProgram(rider);
            if (this.isRemoved()) {
                return;
            }
        }
        applyMovementControllerEveryTick(rider);
        HexwrightNetworking.sendVehicleReconciliation(rider, this.getId(), this.position(), this.getDeltaMovement());
    }

    private void syncYawToRider(LivingEntity rider) {
        this.yRotO = this.getYRot();
        this.setYRot(rider.getYRot());
    }

    private void broadcastAbsolutePosition() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().broadcast(this, new ClientboundTeleportEntityPacket(this));
        }
    }

    private boolean tryPackAwayAfterDismount() {
        UUID riderId = dismountedRider;
        dismountedRider = null;
        if (riderId == null) {
            return false;
        }
        if (!dismountedWithNoRoom && (!dismountedToGround || !packsAwayOnDismount())) {
            return false;
        }
        ServerPlayer rider = this.level().getServer() == null
            ? null
            : this.level().getServer().getPlayerList().getPlayer(riderId);
        return convertToItemAndDiscard(ConversionReason.PACKED_AWAY, rider);
    }

    private void parkHovering() {
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        setStalled(false);
        setAcceptedCommand(Vec3.ZERO);
        clearOverspeed();
        this.entityData.set(DATA_MEDIA_COST, 0L);
        maintainParkedMinHeight();
    }

    private void maintainParkedMinHeight() {
        if (!Double.isNaN(parkedAnchorY)) {
            return;
        }
        double groundY = findGroundYBelow();
        double minY = Double.isNaN(groundY) ? this.getY() : groundY + VehicleConfig.PARK_MIN_HOVER_HEIGHT;
        parkedAnchorY = Math.max(this.getY(), minY);
        if (parkedAnchorY != this.getY()) {
            this.setPos(this.getX(), parkedAnchorY, this.getZ());
        }
    }

    private double findGroundYBelow() {
        return findGroundYBelow(this.position(), VehicleConfig.PARK_GROUND_SCAN_DISTANCE);
    }

    private double findGroundYBelow(Vec3 start, double maxDistance) {
        Vec3 end = start.add(0, -maxDistance, 0);
        BlockHitResult hit = this.level().clip(new ClipContext(
            start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().y : Double.NaN;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 topOfBroom = new Vec3(this.getX(), this.getBoundingBox().maxY, this.getZ());
        double groundY = findGroundYBelow(this.position(), VehicleConfig.DISMOUNT_GROUND_SNAP_DISTANCE);

        if (!Double.isNaN(groundY)) {
            Vec3 nearGround = findNearbySafeDismountLocation(passenger, groundY);
            if (nearGround != null) {
                if (passenger.getUUID().equals(dismountedRider)) {
                    dismountedToGround = true;
                }
                passenger.resetFallDistance();
                return nearGround;
            }
        }

        if (trySelectDismountPose(passenger, topOfBroom)) {
            passenger.resetFallDistance();
            return topOfBroom;
        }

        if (passenger.getUUID().equals(dismountedRider)) {
            dismountedWithNoRoom = true;
        }
        passenger.resetFallDistance();
        return findFallAwayLocation(passenger);
    }

    private Vec3 findFallAwayLocation(LivingEntity passenger) {
        Vec3 seat = passenger.position();
        for (double drop = 0.0; drop <= VehicleConfig.DISMOUNT_GROUND_SNAP_DISTANCE; drop += 1.0) {
            Vec3 candidate = seat.subtract(0.0, drop, 0.0);
            if (trySelectDismountPose(passenger, candidate, true)) {
                return candidate;
            }
        }
        return seat;
    }

    private @Nullable Vec3 findNearbySafeDismountLocation(LivingEntity passenger, double groundY) {
        BlockPos base = BlockPos.containing(this.getX(), groundY + 0.01, this.getZ());
        for (BlockPos candidate : getNearbyDismountCandidates(base)) {
            Vec3 safe = DismountHelper.findSafeDismountLocation(
                passenger.getType(),
                this.level(),
                candidate,
                false
            );
            if (safe != null && trySelectDismountPose(passenger, safe)) {
                return safe;
            }
        }
        return null;
    }

    private static List<BlockPos> getNearbyDismountCandidates(BlockPos center) {
        return List.of(
            center.north(),
            center.south(),
            center.west(),
            center.east(),
            center.north().west(),
            center.north().east(),
            center.south().west(),
            center.south().east(),
            center
        );
    }

    private boolean trySelectDismountPose(LivingEntity passenger, Vec3 location) {
        return trySelectDismountPose(passenger, location, false);
    }

    private boolean trySelectDismountPose(LivingEntity passenger, Vec3 location, boolean vehicleIsLeaving) {
        Pose originalPose = passenger.getPose();
        for (Pose pose : passenger.getDismountPoses()) {
            if (pose == Pose.SWIMMING) {
                continue;
            }
            passenger.setPose(pose);
            AABB candidateBox = passenger.getBoundingBox().move(location.subtract(passenger.position()));
            if (!vehicleIsLeaving && candidateBox.intersects(this.getBoundingBox())) {
                continue;
            }
            if (DismountHelper.canDismountTo(this.level(), passenger, candidateBox)) {
                return true;
            }
        }
        passenger.setPose(originalPose);
        return false;
    }

    private void tickStalledFalling(ServerPlayer rider) {
        this.setNoGravity(false);
        this.setDeltaMovement(fallStep(this.getDeltaMovement()));
        this.move(MoverType.SELF, this.getDeltaMovement());
        HexwrightNetworking.sendVehicleReconciliation(rider, this.getId(), this.position(), this.getDeltaMovement());

        if (flightProgramCooldown-- <= 0) {
            flightProgramCooldown = VehicleConfig.HEX_EXECUTION_INTERVAL_TICKS;
            if (canPotentiallyResumeFlight(rider)) {
                runFlightProgram(rider);
            }
        }
    }

    private boolean canPotentiallyResumeFlight(ServerPlayer rider) {
        if (getInternalMedia() > 0) {
            return true;
        }
        for (ADMediaHolder source : MediaHelper.scanPlayerForMediaStuff(rider)) {
            if (MediaHelper.extractMedia(source, -1, false, true) > 0) {
                return true;
            }
        }
        return false;
    }


    private void runFlightProgram(ServerPlayer rider) {
        if (storedHex == null || !(this.level() instanceof ServerLevel level)) {
            return;
        }

        FlightExecutionContext context = buildFlightExecutionContext(rider);
        FlightCastingEnvironment env = new FlightCastingEnvironment(level, this, rider, context);

        FlightRunner.FlightRunResult result = FlightRunner.run(storedHex, env, level);

        if (result instanceof FlightRunner.FlightRunResult.Failed failed) {
            if (failed.punishing()) {
                rider.sendSystemMessage(failed.riderMessage());
                convertToItemAndDiscard(ConversionReason.MISHAP, null);
            }
            return;
        }

        FlightRunner.FlightRunResult.Success success = (FlightRunner.FlightRunResult.Success) result;
        Vec3 candidateAcceleration = success.acceleration();
        Vec3 candidateVelocity = sanitizeVelocity(this.getDeltaMovement().add(candidateAcceleration));

        int candidateOverspeedRuns = success.overspeed() ? overspeedRuns + 1 : 0;
        boolean penalised = candidateOverspeedRuns >= VehicleConfig.OVERSPEED_GRACE_RUNS;

        long cost = VehicleMediaCost.compute(
            candidateVelocity,
            candidateAcceleration,
            previousCommand,
            getMaxHorizontalSpeed(),
            getMaxVerticalSpeed(),
            getMaxAcceleration(),
            getMediaMultiplier(),
            getLoadMultiplier(),
            penalised
        );
        this.entityData.set(DATA_MEDIA_COST, cost);

        if (env.extractMedia(cost, true) > 0) {
            if (!isStalled()) {
                rider.sendSystemMessage(Component.translatable("message.hexwright.vehicle.stalled_no_media"));
            }
            setStalled(true);
            setAcceptedCommand(Vec3.ZERO);
            clearOverspeed();
            return;
        }
        env.extractMedia(cost, false);

        this.setDeltaMovement(candidateVelocity);
        setAcceptedCommand(candidateAcceleration);
        this.persistentMemory = success.memory();
        setStalled(false);

        overspeedRuns = candidateOverspeedRuns;
        if (this.entityData.get(DATA_OVERSPEED) != penalised) {
            this.entityData.set(DATA_OVERSPEED, penalised);
        }
    }

    public FlightExecutionContext debugContext() {
        return buildFlightExecutionContext(getControllingRider());
    }

    private FlightExecutionContext buildFlightExecutionContext(@Nullable ServerPlayer rider) {
        Vec3 forward = VehicleMovementMath.horizontalForward(rider == null ? this.getYRot() : rider.getYRot());
        Vec3 right = VehicleMovementMath.horizontalRight(forward);
        return new FlightExecutionContext(
            rider == null ? Vec3.ZERO : readRiderInputVector(rider),
            this.position(),
            this.getDeltaMovement(),
            forward,
            right,
            this.previousCommand,
            this.persistentMemory,
            getMaxHorizontalSpeed(),
            getMaxVerticalSpeed(),
            getMaxAcceleration(),
            getInternalMedia()
        );
    }

    private Vec3 readRiderInputVector(ServerPlayer rider) {
        double x = -Mth.clamp(rider.xxa, -1.0f, 1.0f);
        double z = Mth.clamp(rider.zza, -1.0f, 1.0f);
        double y = 0.0;
        if (((LivingEntityJumpingAccessor) rider).hexwright$isJumping()) {
            y += 1.0;
        }
        if (HexwrightNetworking.isVehicleDescendHeld(rider)) {
            y -= 1.0;
        }
        return new Vec3(x, y, z);
    }


    private void applyMovementControllerEveryTick(ServerPlayer rider) {
        this.setDeltaMovement(flightDragStep(this.getDeltaMovement()));
        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    private static Vec3 flightDragStep(Vec3 v) {
        return sanitizeVelocity(v.scale(VehicleConfig.FLIGHT_DRAG));
    }

    private static Vec3 fallStep(Vec3 v) {
        return sanitizeVelocity(new Vec3(v.x * FALL_DRAG, v.y - GRAVITY_PER_TICK, v.z * FALL_DRAG));
    }

    private static Vec3 sanitizeVelocity(Vec3 v) {
        double x = Double.isFinite(v.x) ? v.x : 0.0;
        double y = Double.isFinite(v.y) ? v.y : 0.0;
        double z = Double.isFinite(v.z) ? v.z : 0.0;
        return new Vec3(x, y, z);
    }


    private boolean wasPredicting = false;
    private boolean awaitingAbsoluteResync = false;

    private float bankDegrees = 0.0f;
    private float bankDegreesO = 0.0f;

    public float getBankDegrees(float partialTicks) {
        return Mth.lerp(partialTicks, bankDegreesO, bankDegrees);
    }

    public double getBankPivotY() {
        return this.getY()
            + (VehicleConfig.VEHICLE_MODEL_HEIGHT_OFFSET + VehicleConfig.VEHICLE_MODEL_CENTRE_OFFSET) * getRenderScale();
    }

    private void updateBankAngle() {
        this.bankDegreesO = this.bankDegrees;
        float yawDelta = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        float target = Mth.clamp(
            -yawDelta * VehicleConfig.BANK_DEGREES_PER_YAW_DEGREE,
            -VehicleConfig.BANK_MAX_DEGREES,
            VehicleConfig.BANK_MAX_DEGREES
        );
        this.bankDegrees = Mth.lerp(VehicleConfig.BANK_SMOOTHING, this.bankDegrees, target);
    }

    private void clientTick() {
        boolean predicting = this.isControlledByLocalInstance();
        if (predicting) {
            clientPredictTick();
        } else {
            if (wasPredicting) {
                resyncAfterLosingControl();
            }
            clientInterpolateTick();
        }
        wasPredicting = predicting;
        updateBankAngle();
        clientCosmeticTick();
    }

    protected void clientCosmeticTick() {
    }

    private void resyncAfterLosingControl() {
        this.setDeltaMovement(Vec3.ZERO);
        this.awaitingAbsoluteResync = true;
        if (authoritativePos != null) {
            this.setPos(authoritativePos.x, authoritativePos.y, authoritativePos.z);
            this.syncPacketPositionCodec(authoritativePos.x, authoritativePos.y, authoritativePos.z);
            this.lerpX = authoritativePos.x;
            this.lerpY = authoritativePos.y;
            this.lerpZ = authoritativePos.z;
            this.lerpSteps = 0;
        }
    }

    private void clientPredictTick() {
        LivingEntity rider = getControllingPassenger();
        if (rider == null) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        syncYawToRider(rider);

        if (isStalled()) {
            this.setDeltaMovement(fallStep(this.getDeltaMovement()));
        } else {
            this.setDeltaMovement(flightDragStep(this.getDeltaMovement()));
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        reconcileWithServer();
    }

    public void applyServerReconciliation(Vec3 pos, Vec3 vel) {
        this.authoritativePos = pos;
        this.authoritativeVel = vel;
    }

    private void reconcileWithServer() {
        if (authoritativePos == null || authoritativeVel == null) {
            return;
        }

        Vec3 error = authoritativePos.subtract(this.position());
        double errSq = error.lengthSqr();

        if (errSq >= VehicleConfig.RECONCILE_SNAP_DISTANCE_SQ) {
            this.move(MoverType.SELF, error);
            this.setDeltaMovement(authoritativeVel);
            return;
        }
        if (errSq <= VehicleConfig.RECONCILE_DEADZONE_SQ) {
            return;
        }

        double factor = errSq >= VehicleConfig.RECONCILE_GENTLE_DISTANCE_SQ
            ? VehicleConfig.RECONCILE_STRONG_FACTOR
            : VehicleConfig.RECONCILE_GENTLE_FACTOR;

        this.move(MoverType.SELF, error.scale(factor));
        this.setDeltaMovement(this.getDeltaMovement().lerp(authoritativeVel, factor));
    }

    private void clientInterpolateTick() {
        if (lerpSteps <= 0) {
            return;
        }
        double newX = this.getX() + (lerpX - this.getX()) / lerpSteps;
        double newY = this.getY() + (lerpY - this.getY()) / lerpSteps;
        double newZ = this.getZ() + (lerpZ - this.getZ()) / lerpSteps;
        double yRotDelta = Mth.wrapDegrees(lerpYRot - this.getYRot());
        this.setYRot(this.getYRot() + (float) (yRotDelta / lerpSteps));
        this.setXRot(this.getXRot() + (float) ((lerpXRot - this.getXRot()) / lerpSteps));
        this.lerpSteps--;
        this.setPos(newX, newY, newZ);
        this.setRot(this.getYRot(), this.getXRot());
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        if (awaitingAbsoluteResync) {
            if (!teleport) {
                return;
            }
            awaitingAbsoluteResync = false;
        }
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = VehicleConfig.REMOTE_LERP_STEPS;
    }


    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return this.isAlive();
    }
}
