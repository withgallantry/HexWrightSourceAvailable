#version 150

// Masterwork Gold Background
// Calm top-tier gold item backing.
// Designed to be used several times on one page without getting noisy.

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

#define NUM_OCTAVES 4

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

float softDiamond(vec2 p) {
    p = abs(p);
    float d = p.x + p.y;
    return 1.0 - smoothstep(0.22, 0.26, d);
}

void mainImage(out vec4 outColor, in vec2 fragCoord) {
    vec2 uv = fragCoord.xy / iResolution.xy;
    vec2 p = uv - 0.5;
    p.x *= iResolution.x / iResolution.y;

    float t = iTime;
    float r = length(p);

    vec3 darkGold = vec3(0.22, 0.13, 0.03);
    vec3 midGold  = vec3(0.66, 0.42, 0.10);
    vec3 richGold = vec3(0.90, 0.67, 0.22);
    vec3 paleGold = vec3(1.00, 0.88, 0.55);

    // Base brushed metal texture.
    vec2 metalUv = uv * vec2(7.0, 3.0);
    metalUv.x += t * 0.018;

    float metalNoise = fbm(metalUv);

    float brushed = sin((uv.y + metalNoise * 0.06) * 34.0 + t * 0.22);
    brushed = brushed * 0.5 + 0.5;
    brushed *= 0.16;

    vec3 color = mix(darkGold, midGold, 0.72 + brushed);

    // Gentle polished highlight sweep.
    float sweep = sin((uv.x * 3.2 - uv.y * 1.4) + t * 0.45);
    sweep = smoothstep(0.72, 1.0, sweep);
    color = mix(color, richGold, sweep * 0.22);

    // Soft central glow so the item sits nicely on top.
    float centerGlow = 1.0 - smoothstep(0.02, 0.42, r);
    color += richGold * centerGlow * 0.20;
    color += paleGold * centerGlow * centerGlow * 0.08;

    // Very faint engraved masterwork mark.
    float diamond = softDiamond(p);
    diamond *= 1.0 - smoothstep(0.10, 0.34, r);
    color += paleGold * diamond * 0.10;

    // Soft inner ring, restrained.
    float ring = smoothstep(0.34, 0.30, r) * smoothstep(0.18, 0.22, r);
    color += richGold * ring * 0.10;

    // Vignette to stop four of these from overwhelming the UI.
    float vignette = 1.0 - smoothstep(0.35, 0.72, r);
    color *= mix(0.82, 1.03, vignette);

    // Tiny prestige pulse.
    float pulse = 0.5 + 0.5 * sin(t * 0.8);
    color += paleGold * centerGlow * pulse * 0.025;

    outColor = vec4(color, 1.0);
}

void main() {
    mainImage(
        fragColor,
        vec2(texCoord.x * iResolution.x, texCoord.y * iResolution.y)
    );
}