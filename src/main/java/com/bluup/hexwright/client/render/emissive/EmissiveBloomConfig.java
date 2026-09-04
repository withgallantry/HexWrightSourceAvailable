package com.bluup.hexwright.client.render.emissive;

public final class EmissiveBloomConfig {
    public boolean enabled = true;

    public float framebufferScale = 0.25f;

    public float intensity = 0.6f;

    public float coreStrength = 0.55f;

    public float glowThreshold = 0.62f;

    public float glowSaturation = 0.25f;

    public float glowKnee = 0.12f;

    public boolean blockGlow = true;

    public boolean blockGlowDisableWhenShaderPackActive = true;

    public int blockGlowDistance = 48;

    public float blockGlowStrength = 1.0f;

    public float blockGlowLift = 0.0f;

    public float blockGlowDepthSlope = 0.0f;

    public float blockGlowDepthBias = -10.0f;

    public boolean blockGlowMipmap = true;

    public boolean blockGlowDebug = false;





    public float blurRadius = 1.4f;

    public boolean disableWhenShaderPackActive = false;

    public int debugMode = 0;
}
