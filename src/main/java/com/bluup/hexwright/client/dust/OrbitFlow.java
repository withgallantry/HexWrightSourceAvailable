package com.bluup.hexwright.client.dust;

import org.joml.Vector3f;

final class OrbitFlow implements DustFlowField {

    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final float EPSILON = 0.05f;

    private final Vector3f noise = new Vector3f();
    private final Vector3f here = new Vector3f();
    private final Vector3f ahead = new Vector3f();

    static float angularSpeed(DustFrame frame) {
        return DustConfig.orbitSpeed * frame.orbitSpeedScale
            / Math.max(DustConfig.orbitRadius * frame.orbitRadiusScale, 0.3f);
    }

    @Override
    public void sample(DustFrame frame, DustGrain grain, Vector3f out) {
        int streams = Math.max(DustConfig.orbitStreams, 1);
        int stream = Math.min((int) (grain.current * streams), streams - 1);
        float streamPhase = stream * 2.1f;

        float behind = (float) Math.pow(grain.lead, 1.4);
        float theta = frame.orbitHeadAngle + stream * (TWO_PI / streams)
            - behind * DustConfig.orbitStreamLength * TWO_PI;

        path(frame, theta, streamPhase, here);
        path(frame, theta + EPSILON, streamPhase, ahead);
        float pathScale = angularSpeed(frame) / EPSILON;
        float pvx = (ahead.x - here.x) * pathScale;
        float pvy = (ahead.y - here.y) * pathScale;
        float pvz = (ahead.z - here.z) * pathScale;

        float taper = 1.0f - 0.6f * behind;
        float tube = DustConfig.orbitTubeRadius * taper * (float) Math.sqrt(grain.spread)
            * (0.5f + 0.5f * frame.orbitRadiusScale);
        float twist = grain.phase + frame.time * 0.04f + behind * 6.0f;
        float across = (float) Math.cos(twist) * tube;
        float up = (float) Math.sin(twist) * tube;
        float tx = here.x + (float) Math.cos(theta) * across;
        float ty = here.y + up;
        float tz = here.z + (float) Math.sin(theta) * across;

        float cx = (tx - grain.x) * DustConfig.orbitPull;
        float cy = (ty - grain.y) * DustConfig.orbitPull;
        float cz = (tz - grain.z) * DustConfig.orbitPull;
        float correctionSq = cx * cx + cy * cy + cz * cz;
        if (correctionSq > 0.81f) {
            float scale = 0.9f / (float) Math.sqrt(correctionSq);
            cx *= scale;
            cy *= scale;
            cz *= scale;
        }

        DustFlowField.turbulence(frame, grain, noise);
        float turbulence = DustConfig.orbitTurbulence * (0.5f + grain.turbulence);
        float follow = DustConfig.orbitFollow;
        out.set(
            pvx + cx + noise.x * turbulence + frame.casterVx * follow,
            pvy + cy + noise.y * turbulence + frame.casterVy * follow,
            pvz + cz + noise.z * turbulence + frame.casterVz * follow
        );
    }

    private static void path(DustFrame frame, float theta, float streamPhase, Vector3f out) {
        float t = frame.time;
        float scale = frame.orbitRadiusScale;
        float radius = DustConfig.orbitRadius * scale * (1.0f + DustConfig.orbitRadiusWeave
            * (0.65f * (float) Math.sin(2.0f * theta + t * 0.013f + streamPhase)
            + 0.35f * (float) Math.sin(3.0f * theta - t * 0.021f)));
        float height = DustConfig.orbitHeightSpread * (0.35f + 0.65f * scale)
            * (0.7f * (float) Math.sin(theta + t * 0.017f + streamPhase)
            + 0.3f * (float) Math.sin(3.0f * theta - t * 0.023f + streamPhase));
        out.set((float) Math.cos(theta) * radius, height, (float) Math.sin(theta) * radius);
    }

    static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}
