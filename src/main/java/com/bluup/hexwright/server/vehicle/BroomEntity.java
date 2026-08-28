package com.bluup.hexwright.server.vehicle;

import com.bluup.hexwright.server.item.HexwrightItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class BroomEntity extends VehicleEntity {

    public BroomEntity(EntityType<? extends BroomEntity> type, Level level) {
        super(type, level);
    }

    private BroomVariant variant() {
        return BroomVariant.byId(getVariant());
    }

    @Override
    public double getMaxHorizontalSpeed() {
        return BroomVariant.maxHorizontalSpeed(getQuality());
    }

    @Override
    public double getMaxVerticalSpeed() {
        return BroomVariant.maxVerticalSpeed(getQuality());
    }

    @Override
    public double getMaxAcceleration() {
        return BroomVariant.maxAcceleration(getQuality());
    }

    @Override
    public int getPassengerCapacity() {
        return VehicleConfig.BROOM_PASSENGER_CAPACITY;
    }

    @Override
    public double getMediaMultiplier() {
        return VehicleConfig.BROOM_MEDIA_MULTIPLIER;
    }

    @Override
    public long getMediaCapacity() {
        return BroomVariant.mediaCapacity(getQuality());
    }

    @Override
    public Item getItemForm() {
        return HexwrightItems.BROOM;
    }

    @Override
    public String variantNameKey() {
        return variant().nameKey();
    }

    @Override
    public double getRiderHeightOffset() {
        return VehicleConfig.BROOM_RIDER_HEIGHT_OFFSET;
    }

    @Override
    public double getRiderForwardOffset() {
        return VehicleConfig.BROOM_RIDER_FORWARD_OFFSET;
    }

    @Override
    public double getVisualTiltDegrees() {
        return VehicleConfig.BROOM_VISUAL_TILT_DEGREES;
    }

    @Override
    protected boolean packsAwayOnDismount() {
        return true;
    }
}
