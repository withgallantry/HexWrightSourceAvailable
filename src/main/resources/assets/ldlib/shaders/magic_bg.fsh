#version 150

// Deep purple magic smoke background for Minecraft.
// Safer/brighter version based closely on the original fbm shader structure.

uniform vec2 iResolution;
uniform float iTime;

in vec2 texCoord;
out vec4 fragColor;

float random(in vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
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

void mainImage(out vec4 outColor, in vec2 fragCoord) {
    vec2 uv = fragCoord.xy / iResolution.xy;

    // Keep coordinates similar to your original shader.
    vec2 st = uv * 3.0;

    // Centered coordinates for swirl/vignette.
    vec2 p = uv - 0.5;
    p.x *= iResolution.x / iResolution.y;
    float d = length(p);

    float t = iTime * 0.28;

    // Swirl the sampling space.
    float swirl = 0.9 / (1.0 + d * 5.0);
    st = rot2(swirl + sin(t + d * 7.0) * 0.12) * (st - vec2(1.5)) + vec2(1.5);

    // Domain-warped smoke, similar to the original.
    vec2 q = vec2(0.0);
    q.x = fbm(st + vec2(0.00, 0.12 * t));
    q.y = fbm(st + vec2(1.00, 0.50) - 0.10 * t);

    vec2 r = vec2(0.0);
    r.x = fbm(st + q * 1.4 + vec2(1.7, 9.2) + 0.16 * t);
    r.y = fbm(st + q * 1.4 + vec2(8.3, 2.8) - 0.13 * t);

    float f = fbm(st + r * 1.8);

    // Smoke masks.
    float smoke = smoothstep(0.22, 0.92, f);
    float wisps = smoothstep(0.48, 0.95, f + r.x * 0.35);
    float glow = 1.0 - smoothstep(0.0, 0.95, d);

    // Brighter deep purple palette.
    vec3 darkPurple = vec3(0.055, 0.015, 0.095);
    vec3 basePurple = vec3(0.150, 0.035, 0.240);
    vec3 smokePurple = vec3(0.360, 0.120, 0.540);
    vec3 magicPurple = vec3(0.720, 0.360, 0.950);

    vec3 color = darkPurple;

    // Make sure it never collapses to black.
    color = mix(color, basePurple, 0.55 + glow * 0.25);
    color = mix(color, smokePurple, smoke * 0.70);
    color += magicPurple * wisps * 0.20;
    color += magicPurple * glow * 0.08;

    // Gentle animated pulse.
    color *= 0.90 + 0.10 * sin(iTime * 0.8 + f * 6.0);

    // Very mild vignette, not enough to black out the square.
    float vignette = 1.0 - smoothstep(0.35, 1.15, d);
    color *= 0.82 + vignette * 0.18;

    outColor = vec4(color, 1.0);
}

void main() {
    mainImage(
        fragColor,
        vec2(texCoord.x * iResolution.x, texCoord.y * iResolution.y)
    );
}
