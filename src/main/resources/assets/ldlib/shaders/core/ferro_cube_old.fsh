#version 150

#moj_import <fog.glsl>

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float GameTime;
uniform float DiscardThreshold;

in float vertexDistance;
in vec2 particleCoord;
in vec4 vertexColor;

out vec4 fragColor;

const float PI  = 3.14159265358979323846;
const float TAU = 6.28318530717958647692;

// ---------- Main tuning ----------
const float SPIN_PER_DAY = 42.0;          // ~28.5 seconds per full spin
const float RIPPLE_SPEED = 1.6;
const float SPIKE_CYCLES_PER_DAY = 120.0; // ~10 seconds between violent surges
const float BLOB_SIZE = 0.36; // 0.46
const float ROUNDING = 0.19;
const int   MAX_STEPS = 60;
const float MAX_DISTANCE = 8.0;
const float HIT_EPSILON = 0.0025;

const float CALM_RIPPLE_AMPLITUDE   = 0.060;
const float SURGE_RIPPLE_AMPLITUDE  = 1.095;
const float CALM_WAVE_AMPLITUDE     = 0.001;
const float SURGE_WAVE_AMPLITUDE    = 0.046;
const float SURGE_SPIKE_AMPLITUDE   = 0.05;

const vec3 COLOR_DARK   = vec3(0.055, 0.016, 0.105);
const vec3 COLOR_MID    = vec3(0.215, 0.055, 0.345);
const vec3 COLOR_LIGHT  = vec3(0.525, 0.165, 0.770);
const vec3 COLOR_HOT    = vec3(0.860, 0.520, 1.000);
const vec3 COLOR_SHINE  = vec3(0.970, 0.860, 1.000);

float saturate(float x) {
    return clamp(x, 0.0, 1.0);
}

vec3 rotateX(vec3 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec3(p.x, c * p.y - s * p.z, s * p.y + c * p.z);
}

vec3 rotateY(vec3 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec3(c * p.x + s * p.z, p.y, -s * p.x + c * p.z);
}

vec3 rotateZ(vec3 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec3(c * p.x - s * p.y, s * p.x + c * p.y, p.z);
}

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);

    float n000 = hash13(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));

    float nx00 = mix(n000, n100, u.x);
    float nx10 = mix(n010, n110, u.x);
    float nx01 = mix(n001, n101, u.x);
    float nx11 = mix(n011, n111, u.x);

    float nxy0 = mix(nx00, nx10, u.y);
    float nxy1 = mix(nx01, nx11, u.y);
    return mix(nxy0, nxy1, u.z);
}

float fbm3(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; ++i) {
        v += noise3(p) * a;
        p = p * 2.03 + vec3(7.1, -3.7, 4.4);
        a *= 0.5;
    }
    return v;
}

float sdRoundBox(vec3 p, vec3 b, float r) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r;
}

float surgePulse(float t) {
    float cycle = fract(t * SPIKE_CYCLES_PER_DAY);

    // Mostly calm, then a sudden violent spike window, then quickly back to calm.
    float rise = smoothstep(0.79, 0.845, cycle);
    float crest = 1.0 - smoothstep(0.90, 0.985, cycle);
    float pulse = rise * crest;

    // Sharpen the event so it feels sudden rather than gradual.
    return pow(saturate(pulse), 1.35);
}

float spikeField(vec3 p, float time, float surge) {
    if (surge <= 0.0001) {
        return 0.0;
    }

    // Preferred magnetic directions.
    vec3 d0 = normalize(vec3( 0.95,  1.15,  0.28));
    vec3 d1 = normalize(vec3(-1.10,  0.42,  0.82));
    vec3 d2 = normalize(vec3( 0.18, -0.92,  1.08));
    vec3 d3 = normalize(vec3(-0.74, -0.94, -0.48));
    vec3 d4 = normalize(vec3( 1.10, -0.18, -0.84));
    vec3 d5 = normalize(vec3(-0.28,  1.06, -0.96));

    d0 = rotateY(d0, time * 0.53);
    d1 = rotateX(d1, time * 0.46);
    d2 = rotateZ(d2, time * 0.39);
    d3 = rotateY(d3, -time * 0.35);
    d4 = rotateZ(d4, time * 0.61);
    d5 = rotateX(d5, -time * 0.57);

    vec3 dir = normalize(p + vec3(0.0001));

    float a0 = pow(saturate(dot(dir, d0)), 18.0);
    float a1 = pow(saturate(dot(dir, d1)), 18.0);
    float a2 = pow(saturate(dot(dir, d2)), 18.0);
    float a3 = pow(saturate(dot(dir, d3)), 18.0);
    float a4 = pow(saturate(dot(dir, d4)), 18.0);
    float a5 = pow(saturate(dot(dir, d5)), 18.0);

    float axes = max(max(a0, a1), max(max(a2, a3), max(a4, a5)));

    float breakup = fbm3(p * 9.5 + vec3(time * 2.2, -time * 1.8, time * 1.5));
    breakup = pow(saturate(breakup), 5.8);

    float filaments = noise3(p * 18.0 + vec3(-time * 3.4, time * 2.8, time * 2.1));
    filaments = pow(saturate(filaments), 7.0);

    float faceBias = pow(max(max(abs(dir.x), abs(dir.y)), abs(dir.z)), 1.8);

    return surge * axes * max(breakup, filaments * 0.85) * faceBias * SURGE_SPIKE_AMPLITUDE;
}

float rippleField(vec3 p, float time, float surge) {
    vec3 q = p;
    q = rotateY(q, time * 0.24);
    q = rotateX(q, -time * 0.14);

    float n1 = fbm3(q * 2.8 + vec3(time * RIPPLE_SPEED, -time * 0.8, time * 0.6));
    float n2 = fbm3(q * 5.2 + vec3(-time * 0.9, time * 1.0, -time * 0.7));
    float ripples = n1 * 0.72 + n2 * 0.28;
    ripples = ripples - 0.5;

    float rippleAmp = mix(CALM_RIPPLE_AMPLITUDE, SURGE_RIPPLE_AMPLITUDE, surge);
    ripples *= rippleAmp;

    float waveAmp = mix(CALM_WAVE_AMPLITUDE, SURGE_WAVE_AMPLITUDE, surge);
    ripples += sin(q.x * 7.6 + time * 3.0) * waveAmp;
    ripples += sin(q.z * 8.2 - time * 2.7) * (waveAmp * 0.75);

    return ripples;
}

float mapScene(vec3 p, float time) {
    float base = sdRoundBox(p, vec3(BLOB_SIZE), ROUNDING);
    float surge = surgePulse(GameTime);
    float ripple = rippleField(p, time, surge);
    float spikes = spikeField(p, time, surge);
    return base - ripple - spikes;
}

vec3 estimateNormal(vec3 p, float time) {
    vec2 e = vec2(0.003, 0.0);
    return normalize(vec3(
        mapScene(p + e.xyy, time) - mapScene(p - e.xyy, time),
        mapScene(p + e.yxy, time) - mapScene(p - e.yxy, time),
        mapScene(p + e.yyx, time) - mapScene(p - e.yyx, time)
    ));
}

bool raymarch(vec3 ro, vec3 rd, float time, out float tHit, out vec3 pHit) {
    float t = 0.0;
    for (int i = 0; i < MAX_STEPS; ++i) {
        vec3 p = ro + rd * t;
        float d = mapScene(p, time);
        if (d < HIT_EPSILON) {
            tHit = t;
            pHit = p;
            return true;
        }
        t += max(d * 0.72, 0.006);
        if (t > MAX_DISTANCE) break;
    }
    return false;
}

void main() {
    vec2 uv = particleCoord * 2.0 - 1.0;
    uv.y = -uv.y;

    float spinAngle = GameTime * TAU * SPIN_PER_DAY;
    float localTime = GameTime * TAU;
    float surge = surgePulse(GameTime);

    vec3 cameraPos = vec3(0.0, 0.0, 3.15);
    vec3 planePos = vec3(uv * 1.15, 0.0);
    vec3 rayDir = normalize(planePos - cameraPos);

    // Rotate the ray into object-local space so the ferrofluid cube appears to spin.
    vec3 ro = cameraPos;
    vec3 rd = rayDir;
    ro = rotateY(ro, -spinAngle);
    rd = rotateY(rd, -spinAngle);
    ro = rotateX(ro, -spinAngle * 0.52);
    rd = rotateX(rd, -spinAngle * 0.52);

    float tHit;
    vec3 pHit;
    if (!raymarch(ro, rd, localTime, tHit, pHit)) {
        discard;
    }

    vec3 normal = estimateNormal(pHit, localTime);
    vec3 viewDir = normalize(ro - pHit);
    vec3 lightDir = normalize(vec3(-0.45, 0.80, 0.55));
    vec3 halfDir = normalize(lightDir + viewDir);

    float diffuse = max(dot(normal, lightDir), 0.0);
    float specular = pow(max(dot(normal, halfDir), 0.0), mix(52.0, 34.0, surge));
    float fresnel = pow(1.0 - max(dot(normal, viewDir), 0.0), 2.7);

    float spikeHighlight = spikeField(pHit + normal * 0.018, localTime, 1.0);
    float innerGlow = pow(saturate(1.0 - length(pHit) / 1.05), 2.0);

    vec3 baseColor = mix(COLOR_DARK, COLOR_MID, 0.58 + pHit.y * 0.12);
    baseColor = mix(baseColor, COLOR_LIGHT, diffuse * (0.36 + surge * 0.10) + innerGlow * 0.18);

    vec3 color = baseColor * (0.34 + diffuse * 0.88);
    color += COLOR_LIGHT * fresnel * (0.42 + surge * 0.22);
    color += COLOR_SHINE * specular * (0.88 + surge * 0.20);
    color += COLOR_HOT * spikeHighlight * surge * 0.82;
    color += COLOR_LIGHT * innerGlow * 0.10;

    float alpha = 0.92 + fresnel * 0.06 + surge * spikeHighlight * 0.08;

    vec4 tint = vertexColor * ColorModulator;
    color *= tint.rgb;
    alpha *= tint.a;
    alpha = saturate(alpha);

    if (alpha < DiscardThreshold) {
        discard;
    }

    fragColor = linear_fog(
        vec4(color, alpha),
        vertexDistance,
        FogStart,
        FogEnd,
        FogColor
    );
}
