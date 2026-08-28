package com.bluup.hexwright.client.render.emissive;

public final class EmissiveBloomConfig {
    public boolean enabled = true;

    public float framebufferScale = 0.25f;

    public float intensity = 0.6f;

    public float coreStrength = 0.55f;

    public float glowThreshold = 0.62f;

    public float glowSaturation = 0.25f;

    public float glowKnee = 0.12f;

    public float blurRadius = 1.4f;

    public boolean disableWhenShaderPackActive = true;

    public int debugMode = 0;
}
