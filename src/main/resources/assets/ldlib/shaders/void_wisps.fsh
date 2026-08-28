#version 150

// Wisp / Void Particles
// Dark purple void with drifting lavender/cyan magical motes
// and a soft smoky haze behind them.

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

float hash1(float n) {
    return fract(sin(n) * 43758.5453123);
}

void mainImage(out vec4 outColor, in vec2 fragCoord) {
    vec2 uv = fragCoord.xy / iResolution.xy;
    vec2 p = uv - 0.5;
    p.x *= iResolution.x / iResolution.y;

    float t = iTime * 0.22;
    float d = length(p);

    // Smoky void background
    vec2 st = uv * 3.0;
    vec2 q = vec2(fbm(st + vec2(0.0, t * 0.25)),
                  fbm(st + vec2(1.7, -t * 0.18)));
    float haze = fbm(st + q * 1.8);

    vec3 bg0 = vec3(0.020, 0.010, 0.040);
    vec3 bg1 = vec3(0.080, 0.025, 0.140);
    vec3 hazeColor = vec3(0.280, 0.120, 0.430);
    vec3 color = mix(bg0, bg1, 0.45 + 0.20 * (1.0 - d));
    color = mix(color, hazeColor, smoothstep(0.22, 0.90, haze) * 0.55);

    // Floating motes / wisps
    vec3 moteColor1 = vec3(0.780, 0.540, 0.980); // lavender
    vec3 moteColor2 = vec3(0.420, 0.860, 0.980); // faint cyan

    float sparkle = 0.0;
    vec3 particles = vec3(0.0);

    for (int i = 0; i < 18; ++i) {
        float fi = float(i);
        float seed = fi * 13.17;

        float px = hash1(seed + 1.0) * 1.8 - 0.9;
        float py = hash1(seed + 2.0) * 1.8 - 0.9;

        // Slow drifting movement
        px += 0.10 * sin(t * (0.9 + hash1(seed + 3.0) * 0.8) + seed);
        py += 0.18 * sin(t * (0.7 + hash1(seed + 4.0) * 0.7) + seed * 1.7);

        vec2 pos = vec2(px, py);

        float size = mix(0.015, 0.045, hash1(seed + 5.0));
        float dist = length(p - pos);
        float core = exp(-dist * dist / max(0.00012, size * size * 0.28));
        float glow = exp(-dist * dist / max(0.0008, size * size * 1.8));

        float blink = 0.55 + 0.45 * sin(t * 3.0 + seed * 2.3);
        vec3 c = mix(moteColor1, moteColor2, hash1(seed + 6.0));

        particles += c * (core * 0.65 + glow * 0.25) * blink;
        sparkle += glow * blink;
    }

    color += particles;

    // Subtle central magical presence
    float centerGlow = exp(-3.0 * d * d);
    color += vec3(0.20, 0.08, 0.28) * centerGlow * 0.35;

    // Gentle vignette
    float vignette = 1.0 - smoothstep(0.35, 1.1, d);
    color *= 0.78 + 0.22 * vignette;

    outColor = vec4(color, 1.0);
}

void main() {
    mainImage(
        fragColor,
        vec2(texCoord.x * iResolution.x, texCoord.y * iResolution.y)
    );
}
