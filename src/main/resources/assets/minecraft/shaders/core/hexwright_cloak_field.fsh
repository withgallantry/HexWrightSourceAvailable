#version 150


uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;

uniform mat4 ProjMat;
uniform vec2 ScreenSize;
uniform float GameTime;
uniform vec3 BoxBase;
uniform float HalfWidth;
uniform float Height;
uniform vec3 WorldEast;
uniform vec3 WorldUp;
uniform vec3 WorldSouth;
uniform float Inside;
uniform float Seed;
uniform float DepthReady;
uniform float Rise;
uniform float Fast;
uniform vec4 Ripple0;
uniform vec4 Ripple1;
uniform vec4 Ripple2;
uniform vec4 Ripple3;
uniform vec4 Swell;

in vec3 viewPos;

out vec4 fragColor;

const vec3 TINT = vec3(0.70, 0.62, 1.0);

const float WARP_PX = 3.5;

const float SWEEP_SECONDS = 7.0;
const float SWEEP_WIDTH = 0.06;
const float SWEEP_GLOW = 0.08;
const vec3 SLATE_CORE = vec3(0.95, 0.84, 1.0);
const float RIPPLE_REACH = 8.0;
const float RIPPLE_WAVELENGTH = 1.1;
const float RIPPLE_SPREAD_START = 0.8;
const float RIPPLE_SPREAD_END = 2.2;
const float RIPPLE_GLOW = 0.3;
const float RIPPLE_PUSH_PX = 14.0;
const float CHROMA_SHARE = 0.3;
const float SWELL_WAVELENGTH = 0.3;
const float SWELL_MIN_WAVELENGTH = 3.0;
const float SWELL_SPREAD = 0.8;
const float SWELL_GLOW = 0.05;
const float SWELL_PUSH_PX = 7.0;

const float RISE_LINE = 0.12;
const float RISE_TRAIL = 0.8;
const float RISE_LEAD = 0.25;
const float RISE_ROLL = 0.5;
const float RISE_ECHO = 1.6;
const float RISE_ECHO_WIDTH = 0.3;
const float RISE_PUSH_PX = 10.0;

const float SHIELD_TINT = 0.3;
const float TINT_LUMA_SCALE = 1.46;
const vec3 BASE_COLOR = vec3(0.62, 0.32, 1.0);
const float BASE_CORE = 0.6;
const float BASE_LINE_BLOCKS = 0.12;
const float BASE_GLOW_BLOCKS = 0.5;
const float BASE_GLOW = 0.25;
const float BASE_FAR_SHARE = 0.5;
const float BASE_OCCLUSION_SLACK = 0.5;

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

float luma(vec3 c) {
    return dot(c, vec3(0.3, 0.59, 0.11));
}

vec3 scene(vec2 uv) {
    return textureLod(SceneSampler, clamp(uv, vec2(0.001), vec2(0.999)), 0.0).rgb;
}

bool occludes(vec2 uv, float depth) {
    return DepthReady > 0.5
        && textureLod(DepthSampler, clamp(uv, vec2(0.001), vec2(0.999)), 0.0).r < depth;
}

vec3 toBox(vec3 v) {
    return vec3(dot(v, WorldEast), dot(v, WorldUp), dot(v, WorldSouth));
}

vec3 fromBox(vec3 b) {
    return WorldEast * b.x + WorldUp * b.y + WorldSouth * b.z;
}

vec2 toScreen(vec3 dir) {
    dir.z = min(dir.z, -1e-3);
    vec4 clip = ProjMat * vec4(dir, 1.0);
    return clip.xy / clip.w * 0.5 + 0.5;
}

vec2 wobble(vec2 uv, float t) {
    return vec2(fbm(uv * 7.0 + vec2(t * 0.05, Seed)),
                fbm(uv * 7.0 + vec2(Seed * 1.7, -t * 0.04))) - 0.5;
}

float sweep(float t, float heightAbove, float roof) {
    float sweepAt = fract(t / SWEEP_SECONDS + Seed) * (1.0 + 2.0 * SWEEP_WIDTH) - SWEEP_WIDTH;
    float x = (heightAbove / Height - sweepAt) / SWEEP_WIDTH;
    return exp(-x * x) * (1.0 - roof);
}

float wavePacket(vec3 origin, float front, float wavelength, float spread, float pushPx, inout vec2 push) {
    vec3 out3 = viewPos - origin;
    float distance = length(out3);
    float x = distance - front;
    float envelope = exp(-x * x / (spread * spread));
    if (envelope < 0.01) {
        return 0.0;
    }
    float k = 6.2831853 / wavelength;
    float wavePx = wavelength * ProjMat[1][1] * ScreenSize.y / (2.0 * max(-viewPos.z, 0.1));
    pushPx = min(pushPx, wavePx * 0.16);
    float slope = (-sin(k * x) - cos(k * x) * 2.0 * x / (spread * spread * k)) * envelope;
    vec2 onScreen = (toScreen(viewPos + out3 / max(distance, 1e-3) * 0.1) - toScreen(viewPos)) * ScreenSize;
    push += onScreen / max(length(onScreen), 1e-4) * slope * pushPx;
    return max(cos(k * x), 0.0) * envelope;
}

float ripple(vec4 r, inout vec2 push) {
    if (r.w < 0.0 || r.w >= 1.0) {
        return 0.0;
    }
    float fade = (1.0 - r.w) * (1.0 - r.w);
    float travelled = 1.0 - fade;
    float spread = mix(RIPPLE_SPREAD_START, RIPPLE_SPREAD_END, r.w);
    return wavePacket(r.xyz, RIPPLE_REACH * travelled, RIPPLE_WAVELENGTH, spread,
        RIPPLE_PUSH_PX * fade, push) * fade;
}

float swell(inout vec2 push) {
    if (Swell.w < 0.0 || Swell.w >= 1.0) {
        return 0.0;
    }
    float wavelength = max(SWELL_MIN_WAVELENGTH, HalfWidth * SWELL_WAVELENGTH);
    float strength = sin(3.1415927 * Swell.w);
    return wavePacket(Swell.xyz, 2.0 * max(HalfWidth, Height) * Swell.w, wavelength,
        wavelength * SWELL_SPREAD, SWELL_PUSH_PX * strength, push) * strength;
}

vec3 parted(vec2 uv, vec2 split, vec2 px) {
    if (dot(split, split) < 0.25) {
        return scene(uv);
    }
    return vec3(scene(uv + split * px).r, scene(uv).g, scene(uv - split * px).b);
}

vec2 refracted(vec2 uv0, vec2 px, float t, float depth, float warpPx) {
    vec2 uv = uv0 + wobble(uv0, t) * 2.0 * warpPx * px;
    return occludes(uv, depth) ? uv0 : uv;
}

vec3 sceneViewPos(vec2 uv) {
    float ndc = textureLod(DepthSampler, uv, 0.0).r * 2.0 - 1.0;
    float z = -ProjMat[3][2] / (ndc + ProjMat[2][2]);
    vec2 xy = (uv * 2.0 - 1.0) * -z / vec2(ProjMat[0][0], ProjMat[1][1]);
    return vec3(xy, z);
}

vec3 shield(vec2 uv, vec2 split, vec2 px, float t, float depth) {
    vec2 through = Fast > 0.5 ? uv : refracted(uv, px, t, depth, WARP_PX);
    vec3 seen = parted(through, split, px);
    vec3 milky = mix(seen, vec3(luma(seen)), 0.25) * 0.9 + TINT * 0.1;
    return mix(milky, luma(milky) * TINT * TINT_LUMA_SCALE, SHIELD_TINT);
}

float footprint(float ahead) {
    return 2.0 * ahead / (ProjMat[1][1] * ScreenSize.y);
}

float baseLine(float heightAbove, float aaBlocks) {
    float h = abs(heightAbove);
    float core = 1.0 - smoothstep(0.0, max(BASE_LINE_BLOCKS, aaBlocks * 1.5), h);
    return core * BASE_CORE + exp(-h / BASE_GLOW_BLOCKS) * BASE_GLOW;
}

float farBaseLine(vec3 d, vec3 sceneAt) {
    vec3 eye = toBox(-BoxBase);
    vec3 ray = toBox(d);
    vec3 safe = mix(ray, vec3(1e-6), lessThan(abs(ray), vec3(1e-6)));
    vec3 low = (vec3(-HalfWidth, -Height, -HalfWidth) - eye) / safe;
    vec3 high = (vec3(HalfWidth, Height, HalfWidth) - eye) / safe;
    vec3 far = max(low, high);
    float exit = min(far.x, min(far.y, far.z));
    if (exit <= 0.0) {
        return 0.0;
    }
    vec3 exitPoint = d * exit;
    float ahead = -exitPoint.z;
    if (DepthReady > 0.5 && -sceneAt.z < ahead - BASE_OCCLUSION_SLACK) {
        return 0.0;
    }
    return baseLine(dot(exitPoint - BoxBase, WorldUp), footprint(ahead));
}

float riseOffset(float heightAbove, vec3 local, float t) {
    float front = mix(-1.0, Height + 2.5 * RISE_ECHO, smoothstep(0.0, 1.0, Rise));
    float around = atan(local.z, local.x);
    front += RISE_ROLL * (0.6 * sin(around * 7.0 + t * 5.0) + 0.4 * sin(around * 13.0 - t * 7.3 + Seed));
    return heightAbove - front;
}

float riseGlow(float dh) {
    float halo = exp(-max(dh, 0.0) / RISE_LEAD - max(-dh, 0.0) / RISE_TRAIL);
    float echo1 = (dh + RISE_ECHO) / RISE_ECHO_WIDTH;
    float echo2 = (dh + 2.0 * RISE_ECHO) / RISE_ECHO_WIDTH;
    return halo * 0.5 + 0.3 * exp(-echo1 * echo1) + 0.15 * exp(-echo2 * echo2);
}

vec3 rising(vec3 box, vec2 uv, float reveal, float glow, float line) {
    vec3 color = mix(scene(uv), box, reveal);
    color = mix(color, BASE_COLOR, clamp(glow, 0.0, 1.0));
    return mix(color, SLATE_CORE, line * 0.9);
}

void main() {
    float heightAbove = dot(viewPos - BoxBase, WorldUp);
    float aaBlocks = fwidth(heightAbove);
    float nearBase = baseLine(heightAbove, aaBlocks);

    vec2 px = 1.0 / ScreenSize;
    vec2 uv0 = gl_FragCoord.xy * px;
    float t = GameTime * 1200.0;
    float depth = gl_FragCoord.z;

    bool outside = Inside < 0.5;
    vec3 sceneAt = vec3(0.0, 0.0, -1e6);
    if (outside && DepthReady > 0.5) {
        sceneAt = sceneViewPos(uv0);
    }

    vec3 local = toBox(viewPos - BoxBase);
    vec3 reach = abs(local - vec3(0.0, Height * 0.5, 0.0)) / vec3(HalfWidth, Height * 0.5, HalfWidth);
    vec3 faceAxis = reach.x >= reach.y && reach.x >= reach.z ? vec3(sign(local.x), 0.0, 0.0)
        : reach.z >= reach.y ? vec3(0.0, 0.0, sign(local.z))
        : vec3(0.0, 1.0, 0.0);
    float roof = faceAxis.y;

    float reveal = 1.0;
    float glow = 0.0;
    float line = 0.0;
    vec2 risen = uv0;
    if (Rise < 1.0) {
        float dh = riseOffset(heightAbove, local, t);
        if (dh > 8.0 * RISE_LEAD) {
            discard;
        }
        float lineWidth = max(RISE_LINE, aaBlocks * 1.5);
        reveal = 1.0 - smoothstep(-lineWidth, lineWidth, dh);
        glow = riseGlow(dh);
        line = 1.0 - smoothstep(0.0, lineWidth, abs(dh));
        risen = uv0 + wobble(uv0 * 3.0, t * 6.0) * 4.0 * glow * RISE_PUSH_PX * px;
        risen = occludes(risen, depth) ? uv0 : risen;
    }

    vec3 d = normalize(viewPos);
    vec3 normal = fromBox(faceAxis);
    float facing = dot(normal, -d);

    vec2 push = vec2(0.0);
    float rings = ripple(Ripple0, push) + ripple(Ripple1, push)
        + ripple(Ripple2, push) + ripple(Ripple3, push);
    float swelling = swell(push);
    vec2 pushed = uv0 + push * px;
    vec2 split = push * CHROMA_SHARE;
    float rim = pow(1.0 - abs(facing), 3.0);

    if (!outside) {
        vec2 through = Fast > 0.5 ? pushed : refracted(pushed, px, t, depth, WARP_PX);
        vec3 color = parted(through, split, px)
            + TINT * (0.025 + rim * 0.06 + rings * RIPPLE_GLOW * 0.6 + swelling * SWELL_GLOW
                + sweep(t, heightAbove, roof) * SWEEP_GLOW * 0.5)
            + BASE_COLOR * nearBase;
        if (Rise < 1.0) {
            color = rising(color, risen, reveal, glow, line);
        }
        fragColor = vec4(color, 1.0);
        return;
    }
    if (facing < 0.0) {
        discard;
    }

    vec3 color = shield(pushed, split, px, t, depth);
    float shimmer = 0.6 + 0.4 * sin(t * 1.3 + dot(uv0, vec2(40.0)) + Seed);
    color += TINT * pow(1.0 - facing, 5.0) * shimmer * 0.05;
    color += TINT * rim * 0.22;
    color += TINT * (rings * RIPPLE_GLOW + swelling * SWELL_GLOW);
    color += BASE_COLOR * (nearBase + farBaseLine(d, sceneAt) * BASE_FAR_SHARE);
    color += TINT * sweep(t, heightAbove, roof) * SWEEP_GLOW;
    if (Rise < 1.0) {
        color = rising(color, risen, reveal, glow, line);
    }

    fragColor = vec4(color, 1.0);
}
