package com.bluup.hexwright.server.vehicle;

import net.minecraft.world.phys.Vec3;

public final class VehicleMediaCost {

    private VehicleMediaCost() {
    }

    public static long compute(
        Vec3 resultingVelocity,
        Vec3 acceleration,
        Vec3 previousAcceleration,
        double maxHorizontalSpeed,
        double maxVerticalSpeed,
        double maxAcceleration,
        double vehicleMediaMultiplier,
        double loadMultiplier
    ) {
        double horizontalSpeedFraction = Math.sqrt(
            resultingVelocity.x * resultingVelocity.x + resultingVelocity.z * resultingVelocity.z
        ) / maxHorizontalSpeed;
        double climbFraction = Math.max(resultingVelocity.y, 0.0) / maxVerticalSpeed;
        double changeFraction = acceleration.subtract(previousAcceleration).length() / maxAcceleration;

        double raw = VehicleConfig.HOVER_COST
            + VehicleConfig.CRUISE_COST_COEFFICIENT * horizontalSpeedFraction * horizontalSpeedFraction
            + VehicleConfig.CLIMB_COST_COEFFICIENT * climbFraction * climbFraction
            + VehicleConfig.MANOEUVRE_COST_COEFFICIENT * changeFraction * changeFraction;

        return Math.round(raw * vehicleMediaMultiplier * loadMultiplier);
    }
}
