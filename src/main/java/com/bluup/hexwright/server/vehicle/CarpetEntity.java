package com.bluup.hexwright.server.vehicle;

import com.bluup.hexwright.server.item.HexwrightItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CarpetEntity extends VehicleEntity {

    private static final String TAG_CHEST_ATTACHED = "ChestAttached";

    private static final EntityDataAccessor<Boolean> DATA_CHEST_ATTACHED =
        SynchedEntityData.defineId(CarpetEntity.class, EntityDataSerializers.BOOLEAN);

    private final ChestContainer chest = new ChestContainer();

    private class ChestContainer extends SimpleContainer {

        private final Set<Player> viewers = new HashSet<>();

        ChestContainer() {
            super(VehicleConfig.CARPET_CHEST_SIZE);
        }

        @Override
        public boolean stillValid(Player player) {
            return !CarpetEntity.this.isRemoved()
                && CarpetEntity.this.isChestAttached()
                && player.level() == CarpetEntity.this.level()
                && player.distanceToSqr(CarpetEntity.this) <= VehicleConfig.CARPET_CHEST_REACH_SQR;
        }

        @Override
        public void startOpen(Player player) {
            viewers.add(player);
        }

        @Override
        public void stopOpen(Player player) {
            viewers.remove(player);
        }

        void closeViewers() {
            for (Player viewer : new ArrayList<>(viewers)) {
                if (viewer instanceof ServerPlayer serverPlayer) {
                    serverPlayer.closeContainer();
                }
            }
            viewers.clear();
        }
    }

    public CarpetEntity(EntityType<? extends CarpetEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_CHEST_ATTACHED, false);
    }

    public boolean isChestAttached() {
        return this.entityData.get(DATA_CHEST_ATTACHED);
    }

    private void setChestAttached(boolean attached) {
        this.entityData.set(DATA_CHEST_ATTACHED, attached);
    }

    private CarpetVariant variant() {
        return CarpetVariant.byId(getVariant());
    }

    @Override
    public double getMaxHorizontalSpeed() {
        return CarpetVariant.maxHorizontalSpeed(getQuality());
    }

    @Override
    public double getMaxVerticalSpeed() {
        return CarpetVariant.maxVerticalSpeed(getQuality());
    }

    @Override
    public double getMaxAcceleration() {
        return CarpetVariant.maxAcceleration(getQuality());
    }

    @Override
    public int getPassengerCapacity() {
        return isChestAttached() ? 1 : VehicleConfig.CARPET_PASSENGER_CAPACITY;
    }

    @Override
    public double getMediaMultiplier() {
        return VehicleConfig.CARPET_MEDIA_MULTIPLIER;
    }

    @Override
    public long getMediaCapacity() {
        return VehicleConfig.CARPET_MEDIA_CAPACITY;
    }

    @Override
    public double getLoadMultiplier() {
        if (isChestAttached()) {
            return Mth.lerp(chestOccupancyProportion(), VehicleConfig.CARPET_LOAD_EMPTY_CHEST, VehicleConfig.CARPET_LOAD_FULL_CHEST);
        }
        if (this.getPassengers().size() >= 2) {
            return VehicleConfig.CARPET_LOAD_TWO_PASSENGERS;
        }
        return VehicleConfig.CARPET_LOAD_SINGLE_PASSENGER;
    }

    private double chestOccupancyProportion() {
        int total = chest.getContainerSize();
        if (total <= 0) {
            return 0.0;
        }
        int occupied = 0;
        for (int i = 0; i < total; i++) {
            if (!chest.getItem(i).isEmpty()) {
                occupied++;
            }
        }
        return occupied / (double) total;
    }

    @Override
    public Item getItemForm() {
        return HexwrightItems.CARPET;
    }

    @Override
    public String variantNameKey() {
        return variant().nameKey();
    }

    @Override
    public double getRiderHeightOffset() {
        return VehicleConfig.CARPET_RIDER_HEIGHT_OFFSET;
    }

    @Override
    protected double seatForwardOffset(int seatIndex) {
        if (seatIndex == 0) {
            return isChestAttached()
                ? VehicleConfig.CARPET_PILOT_FORWARD_OFFSET_WITH_CHEST
                : VehicleConfig.CARPET_PILOT_FORWARD_OFFSET;
        }
        return VehicleConfig.CARPET_PASSENGER_FORWARD_OFFSET;
    }

    @Override
    public double getRenderScale() {
        return VehicleConfig.CARPET_RENDER_SCALE;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(VehicleConfig.CARPET_CULLING_INFLATION);
    }


    private float ripplePhase = 0.0f;
    private float ripplePhaseO = 0.0f;
    private float rippleFlow = 0.0f;
    private float rippleFlowO = 0.0f;

    @Override
    protected void clientCosmeticTick() {
        rippleFlowO = rippleFlow;
        double movedX = this.getX() - this.xo;
        double movedZ = this.getZ() - this.zo;
        double speed = Math.sqrt(movedX * movedX + movedZ * movedZ);
        double top = Math.max(getMaxHorizontalSpeed(), 1.0e-4);
        float target = (float) Mth.clamp(speed / top, 0.0, 1.0);
        rippleFlow = Mth.lerp(VehicleConfig.CARPET_RIPPLE_FLOW_SMOOTHING, rippleFlow, target);

        ripplePhaseO = ripplePhase;
        double period = Mth.lerp(
            rippleFlow,
            VehicleConfig.CARPET_RIPPLE_IDLE_PERIOD_TICKS,
            VehicleConfig.CARPET_RIPPLE_FLIGHT_PERIOD_TICKS
        );
        ripplePhase = Mth.wrapDegrees(ripplePhase + (float) (360.0 / period));
    }

    public float getRipplePhase(float partialTicks) {
        return ripplePhaseO + Mth.wrapDegrees(ripplePhase - ripplePhaseO) * partialTicks;
    }

    public float getRippleFlow(float partialTicks) {
        return Mth.lerp(partialTicks, rippleFlowO, rippleFlow);
    }


    @Override
    public InteractionResult interactAt(Player player, Vec3 hitVec, InteractionHand hand) {
        if (!isChestAttached() || !player.getItemInHand(hand).isEmpty() || !isHitOnChestSide(hitVec)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            if (this.level().isClientSide) {
                return InteractionResult.SUCCESS;
            }
            ServerPlayer serverPlayer = player instanceof ServerPlayer sp ? sp : null;
            if (chest.isEmpty()) {
                detachChest(serverPlayer);
            } else if (serverPlayer != null) {
                serverPlayer.displayClientMessage(
                    Component.translatable("message.hexwright.vehicle.chest_not_empty").withStyle(ChatFormatting.YELLOW),
                    true);
            }
            return InteractionResult.CONSUME;
        }

        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            openChestMenu(serverPlayer);
        }
        return InteractionResult.CONSUME;
    }

    private void detachChest(@Nullable ServerPlayer player) {
        closeChestViewers();
        setChestAttached(false);
        chest.clearContent();
        ItemStack chestStack = new ItemStack(Items.CHEST);
        if (player != null) {
            player.getInventory().add(chestStack);
        }
        if (!chestStack.isEmpty()) {
            this.spawnAtLocation(chestStack);
        }
    }

    private boolean isHitOnChestSide(Vec3 hitVec) {
        Vec3 forward = VehicleMovementMath.horizontalForward(this.getYRot());
        return hitVec.x * forward.x + hitVec.z * forward.z < 0.0;
    }

    @Override
    protected boolean tryClaimSlot(Player player, ItemStack held) {
        List<Entity> passengers = this.getPassengers();

        if (held.is(Items.CHEST) && !isChestAttached()
            && passengers.size() < VehicleConfig.CARPET_PASSENGER_CAPACITY) {
            held.shrink(1);
            setChestAttached(true);
            return true;
        }

        if (!passengers.contains(player) && passengers.size() < getPassengerCapacity()) {
            player.startRiding(this);
            return true;
        }

        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {
            closeChestViewers();
        }
        super.remove(reason);
    }

    private void closeChestViewers() {
        chest.closeViewers();
    }

    private void openChestMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (containerId, playerInventory, p) -> ChestMenu.threeRows(containerId, playerInventory, chest),
            Component.translatable("container.hexwright.carpet_chest")
        ));
    }

    @Override
    protected void writeExtra(CompoundTag data) {
        data.putBoolean(TAG_CHEST_ATTACHED, isChestAttached());
        if (isChestAttached()) {
            NonNullList<ItemStack> items = NonNullList.withSize(chest.getContainerSize(), ItemStack.EMPTY);
            for (int i = 0; i < chest.getContainerSize(); i++) {
                items.set(i, chest.getItem(i));
            }
            VehicleData.setChest(data, items);
        }
    }

    @Override
    protected void readExtra(CompoundTag data, ServerLevel level) {
        setChestAttached(data.getBoolean(TAG_CHEST_ATTACHED));
        NonNullList<ItemStack> items = NonNullList.withSize(VehicleConfig.CARPET_CHEST_SIZE, ItemStack.EMPTY);
        VehicleData.loadChest(data, items);
        for (int i = 0; i < items.size() && i < chest.getContainerSize(); i++) {
            chest.setItem(i, items.get(i));
        }
    }
}
