package com.bluup.hexwright.client.dust;

import org.joml.Vector3f;

final class DisperseFlow implements DustFlowField {

    private final Vector3f noise = new Vector3f();

    @Override
    public void sample(DustFrame frame, DustGrain grain, Vector3f out) {
        float horizontal = (float) Math.sqrt(grain.x * grain.x + grain.z * grain.z);
        float ox = 0.0f;
        float oz = 0.0f;
        if (horizontal > 1.0e-3f) {
            ox = grain.x / horizontal * DustConfig.disperseOutward;
            oz = grain.z / horizontal * DustConfig.disperseOutward;
        }
        DustFlowField.turbulence(frame, grain, noise);
        float turbulence = DustConfig.disperseTurbulence * (0.5f + grain.turbulence);
        float drag = DustConfig.disperseDrag;
        out.set(
            grain.vx * drag + ox + noise.x * turbulence,
            grain.vy * drag - DustConfig.disperseSink + noise.y * turbulence * 0.5f,
            grain.vz * drag + oz + noise.z * turbulence
        );
    }
}
