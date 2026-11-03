package com.bluup.hexwright.client.dust;

import org.joml.Vector3f;

final class ReturnFlow implements DustFlowField {

    private final OrbitFlow orbit;
    private final Vector3f orbitSample = new Vector3f();

    ReturnFlow(OrbitFlow orbit) {
        this.orbit = orbit;
    }

    @Override
    public void sample(DustFrame frame, DustGrain grain, Vector3f out) {
        float distance = (float) Math.sqrt(grain.x * grain.x + grain.y * grain.y + grain.z * grain.z);
        float far = DustConfig.returnBlendFar;
        float near = DustConfig.returnBlendNear;
        float toOrbit = OrbitFlow.clamp((far - distance) / Math.max(far - near, 0.01f), 0.0f, 1.0f);
        toOrbit = toOrbit * toOrbit * (3.0f - 2.0f * toOrbit);

        float rx = 0.0f;
        float ry = 0.0f;
        float rz = 0.0f;
        if (toOrbit < 1.0f && distance > 1.0e-3f) {
            float pull = DustConfig.returnSpeed * OrbitFlow.clamp(distance / 4.0f, 0.35f, 1.0f);
            rx = -grain.x / distance * pull;
            ry = -grain.y / distance * pull;
            rz = -grain.z / distance * pull;

            float horizontal = (float) Math.sqrt(grain.x * grain.x + grain.z * grain.z);
            if (horizontal > 1.0e-3f) {
                float curl = DustConfig.returnCurl * (1.0f - OrbitFlow.clamp((distance - 2.0f) / 10.0f, 0.0f, 1.0f));
                rx += -grain.z / horizontal * curl;
                rz += grain.x / horizontal * curl;
            }
            rx += frame.casterVx * DustConfig.orbitFollow;
            ry += frame.casterVy * DustConfig.orbitFollow;
            rz += frame.casterVz * DustConfig.orbitFollow;
        }

        if (toOrbit > 0.0f) {
            orbit.sample(frame, grain, orbitSample);
            float keep = 1.0f - toOrbit;
            out.set(
                rx * keep + orbitSample.x * toOrbit,
                ry * keep + orbitSample.y * toOrbit,
                rz * keep + orbitSample.z * toOrbit
            );
        } else {
            out.set(rx, ry, rz);
        }
    }
}
