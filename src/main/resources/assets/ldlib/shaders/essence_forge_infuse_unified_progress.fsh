#version 150

// Essence Forge - Infuse Effect (Unified Quality Shader)
// Manual-progress pixel-art infuse effect for UI use.
//
// Uniforms:
//   uProgress : 0.0 -> 1.0, driven manually by the UI when Infuse is clicked
//   uQuality  : 0 = Sound, 1 = Fine, 2 = Exquisite, 3 = Masterwork
//
// Visual arc:
// - dense particles swirl inward
// - rushes in quickly, slows toward the centre
// - energy accumulates into a core
// - circular fusion pulse
// - fades out

uniform vec2 iResolution;
uniform float iTime;      // optional / unused, kept for compatibility
uniform float uProgress;  // 0.0 -> 1.0
uniform float uQuality;   // 0=Sound, 1=Fine, 2=Exquisite, 3=Masterwork

in vec2 texCoord;
out vec4 fragColor;

float hash11(float p) {
    return fract(sin(p * 127.1) * 43758.5453123);
}

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);

    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));

    vec2 u = f * f * (3.0 - 2.0 * f);

    return mix(a, b, u.x)
         + (c - a) * u.y * (1.0 - u.x)
         + (d - b) * u.x * u.y;
}

float easeOutQuint(float x) {
    x = clamp(x, 0.0, 1.0);
    return 1.0 - pow(1.0 - x, 5.0);
}

float easeInCubic(float x) {
    x = clamp(x, 0.0, 1.0);
    return x * x * x;
}

float gaussian(float x, float centre, float width) {
    float d = (x - centre) / max(width, 0.0001);
    return exp(-d * d);
}

float boxGlow(vec2 p, vec2 c, vec2 halfSize, float blur) {
    vec2 d = abs(p - c) - halfSize;
    vec2 q = max(d, 0.0);
    float outside = length(q);
    float inside = min(max(d.x, d.y), 0.0);
    float dist = outside + inside;
    return 1.0 - smoothstep(0.0, blur, dist);
}

float circleGlow(vec2 p, vec2 c, float r, float blur) {
    float d = length(p - c);
    return 1.0 - smoothstep(r, r + blur, d);
}

float circleRing(vec2 p, vec2 c, float r, float width) {
    float d = abs(length(p - c) - r);
    return 1.0 - smoothstep(width, width + 0.012, d);
}

void mainImage(out vec4 outColor, in vec2 fragCoord) {
    // Deliberate pixelation.
    float pixelSize = 3.0;
    vec2 virtualRes = max(vec2(1.0), floor(iResolution / pixelSize));
    vec2 grid = floor(fragCoord / pixelSize);
    vec2 uv = grid / virtualRes;

    vec2 p = uv - 0.5;
    float aspect = iResolution.x / iResolution.y;
    p.x *= aspect;

    float prog = clamp(uProgress, 0.0, 1.0);
    int quality = int(floor(clamp(uQuality, 0.0, 3.0) + 0.5));

    float coreBuild = smoothstep(0.05, 0.71, prog);
    float fusion    = gaussian(prog, 0.77, 0.050);
    float afterGlow = gaussian(prog, 0.86, 0.10);
    float fadeOut   = 1.0 - smoothstep(0.88, 1.0, prog);

    // Defaults: Exquisite
    vec3 outerColor = vec3(0.20, 0.06, 0.46);
    vec3 midColor   = vec3(0.48, 0.18, 0.86);
    vec3 coreColor  = vec3(0.94, 0.84, 1.00);
    vec3 sparkColor = vec3(0.78, 0.58, 1.00);

    int count = 104;
    int streaks = 22;
    float arriveBoostScale = 1.00;
    float streakGain = 0.22;
    float coreGain = 0.86;
    float fusionGain = 1.38;

    // 0 = Sound, 1 = Fine, 2 = Exquisite, 3 = Masterwork
    if (quality == 0) {
        outerColor = vec3(0.08, 0.18, 0.24);
        midColor   = vec3(0.20, 0.46, 0.58);
        coreColor  = vec3(0.84, 0.92, 0.96);
        sparkColor = vec3(0.54, 0.70, 0.80);
        count = 58;
        streaks = 14;
        arriveBoostScale = 0.80;
        streakGain = 0.16;
        coreGain = 0.74;
        fusionGain = 1.18;
    } else if (quality == 1) {
        outerColor = vec3(0.12, 0.15, 0.40);
        midColor   = vec3(0.36, 0.46, 0.90);
        coreColor  = vec3(0.90, 0.94, 1.00);
        sparkColor = vec3(0.64, 0.76, 1.00);
        count = 78;
        streaks = 18;
        arriveBoostScale = 0.90;
        streakGain = 0.19;
        coreGain = 0.80;
        fusionGain = 1.28;
    } else if (quality == 2) {
        outerColor = vec3(0.20, 0.06, 0.46);
        midColor   = vec3(0.48, 0.18, 0.86);
        coreColor  = vec3(0.94, 0.84, 1.00);
        sparkColor = vec3(0.78, 0.58, 1.00);
        count = 104;
        streaks = 22;
        arriveBoostScale = 1.00;
        streakGain = 0.22;
        coreGain = 0.86;
        fusionGain = 1.38;
    } else {
        outerColor = vec3(0.30, 0.16, 0.03);
        midColor   = vec3(0.96, 0.68, 0.18);
        coreColor  = vec3(1.00, 0.95, 0.78);
        sparkColor = vec3(1.00, 0.84, 0.40);
        count = 128;
        streaks = 26;
        arriveBoostScale = 1.25;
        streakGain = 0.26;
        coreGain = 0.94;
        fusionGain = 1.48;
    }

    vec3 color = vec3(0.0);
    float alpha = 0.0;

    // Pixel haze around the centre.
    float hazeNoise = noise(p * 5.0 + vec2(prog * 1.1, -prog * 0.7));
    float haze = circleGlow(p, vec2(0.0), 0.10, 0.34) * (0.16 + 0.16 * hazeNoise) * (0.30 + 0.70 * coreBuild);
    color += outerColor * haze * 0.34 + midColor * haze * 0.30;
    alpha = max(alpha, haze * 0.12);

    // Dense incoming pixel particles with strong swirl.
    const int MAX_COUNT = 128;
    vec2 snapScale = vec2(aspect / virtualRes.x, 1.0 / virtualRes.y);

    for (int i = 0; i < MAX_COUNT; ++i) {
        if (i >= count) continue;

        float fi = float(i);
        float seed = fi * 19.37 + 5.9;

        float ang0 = hash11(seed + 0.3) * 6.2831853;
        float ellipse = mix(0.72, 1.18, hash11(seed + 0.9));
        float swirlDir = hash11(seed + 7.8) < 0.5 ? -1.0 : 1.0;

        float startR = mix(0.76, 1.20, hash11(seed + 1.7));

        // Stagger particles, but keep the rush dense and early.
        float spawn = hash11(seed + 2.3) * 0.17;
        float local = clamp((prog - spawn) / max(0.001, 0.72 - spawn), 0.0, 1.0);

        // Quick rush, then strong easing as particles approach the centre.
        float travel = easeOutQuint(local);
        float radius = mix(startR, 0.0, travel);

        // Strong swirl at the outside, calming near the centre.
        float spin = swirlDir * (1.95 * pow(1.0 - local, 0.55) + 0.16 * sin(local * 8.0 + seed));
        float ang = ang0 + spin;

        vec2 dir = normalize(vec2(cos(ang), sin(ang) * ellipse));
        vec2 perp = vec2(-dir.y, dir.x);

        vec2 pos = dir * radius;
        pos += perp * (hash11(seed + 3.1) - 0.5) * 0.030 * (1.0 - travel);
        pos += dir * (hash11(seed + 4.7) - 0.5) * 0.040 * (1.0 - travel) * sin(local * 7.0 + seed * 1.6);

        // Snap particle centres to the virtual pixel grid.
        pos = floor(pos / snapScale + 0.5) * snapScale;

        float sz = mix(0.005, 0.015, hash11(seed + 5.6));
        float coreBox = boxGlow(p, pos, vec2(sz, sz), sz * 0.25 + 0.002);
        float glowBox = boxGlow(p, pos, vec2(sz * 1.9, sz * 1.9), sz * 0.90 + 0.010);

        float arriveBoost = 1.0 + arriveBoostScale * smoothstep(0.80, 1.0, local);
        float lifeFade = 1.0 - smoothstep(0.77, 0.93, prog);
        vec3 c = mix(outerColor, midColor, hash11(seed + 6.4));

        color += c * (coreBox * 0.74 + glowBox * 0.25) * arriveBoost * lifeFade;
        alpha = max(alpha, (coreBox * 0.54 + glowBox * 0.13) * lifeFade);
    }

    // Swirling feed bands to make the movement read more clearly.
    const int MAX_STREAKS = 26;
    for (int i = 0; i < MAX_STREAKS; ++i) {
        if (i >= streaks) continue;

        float fi = float(i);
        float seed = fi * 11.9 + 12.1;

        float ang0 = hash11(seed) * 6.2831853;
        float swirlDir = hash11(seed + 2.1) < 0.5 ? -1.0 : 1.0;
        float active = clamp((prog - hash11(seed + 1.7) * 0.15) / 0.60, 0.0, 1.0);
        float travel = easeOutQuint(active);
        float radius = mix(0.95, 0.06, travel);
        float ang = ang0 + swirlDir * 1.45 * pow(1.0 - active, 0.55);

        vec2 dir = vec2(cos(ang), sin(ang));
        vec2 perp = vec2(-dir.y, dir.x);
        vec2 centre = dir * radius;

        float along = dot(p - centre, dir);
        float across = dot(p - centre, perp);
        float strip = boxGlow(vec2(along, across), vec2(0.0), vec2(0.15, 0.008), 0.012);
        strip *= (1.0 - smoothstep(0.73, 0.89, prog));

        color += mix(outerColor, sparkColor, 0.55) * strip * streakGain;
        alpha = max(alpha, strip * 0.08);
    }

    // Central accumulation core.
    float coreRadius = mix(0.018, 0.084, easeInCubic(coreBuild));
    float coreGlow = boxGlow(p, vec2(0.0), vec2(coreRadius * 0.82, coreRadius * 0.68), coreRadius * 1.45 + 0.010);
    float coreHalo = circleGlow(p, vec2(0.0), coreRadius * 0.85, coreRadius * 1.6 + 0.030);

    color += outerColor * coreHalo * 0.16 * coreBuild;
    color += midColor   * coreHalo * 0.38 * coreBuild;
    color += coreColor  * coreGlow * coreGain * coreBuild;
    alpha = max(alpha, coreHalo * 0.18 * coreBuild);

    // Circular fusion event.
    float fusionCore = circleGlow(p, vec2(0.0), 0.012, 0.090) * fusion;
    float ringRadius = mix(0.026, 0.21, smoothstep(0.70, 0.82, prog));
    float ring = circleRing(p, vec2(0.0), ringRadius, 0.012) * fusion;

    float crossH = boxGlow(p, vec2(0.0), vec2(0.16, 0.007), 0.014) * fusion;
    float crossV = boxGlow(p, vec2(0.0), vec2(0.010, 0.12), 0.014) * fusion;

    // Four small orbiting spark nodes around the fusion.
    float orbitNodes = 0.0;
    for (int i = 0; i < 4; ++i) {
        float a = float(i) * 1.5707963 + prog * 2.4;
        vec2 nodePos = vec2(cos(a), sin(a)) * ringRadius;
        orbitNodes += circleGlow(p, nodePos, 0.010, 0.020) * fusion;
    }

    color += coreColor  * fusionCore * fusionGain;
    color += midColor   * ring * 0.54;
    color += sparkColor * (crossH + crossV) * 0.30;
    color += sparkColor * orbitNodes * 0.34;
    alpha = max(alpha, fusionCore * 0.98 + ring * 0.28 + orbitNodes * 0.16);

    // Afterglow.
    float settle = circleGlow(p, vec2(0.0), 0.09, 0.22) * afterGlow * 0.32;
    color += midColor * settle * 0.62 + outerColor * settle * 0.24;
    alpha = max(alpha, settle * 0.16);

    color *= fadeOut;
    alpha = clamp(alpha * fadeOut, 0.0, 1.0);

    outColor = vec4(color, alpha);
}

void main() {
    mainImage(
        fragColor,
        vec2(texCoord.x * iResolution.x, texCoord.y * iResolution.y)
    );
}
