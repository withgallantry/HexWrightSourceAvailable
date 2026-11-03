package com.bluup.hexwright.client.dust;

import org.joml.Vector3f;

public interface DustFlowField {

    void sample(DustFrame frame, DustGrain grain, Vector3f out);

    static void turbulence(DustFrame frame, DustGrain grain, Vector3f out) {
        float t = frame.time;
        float p = grain.phase;
        out.set(
            (float) (Math.sin(grain.y * 1.7f + t * 0.11f + p) * Math.cos(grain.z * 1.3f - t * 0.07f)),
            (float) (Math.sin(grain.x * 1.1f - t * 0.09f + p * 1.3f) * Math.cos(grain.z * 1.9f + t * 0.05f)),
            (float) (Math.sin(grain.x * 1.5f + t * 0.08f) * Math.cos(grain.y * 1.2f - t * 0.1f + p))
        );
    }
}
