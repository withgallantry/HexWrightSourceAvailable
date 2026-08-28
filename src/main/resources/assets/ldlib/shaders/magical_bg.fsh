#version 150

// Magical Menu Background with Transparent Edges
//
// Designed as a general menu background panel for Minecraft GUIs.
// Features:
// - deep purple magical smoke / nebula
// - faint arcane linework / sigils
// - soft transparency falloff around the edges
//
// IMPORTANT:
// This shader depends on alpha, so make sure the render path uses blending.
// If alpha is ignored by the renderer, the transparent edges will not show.

uniform vec2 iResolution;
uniform float iTime;

in vec2 texCoord;
out vec4 fragColor;

float random(in vec2 st) {
    return fract(sin(dot(st, vec2(12.9898, 78.233))) * 43758.5453123);
}

float noise(in vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);

    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));

    vec2 u = f * f * (3.0 - 2.0 * f);

    return mix(a, b, u.x)
         + (c - a) * u.y * (1.0 - u.x)
         + (d - b) * u.x * u.y;
}

#define NUM_OCTAVES 5

float fbm(in vec2 st) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100.0);
    mat2 rot = mat2(cos(0.5), sin(0.5),
                   -sin(0.5), cos(0.5));

    for (int i = 0; i < NUM_OCTAVES; ++i) {
        v += a * noise(st);
        st = rot * st * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

mat2 rot2(float a) {
    float s = sin(a);
    float c = cos(a);
    return mat2(c, -s, s, c);
}

float rectMask(vec2 uv, float feather) {
    float edge = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    return smoothstep(0.0, feather, edge);
}

float lineBand(float x, float width) {
    return 1.0 - smoothstep(0.0, width, abs(x));
}

void mainImage(out vec4 outColor, in vec2 fragCoord) {
    vec2 uv = fragCoord.xy / iResolution.xy;
    vec2 p = uv - 0.5;
    p.x *= iResolution.x / iResolution.y;

    float t = iTime * 0.26;
    float d = length(p);

    vec2 st = uv * 3.6;
    vec2 q = vec2(
        fbm(st + vec2(0.0, t * 0.35)),
        fbm(st + vec2(1.7, -t * 0.28))
    );

    vec2 r = vec2(
        fbm(st + q * 1.6 + vec2(1.7, 9.2) + 0.10 * t),
        fbm(st + q * 1.6 + vec2(8.3, 2.8) - 0.08 * t)
    );

    float f = fbm(st + r * 1.9);

    vec3 bgDark    = vec3(0.030, 0.010, 0.060);
    vec3 bgMid     = vec3(0.090, 0.020, 0.140);
    vec3 smokeA    = vec3(0.220, 0.070, 0.340);
    vec3 smokeB    = vec3(0.420, 0.150, 0.620);
    vec3 glowColor = vec3(0.740, 0.460, 0.980);
    vec3 cyanGlow  = vec3(0.430, 0.900, 0.980);

    float smoke = smoothstep(0.22, 0.90, f);
    float dense = smoothstep(0.52, 0.95, f + r.x * 0.18);
    float centerGlow = exp(-2.7 * d * d);

    vec3 color = mix(bgDark, bgMid, 0.45 + centerGlow * 0.22);
    color = mix(color, smokeA, smoke * 0.45);
    color = mix(color, smokeB, dense * 0.50);
    color += glowColor * centerGlow * 0.08;

    vec2 g = rot2(0.12 * sin(t * 0.7)) * p;
    float diamond = 1.0 - smoothstep(0.20, 0.23, abs(g.x) + abs(g.y));
    float diamondRing = smoothstep(0.16, 0.17, abs(g.x) + abs(g.y))
                      - smoothstep(0.22, 0.23, abs(g.x) + abs(g.y));

    float circleRing = smoothstep(0.26, 0.265, length(g))
                     - smoothstep(0.31, 0.315, length(g));

    float vLine = lineBand(g.x, 0.004) * smoothstep(0.04, 0.34, abs(g.y));
    float hLine = lineBand(g.y, 0.004) * smoothstep(0.04, 0.34, abs(g.x));

    float sigil = diamondRing * 0.9 + circleRing * 0.7 + vLine * 0.5 + hLine * 0.5;
    float sigilPulse = 0.70 + 0.30 * sin(iTime * 1.6 + f * 6.0);
    color += glowColor * sigil * 0.18 * sigilPulse;
    color += cyanGlow * diamond * 0.04 * (0.5 + 0.5 * sin(iTime * 1.2));

    float sparkField = 0.0;
    vec2 sparkUv = uv * 16.0;
    vec2 sparkCell = floor(sparkUv);
    vec2 sparkFrac = fract(sparkUv) - 0.5;

    float rnd = random(sparkCell);
    vec2 sparkOffset = vec2(
        sin(iTime * (0.8 + rnd) + rnd * 20.0),
        cos(iTime * (0.6 + rnd) + rnd * 14.0)
    ) * 0.12;

    float sparkDist = length(sparkFrac - sparkOffset);
    float spark = exp(-sparkDist * sparkDist * 65.0) * step(0.90, rnd);
    sparkField += spark;

    color += mix(glowColor, cyanGlow, rnd) * sparkField * 0.22;

    float alphaRect = rectMask(uv, 0.16);
    float cornerFade = 1.0 - smoothstep(0.45, 0.92, d * 1.35);
    float alpha = alphaRect * (0.78 + 0.22 * cornerFade);
    alpha *= 0.62 + smoke * 0.18 + centerGlow * 0.12;

    float vignette = 1.0 - smoothstep(0.30, 1.05, d);
    color *= 0.84 + 0.16 * vignette;

    outColor = vec4(color, alpha);
}

void main() {
    mainImage(
        fragColor,
        vec2(texCoord.x * iResolution.x, texCoord.y * iResolution.y)
    );
}
