#version 150

uniform vec2 iResolution;
uniform float iTime;

in vec2 texCoord;
out vec4 fragColor;

// Deep purple swirling magic smoke background.
// Intended for a magical item storage GUI/background.

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

#define NUM_OCTAVES 6

float fbm(in vec2 st) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100.0);

    mat2 rot = mat2(cos(0.5), sin(0.5),
                   -sin(0.5), cos(0.5));

    for (int i = 0; i < NUM_OCTAVES; i++) {
        v += a * noise(st);
        st = rot * st * 2.0 + shift;
        a *= 0.5;
    }

    return v;
}

mat2 rotate2D(float a) {
    float s = sin(a);
    float c = cos(a);
    return mat2(c, -s, s, c);
}

float smokeField(vec2 p, float t) {
    vec2 q = vec2(
        fbm(p + vec2(0.0, 0.08 * t)),
        fbm(p + vec2(5.2, 1.3) - 0.06 * t)
    );

    vec2 r = vec2(
        fbm(p + 2.0 * q + vec2(1.7, 9.2) + 0.05 * t),
        fbm(p + 2.0 * q + vec2(8.3, 2.8) - 0.04 * t)
    );

    return fbm(p + 3.0 * r);
}

void mainImage(out vec4 outColor, in vec2 fragCoord) {
    vec2 uv = fragCoord.xy / iResolution.xy;
    vec2 p = uv - 0.5;
    p.x *= iResolution.x / iResolution.y;

    float t = iTime * 0.45;
    float dist = length(p);

    // Subtle magical vortex motion.
    float swirl = 0.55 / (1.0 + 4.0 * dist);
    float twist = swirl + 0.12 * sin(t + dist * 8.0);

    vec2 flow = rotate2D(twist) * p;
    flow *= 3.2;

    // Layered domain-warped smoke.
    float s1 = smokeField(flow + vec2(0.0, 0.30 * t), t);
    float s2 = smokeField(flow * 1.6 - vec2(0.18 * t, -0.10 * t), t + 13.0);

    float smoke = s1 * 0.75 + s2 * 0.45;
    float wisps = smoothstep(0.38, 0.82, smoke);
    float dense = smoothstep(0.58, 0.95, s1);

    // Hexcasting-inspired deep purple palette.
    vec3 bgDark    = vec3(0.030, 0.010, 0.060);
    vec3 bgMid     = vec3(0.090, 0.020, 0.140);
    vec3 purple    = vec3(0.280, 0.080, 0.430);
    vec3 hexGlow   = vec3(0.560, 0.240, 0.780);
    vec3 highlight = vec3(0.760, 0.470, 0.980);

    float centerGlow = exp(-2.8 * dist * dist);

    vec3 color = mix(bgDark, bgMid, centerGlow * 0.65);
    color = mix(color, purple, wisps * 0.65);
    color = mix(color, hexGlow, dense * 0.45);

    // Thin brighter magical wisps.
    float brightWisps = smoothstep(0.72, 0.98, smoke) * (0.4 + 0.6 * centerGlow);
    color += highlight * brightWisps * 0.22;

    // Soft ambient magical haze.
    color += hexGlow * centerGlow * 0.08;

    // Vignette helps it sit behind item slots/UI.
    float vignette = 1.0 - smoothstep(0.35, 1.05, dist);
    color *= 0.60 + 0.40 * vignette;

    outColor = vec4(color, 1.0);
}

void main() {
    mainImage(
        fragColor,
        vec2(texCoord.x * iResolution.x, texCoord.y * iResolution.y)
    );
}
