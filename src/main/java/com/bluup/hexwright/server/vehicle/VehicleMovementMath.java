package com.bluup.hexwright.server.vehicle;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class VehicleMovementMath {

    private VehicleMovementMath() {
    }

    public static Vec3 solveThrottle(
        Vec3 currentVelocity,
        Vec3 localCommand,
        Vec3 horizontalForward,
        Vec3 horizontalRight,
        double maxHorizontalSpeed,
        double maxVerticalSpeed,
        double maxAcceleration
    ) {
        if (maxAcceleration <= 0.0) {
            return Vec3.ZERO;
        }
        Vec3 target = integrate(currentVelocity, localCommand, horizontalForward, horizontalRight,
            maxHorizontalSpeed, maxVerticalSpeed, maxAcceleration);
        return sanitize(target.subtract(currentVelocity).scale(1.0 / maxAcceleration));
    }

    public static Vec3 integrate(
        Vec3 currentVelocity,
        Vec3 localCommand,
        Vec3 horizontalForward,
        Vec3 horizontalRight,
        double maxHorizontalSpeed,
        double maxVerticalSpeed,
        double maxAcceleration
    ) {
        Vec3 command = clampCommand(localCommand);
        Vec3 targetVelocity = horizontalRight.scale(command.x * maxHorizontalSpeed)
            .add(0.0, command.y * maxVerticalSpeed, 0.0)
            .add(horizontalForward.scale(command.z * maxHorizontalSpeed));

        Vec3 appliedChange = clampMagnitude(targetVelocity.subtract(currentVelocity), maxAcceleration);
        return sanitize(currentVelocity.add(appliedChange));
    }

    public static Vec3 horizontalForward(float yawDegrees) {
        float yawRad = (float) Math.toRadians(yawDegrees);
        return new Vec3(-Mth.sin(yawRad), 0.0, Mth.cos(yawRad));
    }

    public static Vec3 horizontalRight(Vec3 forward) {
        return new Vec3(-forward.z, 0.0, forward.x);
    }

    private static Vec3 clampCommand(Vec3 raw) {
        double x = Mth.clamp(raw.x, -1.0, 1.0);
        double y = Mth.clamp(raw.y, -1.0, 1.0);
        double z = Mth.clamp(raw.z, -1.0, 1.0);
        double horizontalMagnitude = Math.sqrt(x * x + z * z);
        if (horizontalMagnitude > 1.0) {
            x /= horizontalMagnitude;
            z /= horizontalMagnitude;
        }
        return new Vec3(x, y, z);
    }

    private static Vec3 clampMagnitude(Vec3 vec, double max) {
        double lenSq = vec.lengthSqr();
        if (lenSq <= max * max || lenSq == 0.0) {
            return vec;
        }
        return vec.scale(max / Math.sqrt(lenSq));
    }

    private static Vec3 sanitize(Vec3 v) {
        double x = Double.isFinite(v.x) ? v.x : 0.0;
        double y = Double.isFinite(v.y) ? v.y : 0.0;
        double z = Double.isFinite(v.z) ? v.z : 0.0;
        return new Vec3(x, y, z);
    }
}
