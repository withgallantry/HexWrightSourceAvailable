package com.bluup.hexwright.server.vehicle;

import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class FlightExecutionContext {

    private final Vec3 riderInput;
    private final Vec3 vehiclePosition;
    private final Vec3 vehicleVelocity;
    private final Vec3 riderForward;
    private final Vec3 riderRight;
    private final Vec3 previousCommand;
    private final ListIota persistentMemory;
    private final double maxHorizontalSpeed;
    private final double maxVerticalSpeed;
    private final double maxAcceleration;
    private final long internalMedia;

    public FlightExecutionContext(
        Vec3 riderInput,
        Vec3 vehiclePosition,
        Vec3 vehicleVelocity,
        Vec3 riderForward,
        Vec3 riderRight,
        Vec3 previousCommand,
        ListIota persistentMemory,
        double maxHorizontalSpeed,
        double maxVerticalSpeed,
        double maxAcceleration,
        long internalMedia
    ) {
        this.riderInput = riderInput;
        this.vehiclePosition = vehiclePosition;
        this.vehicleVelocity = vehicleVelocity;
        this.riderForward = riderForward;
        this.riderRight = riderRight;
        this.previousCommand = previousCommand;
        this.persistentMemory = persistentMemory;
        this.maxHorizontalSpeed = maxHorizontalSpeed;
        this.maxVerticalSpeed = maxVerticalSpeed;
        this.maxAcceleration = maxAcceleration;
        this.internalMedia = internalMedia;
    }

    public Vec3 getRiderInput() {
        return riderInput;
    }

    public Vec3 getVehiclePosition() {
        return vehiclePosition;
    }

    public Vec3 getPreviousCommand() {
        return previousCommand;
    }

    public ListIota getPersistentMemory() {
        return persistentMemory;
    }

    public long getInternalMedia() {
        return internalMedia;
    }

    public Vec3 getVehicleVelocity() {
        return vehicleVelocity;
    }

    public Vec3 getRiderForward() {
        return riderForward;
    }

    public Vec3 getRiderRight() {
        return riderRight;
    }

    public double getMaxHorizontalSpeed() {
        return maxHorizontalSpeed;
    }

    public double getMaxVerticalSpeed() {
        return maxVerticalSpeed;
    }

    public double getMaxAcceleration() {
        return maxAcceleration;
    }

    public double getClimbSpeed() {
        return vehicleVelocity.y;
    }

    public double getGroundSpeed() {
        return Math.sqrt(vehicleVelocity.x * vehicleVelocity.x + vehicleVelocity.z * vehicleVelocity.z);
    }

    public ListIota toLimitsList() {
        return new ListIota(List.of(
            new DoubleIota(maxHorizontalSpeed),
            new DoubleIota(maxVerticalSpeed),
            new DoubleIota(maxAcceleration)
        ));
    }

    public ListIota toContextList() {
        List<Iota> values = List.of(
            new Vec3Iota(riderInput),
            new Vec3Iota(riderForward),
            new Vec3Iota(riderRight),
            new Vec3Iota(vehicleVelocity),
            new DoubleIota(getClimbSpeed()),
            new DoubleIota(getGroundSpeed()),
            new Vec3Iota(previousCommand),
            new DoubleIota((double) internalMedia),
            persistentMemory
        );
        return new ListIota(values);
    }
}
