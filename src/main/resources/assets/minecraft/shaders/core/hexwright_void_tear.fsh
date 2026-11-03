#version 150


uniform sampler2D SceneSampler;

uniform vec2 ScreenSize;
uniform float GameTime;
uniform vec2 TearSize;
uniform float Progress;
uniform float Seed;
uniform float SceneReady;

in vec2 localPos;

out vec4 fragColor;

const vec3 VOID_DEEP = vec3(0.016, 0.008, 0.035);
const vec3 VOID_CHURN = vec3(0.20, 0.07, 0.38);
const vec3 RIM_COLOR = vec3(0.66, 0.38, 1.0);
const vec3 RIM_LIGHT = vec3(0.86, 0.68, 1.0);

const float JAG_DEPTH = 0.42;
const float JAG_FREQ = 5.5;
const float WARP_BAND = 0.9;
const float WARP_SPAN = 0.05;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    return noise(p) * 0.6 + noise(p * 2.3) * 0.3 + noise(p * 4.7) * 0.1;
}

float animTime() {
    return GameTime * 1200.0;
}

float edgeProfile(float along, float side) {
    float lens = pow(max(0.0, 1.0 - along * along), 0.72);
    float torn = fbm(vec2(along * JAG_FREQ + Seed, side * 3.7 + Seed * 0.37));
    return lens * (1.0 - JAG_DEPTH * torn);
}

void main() {
    float blocksPerPixel = max(fwidth(localPos.x) + fwidth(localPos.y), 1e-5);
    float spanPixels = clamp((TearSize.x + TearSize.y) * 2.0 / blocksPerPixel, 16.0, 8192.0);
    float warpAmp = spanPixels * WARP_SPAN;

    if (Progress <= 0.001) {
        discard;
    }

    float openLong = smoothstep(0.0, 0.35, Progress);
    float openAcross = pow(Progress, 1.6);
    if (openLong <= 0.001 || openAcross <= 0.001) {
        discard;
    }

    vec2 n = localPos / TearSize;
    float along = n.x / openLong;
    float across = n.y / openAcross;
    if (abs(along) >= 1.0 + WARP_BAND) {
        discard;
    }

    float edge = edgeProfile(along, across >= 0.0 ? 1.0 : -1.0);
    float d = edge - abs(across);
    float beyondTip = max(0.0, abs(along) - 1.0);
    float dBlocks = d * TearSize.y * openAcross;
    float openBlocks = max(TearSize.y * openAcross, 0.001);

    float t = animTime();
    vec2 screenUV = gl_FragCoord.xy / ScreenSize;

    vec3 color;
    float alpha;

    if (d > 0.0) {
        float churn = fbm(vec2(along * 2.6 - t * 0.10, across * 1.9 + t * 0.07));
        float filament = pow(fbm(vec2(along * 7.0 + t * 0.22, across * 3.0 - t * 0.13)), 3.0);
        vec3 interior = mix(VOID_DEEP, VOID_CHURN, churn * 0.55) + RIM_COLOR * filament * 0.9;

        float pulse = 0.80 + 0.20 * sin(t * 2.1 + along * 4.0 + Seed);
        float halo = exp(-dBlocks / (openBlocks * 0.55)) * pulse;
        float core = exp(-dBlocks / (openBlocks * 0.14)) * pulse;

        color = interior + RIM_COLOR * halo * 0.75 + RIM_LIGHT * core * 1.15;
        alpha = 1.0;
    } else {
        float dOut = -length(vec2(beyondTip, d));
        float outer = exp(dOut / (WARP_BAND * 0.45));
        if (outer < 0.004) {
            discard;
        }
        vec2 dir = normalize(vec2(n.x * 0.35, across >= 0.0 ? 1.0 : -1.0));
        float ripple = 0.75 + 0.25 * sin(along * 8.0 - t * 2.3 + Seed);

        vec3 veil;
        if (SceneReady > 0.5) {
            vec2 offset = dir * (outer * ripple * warpAmp) / ScreenSize;
            veil = textureLod(SceneSampler, clamp(screenUV + offset, vec2(0.001), vec2(0.999)), 0.0).rgb;
        } else {
            veil = mix(VOID_DEEP, VOID_CHURN, fbm(vec2(along * 3.0 + t * 0.1, across * 2.0)));
        }
        float glow = exp(dOut / (WARP_BAND * 0.16));
        color = veil + RIM_COLOR * glow * 0.55;
        alpha = clamp(max(outer * 0.85, glow * 0.9), 0.0, 1.0);
    }

    fragColor = vec4(color, alpha);
}
