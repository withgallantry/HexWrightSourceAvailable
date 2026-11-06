#version 150


uniform sampler2D DepthSampler;
uniform sampler2D OpaqueSampler;
uniform sampler3D RegionField;
uniform sampler3D VeilNoise;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 InverseProjMat;
uniform vec2 ScreenSize;
uniform float DepthReady;
uniform float OpaqueReady;
uniform float PixelAngle;

uniform vec3 Origin;
uniform vec3 GridSpan;
uniform vec3 GridRes;
uniform vec3 Centre;
uniform float Bottom;
uniform float Top;

uniform float Time;
uniform float Seed;
uniform vec3 BaseColour;
uniform vec3 EnergyColour;

uniform float Intensity;
uniform float Coverage;
uniform float Spread;
uniform float Veil;
uniform float Currents;
uniform float Interior;
uniform float Thickness;
uniform float Scale;
uniform float StrandWidth;
uniform float Rise;
uniform float Swirl;
uniform float Darkening;
uniform float Steps;

uniform float Activation;
uniform float Charge;
uniform float Gather;
uniform float Flash;
uniform float Fade;

in vec3 rayRel;

out vec4 fragColor;

const float NOISE_PERIOD = 8.0;
const float CREASE_GRADIENT = 1.16;
const float NOISE_SPREAD = 0.137;
const float GATHER_SCALE = 2.6;
const float FLOW_SCALE = 0.45;
const float FINE_SCALE = 0.42;
const float WARP_BROAD = 1.2;
const float WARP = 1.6;
const float GATHER_SLOPE = 0.45;
const float OUTER = 1.0;
const float REACH_OUT = 2.6;
const float REACH_IN = 4.5;
const float FINE_WIDTH = 0.6;
const float FINE_NEAR = 6.0;
const float FINE_LIGHT = 0.6;
const float REST = 0.7;
const float INSIDE = 0.5;
const float WHITE_HOT = 0.25;
const float THROUGH = 0.55;
const float SQRT_PI = 1.7724539;

float w;
float shift;
float cut;
float mistCut;
float present;
vec3 seed;
mat2 turn;

float sceneDistance(sampler2D depthTexture, vec2 uv) {
    float depth = texture(depthTexture, uv).r;
    if (depth >= 1.0) {
        return 1.0e9;
    }
    vec4 scene = InverseProjMat * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return length(scene.xyz / scene.w);
}

vec4 noise(vec3 q) {
    return texture(VeilNoise, q / NOISE_PERIOD);
}

float field(vec3 p) {
    vec3 uvw = (clamp(p / GridSpan, 0.0, 1.0) * (GridRes - 1.0) + 0.5) / GridRes;
    return texture(RegionField, uvw.zyx).r;
}

float creaseBlocks(float n, float cell) {
    return (2.0 * n - 1.0) * cell / CREASE_GRADIENT;
}

float erfApprox(float x) {
    float e = exp(-2.0 * 1.2286 * abs(x));
    return sign(x) * (1.0 - e) / (1.0 + e);
}

float sheetAcross(float c0, float c1, float width, float len) {
    float dc = c1 - c0;
    if (abs(dc) < 0.05 * width) {
        float c = 0.5 * (c0 + c1) / width;
        return len * exp(-c * c);
    }
    return len * width * 0.5 * SQRT_PI * (erfApprox(c1 / width) - erfApprox(c0 / width)) / dc;
}

struct Medium {
    vec3 mist;
    float veil;
    float mainLight;
    float fineLight;
    float mainCrease;
    float fineCrease;
    float clear;
};

Medium medium(vec3 p, float s, float footprint) {
    Medium m;
    m.mist = vec3(0.0);
    m.veil = 0.0;
    m.mainLight = 0.0;
    m.fineLight = 0.0;
    m.mainCrease = 0.0;
    m.fineCrease = 0.0;
    m.clear = 0.0;

    vec3 q = p - Centre;
    q.xz = turn * q.xz;
    q.y -= Time * Rise * Scale;
    vec3 cells = q / Scale;

    float span = max(Top - Bottom, 0.5);
    float soft = 0.2 * span + 0.25;
    float front = Bottom - soft + Charge * (span + 2.0 * soft);
    float filled = Gather * smoothstep(front + soft, front - soft, p.y);
    float lower = 0.05 * filled + 0.08 * Flash;

    vec4 a = noise(cells / GATHER_SCALE + seed + vec3(0.0, Time * Rise * 0.6 / GATHER_SCALE, 0.0));
    vec4 flow = noise(cells * FLOW_SCALE + (a.yzw - 0.5) * 2.0 * WARP_BROAD + seed.yzx
        + Time * vec3(0.013, 0.021, -0.017));
    float gathered = a.x + (flow.w - 0.5) * 0.4;
    float mistEdge = mistCut - lower - 0.06;
    if (gathered < mistEdge) {
        m.clear = (mistEdge - gathered) / GATHER_SLOPE * Scale;
        return m;
    }

    vec3 u = cells + (flow.xyz - 0.5) * 2.0 * WARP + seed.zxy;
    vec4 b = noise(u);
    float fineDetail = 1.0 - smoothstep(0.2, 0.5, footprint / (Scale * FINE_SCALE));
    vec4 f = vec4(0.5);
    if (fineDetail > 0.0) {
        f = mix(vec4(0.5), noise(u / FINE_SCALE + (b.zwx - 0.5) * 0.6 + seed), fineDetail);
    }

    float envelope = s > 0.0 ? exp(-(s * s) / (w * w * OUTER * OUTER)) : exp(s / (w * Interior));
    float bright = present * mix(REST, 1.0, filled) * (1.0 + 0.35 * Flash);

    float mistCover = smoothstep(mistEdge, mistEdge + 0.18, gathered);
    float detail = b.x * 0.6 + f.x * 0.4;
    float mistDetail = 1.0 - smoothstep(0.3, 0.8, footprint / Scale);
    float wisp = mix(0.25, smoothstep(0.45, 0.75, detail), mistDetail);
    float mist = mistCover * wisp * envelope;
    vec3 deep = BaseColour * BaseColour;
    m.mist = mix(deep, BaseColour, smoothstep(0.1, 0.9, wisp * mistCover)) * mist * Veil * bright;
    m.veil = mist * present;

    float cover = smoothstep(cut - lower - 0.06, cut - lower + 0.12, gathered);
    m.mainCrease = creaseBlocks(b.y, Scale);
    m.fineCrease = creaseBlocks(f.y, Scale * FINE_SCALE);
    float lit = cover * envelope * bright * Currents;
    m.mainLight = lit * smoothstep(0.42, 0.58, b.z);
    float drawn = max(StrandWidth, 1.2 * footprint);
    float beside = m.mainCrease / (FINE_NEAR * drawn);
    m.fineLight = lit * FINE_LIGHT * exp(-beside * beside) * smoothstep(0.46, 0.58, f.z) * fineDetail;
    return m;
}

void main() {
    vec3 dir = normalize(rayRel);
    vec3 ro = -Origin;
    vec3 safe = mix(dir, vec3(1.0e-6), lessThan(abs(dir), vec3(1.0e-6)));
    vec3 inv = 1.0 / safe;
    vec3 ta = -ro * inv;
    vec3 tb = (GridSpan - ro) * inv;
    vec3 lo = min(ta, tb);
    vec3 hi = max(ta, tb);
    float tEnter = max(max(lo.x, lo.y), lo.z);
    float tExit = min(min(hi.x, hi.y), hi.z);
    if (length(rayRel) < tExit - 0.01) {
        discard;
    }
    float tStart = max(tEnter, 0.0);
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float tTranslucent = DepthReady > 0.5 ? sceneDistance(DepthSampler, uv) : 1.0e9;
    float tSolid = OpaqueReady > 0.5 ? max(sceneDistance(OpaqueSampler, uv), tTranslucent) : tTranslucent;
    float tEnd = min(tExit, tSolid);
    if (tEnd <= tStart) {
        discard;
    }

    float arrive = smoothstep(0.0, 1.0, Activation);
    present = smoothstep(0.0, 0.35, Activation) * Fade;
    w = Thickness * mix(2.4, 1.0, arrive) * (1.0 + 0.8 * Flash);
    shift = Thickness * 1.6 * Flash;
    float evaporate = (1.0 - Fade) * 0.15;
    float share = clamp(Coverage, 0.02, 0.98);
    cut = mix(0.95, 0.5 + NOISE_SPREAD * 0.66 * log((1.0 - share) / share), present) + evaporate;
    float mistShare = clamp(Coverage * Spread, 0.02, 0.98);
    mistCut = mix(0.95, 0.5 + NOISE_SPREAD * 0.66 * log((1.0 - mistShare) / mistShare), present) + evaporate;
    seed = vec3(Seed, Seed * 0.37, -Seed * 0.71);
    float angle = Time * Swirl;
    turn = mat2(cos(angle), sin(angle), -sin(angle), cos(angle));

    float reachOut = REACH_OUT * OUTER * w;
    float reachIn = REACH_IN * Interior * w;
    float inside = field(ro) - shift < 0.0 ? INSIDE : 1.0;
    float stride = clamp(0.35 * Scale * FINE_SCALE, 0.08, 0.45);
    stride = min(stride, 0.6 * w);

    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float t = tStart + jitter * stride;
    vec3 light = vec3(0.0);
    float transmit = 1.0;
    bool linked = false;
    float lastMain = 0.0;
    float lastFine = 0.0;
    float lastT = t;
    int budget = int(Steps);
    for (int i = 0; i < 256; i++) {
        if (i >= budget || t >= tEnd || transmit < 0.03) {
            break;
        }
        vec3 p = ro + dir * t;
        float s = field(p) - shift;
        if (s > reachOut || s < -reachIn) {
            t += (s > 0.0 ? s - reachOut : -s - reachIn) + 0.02;
            linked = false;
            continue;
        }
        float footprint = t * PixelAngle;
        float dt = max(stride, 1.5 * footprint);
        dt = max(dt, (tEnd - t) / float(max(budget - i, 1)));

        Medium m = medium(p, s, footprint);
        if (m.clear > 0.0) {
            t += max(dt, m.clear);
            linked = false;
            continue;
        }
        float near = smoothstep(0.15, 1.2, t) * inside;
        float drawn = max(StrandWidth, 1.2 * footprint);
        float sheets = 0.0;
        if (linked) {
            float len = t - lastT;
            sheets = (m.mainLight * sheetAcross(lastMain, m.mainCrease, drawn, len)
                + m.fineLight * sheetAcross(lastFine, m.fineCrease, FINE_WIDTH * drawn, len)) * (StrandWidth / drawn);
        }
        float seen = t > tTranslucent ? THROUGH : 1.0;
        light += transmit * near * seen * (m.mist * dt + EnergyColour * sheets);
        transmit *= exp(-(m.veil * dt + 0.5 * sheets * present) * near * 1.5);
        linked = true;
        lastMain = m.mainCrease;
        lastFine = m.fineCrease;
        lastT = t;
        t += dt;
    }

    vec3 exposed = light * Intensity;
    float peak = max(max(exposed.r, exposed.g), max(exposed.b, 1.0e-4));
    vec3 colour = mix(exposed * ((1.0 - exp(-peak)) / peak), 1.0 - exp(-exposed), WHITE_HOT);
    float alpha = Darkening * (1.0 - transmit);
    if (colour.r + colour.g + colour.b + alpha < 0.003) {
        discard;
    }
    fragColor = vec4(colour, alpha);
}
