#version 150


uniform sampler2D PortalSampler;
uniform sampler2D SceneSampler;

uniform vec2 ScreenSize;
uniform float GameTime;
uniform vec2 WindowSize;
uniform float Progress;
uniform float ViewReady;
uniform float SceneReady;
uniform float Muted;
uniform float Round;

in vec2 localPos;

out vec4 fragColor;

const vec3 RIM_COLOR = vec3(0.66, 0.38, 1.0);
const vec3 RIM_LIGHT = vec3(0.86, 0.68, 1.0);
const vec3 VOID_COLOR_A = vec3(0.10, 0.03, 0.22);
const vec3 VOID_COLOR_B = vec3(0.32, 0.12, 0.55);

const float FRONT_SOFT = 0.35;
const float VIEW_LAG = 0.5;
const float WARP_SPAN = 0.035;

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

float animTime() {
    return GameTime * 1200.0;
}

vec3 voidSwirl(vec2 p) {
    float t = animTime() * 0.25;
    vec2 c = p - WindowSize * 0.5;
    float ang = atan(c.y, c.x);
    float r = length(c);
    float swirl = noise(vec2(ang * 2.0 + t, r * 1.5 - t * 0.7));
    float pulse = 0.5 + 0.5 * sin(t * 0.8 + r * 2.0);
    return mix(VOID_COLOR_A, VOID_COLOR_B, swirl) + RIM_COLOR * pulse * 0.12;
}

vec2 warpDirection(vec2 p, float radial) {
    float t = animTime();
    vec2 c = p - WindowSize * 0.5;
    vec2 dir = c / max(length(c), 0.001);
    float pinch = sin(3.14159265 * clamp(radial, 0.0, 1.0));
    float ripple = sin(radial * 9.0 - t * 1.7);
    vec2 turb = vec2(noise(p * 1.6 + vec2(t * 0.28, t * 0.15)),
                     noise(p * 2.9 + vec2(-t * 0.19, t * 0.33))) - 0.5;
    return dir * (pinch * 0.85 + ripple * 0.25) + turb * 1.3;
}

void main() {
    vec2 p = localPos;

    float blocksPerPixel = max(fwidth(p.x) + fwidth(p.y), 1e-5);
    float paneSpanPixels = clamp((WindowSize.x + WindowSize.y) / blocksPerPixel, 16.0, 8192.0);
    float warpAmp = paneSpanPixels * WARP_SPAN * mix(1.0, 0.45, Muted);

    vec2 c = p - WindowSize * 0.5;
    float diag = length(WindowSize * 0.5) + 0.001;
    float ellipse = length(c / (WindowSize * 0.5));
    float radial = Round > 0.5 ? ellipse : length(c) / diag;
    float tear = noise(p * 2.4 + vec2(0.0, animTime() * 0.6)) - 0.5;

    float front = Progress * (1.05 + FRONT_SOFT + VIEW_LAG) - radial + tear * 0.10;
    float presence = smoothstep(0.0, FRONT_SOFT, front) * smoothstep(0.0, 0.02, Progress);
    if (presence <= 0.004) {
        discard;
    }
    float show = ViewReady > 0.5 ? smoothstep(0.0, FRONT_SOFT, front - VIEW_LAG) : 0.0;
    float warp = presence * (1.0 - show);

    float borderDist = Round > 0.5
        ? (1.0 - ellipse) * min(WindowSize.x, WindowSize.y) * 0.5
        : min(min(p.x, p.y), min(WindowSize.x - p.x, WindowSize.y - p.y));
    if (borderDist < 0.0) {
        discard;
    }
    float borderBand = min(0.30, min(WindowSize.x, WindowSize.y) * 0.2);
    float borderFade = smoothstep(0.0, borderBand, borderDist);

    vec2 screenUV = gl_FragCoord.xy / ScreenSize;

    vec3 veil;
    float veilAlpha;
    if (SceneReady > 0.5) {
        vec2 offset = warpDirection(p, radial) * (warp * borderFade * warpAmp) / ScreenSize;
        veil = textureLod(SceneSampler, clamp(screenUV + offset, vec2(0.001), vec2(0.999)), 0.0).rgb;
        veil += RIM_COLOR * warp * 0.10;
        veilAlpha = clamp(warp * 1.5, 0.0, 1.0);
    } else {
        veil = voidSwirl(p);
        veilAlpha = warp * (0.30 + 0.55 * (1.0 - clamp(radial, 0.0, 1.0)));
    }

    vec3 scene = veil;
    if (ViewReady > 0.5) {
        scene = mix(veil, textureLod(PortalSampler, screenUV, 0.0).rgb, show);
    }

    float rim = 1.0 - Muted;
    float opening = step(0.001, Progress) * (1.0 - step(0.999, Progress));
    float tearRim = exp(-abs(front) * 12.0) * opening * rim;

    float pulse = 0.78 + 0.22 * sin(animTime() * 1.6 + (p.x + p.y) * 1.1);
    float halo = exp(-borderDist * 5.0) * pulse * presence * rim;
    float core = exp(-borderDist * 24.0) * pulse * presence * rim;

    vec3 color = scene
        + RIM_COLOR * (tearRim * 1.4 + halo * 0.45)
        + RIM_LIGHT * core * 0.85;
    float alpha = clamp(max(max(veilAlpha, show), max(tearRim, halo * 0.5 + core)), 0.0, 1.0);
    fragColor = vec4(color, alpha);
}
