package com.bluup.hexwright.client.staff_assembly;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public final class FlightLeanModifier extends AbstractModifier {
    private static final double REFERENCE_SPEED = 0.6;
    private static final float MAX_TRAVEL_PITCH = (float) Math.toRadians(18.0);
    private static final float MAX_CLIMB_PITCH = (float) Math.toRadians(10.0);
    private static final float MAX_ROLL = (float) Math.toRadians(14.0);
    private static final float SMOOTHING = 0.2f;

    private float pitch;
    private float roll;
    private float prevPitch;
    private float prevRoll;

    void update(double forward, double right, double up) {
        float targetPitch = Mth.clamp(
            -normalise(forward) * MAX_TRAVEL_PITCH + normalise(up) * MAX_CLIMB_PITCH,
            -(MAX_TRAVEL_PITCH + MAX_CLIMB_PITCH),
            MAX_TRAVEL_PITCH + MAX_CLIMB_PITCH);
        float targetRoll = -normalise(right) * MAX_ROLL;

        prevPitch = pitch;
        prevRoll = roll;
        pitch += (targetPitch - pitch) * SMOOTHING;
        roll += (targetRoll - roll) * SMOOTHING;
    }

    void relax() {
        update(0.0, 0.0, 0.0);
    }

    private static float normalise(double speed) {
        return (float) Mth.clamp(speed / REFERENCE_SPEED, -1.0, 1.0);
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type,
                                         float tickDelta, @NotNull Vec3f value0) {
        Vec3f base = super.get3DTransform(modelName, type, tickDelta, value0);
        if (type != TransformType.ROTATION || !modelName.equals("body")) {
            return base;
        }

        return base.add(new Vec3f(
            Mth.lerp(tickDelta, prevPitch, pitch),
            0f,
            Mth.lerp(tickDelta, prevRoll, roll)));
    }
}
