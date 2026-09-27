package com.bluup.hexwright.server.vehicle;

import com.bluup.hexwright.server.item.HexwrightItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class BroomEntity extends VehicleEntity {

    public BroomEntity(EntityType<? extends BroomEntity> type, Level level) {
        super(type, level);
    }

    private BroomVariant variant() {
        return BroomVariant.byId(getVariant());
    }

    @Override
    public double getMaxHorizontalSpeed() {
        return variant().maxHorizontalSpeed(getQuality());
    }

    @Override
    public double getMaxVerticalSpeed() {
        return variant().maxVerticalSpeed(getQuality());
    }

    @Override
    public double getMaxAcceleration() {
        return variant().maxAcceleration(getQuality());
    }

    @Override
    public int getPassengerCapacity() {
        return variant().passengerCapacity();
    }

    @Override
    public double getMediaMultiplier() {
        return VehicleConfig.BROOM_MEDIA_MULTIPLIER;
    }

    @Override
    public long getMediaCapacity() {
        return variant().mediaCapacity(getQuality());
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

    private BroomSeats.@Nullable Seat seat(int seatIndex) {
        BroomSeats.Seat seat = BroomSeats.seat(variant(), seatIndex);
        if (seat == null) {
            return null;
        }
        double tilt = Math.toRadians(getVisualTiltDegrees());
        double cos = Math.cos(tilt);
        double sin = Math.sin(tilt);
        return new BroomSeats.Seat(
            seat.sideways(),
            seat.height() * cos + seat.forward() * sin,
            seat.forward() * cos - seat.height() * sin
        );
    }

    @Override
    protected double seatForwardOffset(int seatIndex) {
        BroomSeats.Seat seat = seat(seatIndex);
        return seat == null ? super.seatForwardOffset(seatIndex) : seat.forward();
    }

    @Override
    protected double seatSidewaysOffset(int seatIndex) {
        BroomSeats.Seat seat = seat(seatIndex);
        return seat == null ? 0.0 : seat.sideways();
    }

    @Override
    protected double seatVerticalOffset(int seatIndex) {
        BroomSeats.Seat seat = seat(seatIndex);
        if (seat == null) {
            return 0.0;
        }
        double seatAboveOrigin = VehicleConfig.VEHICLE_MODEL_HEIGHT_OFFSET + seat.height();
        return seatAboveOrigin - VehicleConfig.SEATED_PLAYER_SEAT_HEIGHT - getRiderHeightOffset();
    }

    @Override
    public double getLoadMultiplier() {
        int passengers = Math.max(0, this.getPassengers().size() - 1);
        if (passengers >= 2) {
            return VehicleConfig.BROOM_LOAD_TWO_PASSENGERS;
        }
        return passengers == 1 ? VehicleConfig.BROOM_LOAD_ONE_PASSENGER : 1.0;
    }

    @Override
    protected boolean packsAwayOnDismount() {
        return true;
    }
}
