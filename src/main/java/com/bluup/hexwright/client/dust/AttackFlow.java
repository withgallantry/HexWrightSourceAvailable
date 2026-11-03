package com.bluup.hexwright.client.dust;

import org.joml.Vector3f;

final class AttackFlow implements DustFlowField {

    private final Vector3f noise = new Vector3f();

    @Override
    public void sample(DustFrame frame, DustGrain grain, Vector3f out) {
        float dx = frame.attackDirX;
        float dy = frame.attackDirY;
        float dz = frame.attackDirZ;
        float reach = frame.attackReach;

        float gx = grain.x - frame.attackOriginX;
        float gy = grain.y - frame.attackOriginY;
        float gz = grain.z - frame.attackOriginZ;
        float along = gx * dx + gy * dy + gz * dz;
        float qx = gx - along * dx;
        float qy = gy - along * dy;
        float qz = gz - along * dz;
        float offset = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
        if (offset > 1.0e-4f) {
            qx /= offset;
            qy /= offset;
            qz /= offset;
        } else {
            qx = (float) Math.cos(grain.phase) * -dz;
            qy = 0.0f;
            qz = (float) Math.cos(grain.phase) * dx;
            float length = (float) Math.sqrt(qx * qx + qz * qz);
            if (length < 1.0e-4f) {
                qx = 1.0f;
                qz = 0.0f;
            } else {
                qx /= length;
                qz /= length;
            }
        }

        float personalReach = reach * (0.6f + 0.55f * grain.lead);
        float remaining = personalReach - along;
        float drive = OrbitFlow.clamp(remaining / (reach * 0.3f + 0.5f), -0.35f, 1.0f);
        float forward = frame.attackSpeed * (0.8f + 0.4f * grain.lead) * drive;
        if (along < 0.0f) {
            forward = Math.max(forward, frame.attackSpeed * 0.6f);
        }

        float tube = DustConfig.streamRadius * (0.4f + 1.6f * grain.spread * grain.spread)
            + DustConfig.streamFlare * Math.max(along, 0.0f);
        float tipZone = reach * 0.15f + 0.5f;
        if (remaining < tipZone) {
            tube *= 1.0f + DustConfig.streamImpactBloom * OrbitFlow.clamp(1.0f - remaining / tipZone, 0.0f, 1.0f);
        }
        float inward = OrbitFlow.clamp((tube - offset) * DustConfig.streamCompression, -0.6f, 0.2f);

        float swirl = DustConfig.streamSwirl * (0.5f + grain.turbulence);
        float sx = dy * qz - dz * qy;
        float sy = dz * qx - dx * qz;
        float sz = dx * qy - dy * qx;

        DustFlowField.turbulence(frame, grain, noise);
        float turbulence = DustConfig.streamTurbulence * (0.5f + grain.spread);
        float follow = frame.attackFollow;

        out.set(
            dx * forward + qx * inward + sx * swirl + noise.x * turbulence + frame.casterVx * follow,
            dy * forward + qy * inward + sy * swirl + noise.y * turbulence + frame.casterVy * follow,
            dz * forward + qz * inward + sz * swirl + noise.z * turbulence + frame.casterVz * follow
        );
    }
}
