package com.bluup.hexwright.client.render.emissive;

public final class EmissiveBloomConfig {
    public boolean enabled = true;

    public float framebufferScale = 0.6f;

    public float intensity = 2.0f;

    public float coreStrength = 1.0f;

    public float glowThreshold = 0.62f;

    public float glowSaturation = 1.0f;

    public float glowKnee = 0.6f;

    public boolean blockGlow = true;

    public boolean blockGlowDisableWhenShaderPackActive = true;

    public int blockGlowDistance = 100;

    public float blockGlowStrength = 0.6f;

    public float blockGlowLift = 0.002f;

    public float blockGlowDepthSlope = -0.1f;

    public float blockGlowDepthBias = -20.0f;

    public boolean blockGlowMipmap = true;

    public boolean blockGlowDebug = false;





    public float blurRadius = 2.0f;

    public boolean disableWhenShaderPackActive = false;

    public int debugMode = 0;
}
