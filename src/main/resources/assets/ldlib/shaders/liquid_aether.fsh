#version 150

// Mana Pool / Liquid Aether
// A purple magical liquid look with ripples, depth, and soft glow.

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

void mainImage(out vec4 outColor, in vec2 fragCoord) {
    vec2 uv = fragCoord.xy / iResolution.xy;
    vec2 p = uv - 0.5;
    p.x *= iResolution.x / iResolution.y;

    float t = iTime * 0.42;
    float d = length(p);

    // Distorted coordinates for liquid motion
    vec2 st = uv * 4.0;
    vec2 warp = vec2(
        fbm(st + vec2(0.0, t * 0.35)),
        fbm(st + vec2(3.4, -t * 0.28))
    );

    vec2 flow = st + warp * 1.2;
    float n1 = fbm(flow + vec2(t * 0.12, -t * 0.08));
    float n2 = fbm(flow * 1.7 - vec2(t * 0.10, t * 0.13));

    float waves =
        0.40 * sin((uv.x + warp.x * 0.15) * 18.0 + t * 1.8) +
        0.35 * sin((uv.y + warp.y * 0.20) * 15.0 - t * 1.4) +
        0.25 * sin((uv.x + uv.y) * 14.0 + t * 1.2);

    float liquid = n1 * 0.65 + n2 * 0.35 + waves * 0.18;

    vec3 deep = vec3(0.040, 0.015, 0.090);
    vec3 base = vec3(0.150, 0.050, 0.250);
    vec3 mid  = vec3(0.330, 0.120, 0.500);
    vec3 glow = vec3(0.720, 0.360, 0.950);

    vec3 color = mix(deep, base, 0.45 + 0.25 * (1.0 - d));
    color = mix(color, mid, smoothstep(0.15, 0.85, liquid) * 0.75);

    // Bright ripple highlights
    float highlight = smoothstep(0.52, 0.92, liquid + waves * 0.12);
    color += glow * highlight * 0.22;

    // Soft caustic-like shimmer
    float shimmer = smoothstep(0.55, 0.90, sin(liquid * 10.0 + t * 2.0) * 0.5 + 0.5);
    color += vec3(0.55, 0.28, 0.82) * shimmer * 0.06;

    // Central luminous pool feeling
    float centerGlow = exp(-2.5 * d * d);
    color += glow * centerGlow * 0.08;

    float vignette = 1.0 - smoothstep(0.35, 1.05, d);
    color *= 0.80 + 0.20 * vignette;

    outColor = vec4(color, 1.0);
}

void main() {
    mainImage(
        fragColor,
        vec2(texCoord.x * iResolution.x, texCoord.y * iResolution.y)
    );
}
