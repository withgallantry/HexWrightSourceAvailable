package com.bluup.hexwright.client.render.emissive;

public record GlowParams(float strength, float threshold, float saturation) {
    public static final float INHERIT = -1.0f;

    public static final GlowParams DEFAULT = new GlowParams(1.0f, INHERIT, INHERIT);

    public GlowParams withStrength(float value) {
        return new GlowParams(value, threshold, saturation);
    }

    public float resolveThreshold(float configured) {
        return threshold < 0.0f ? configured : threshold;
    }

    public float resolveSaturation(float configured) {
        return saturation < 0.0f ? configured : saturation;
    }
}
