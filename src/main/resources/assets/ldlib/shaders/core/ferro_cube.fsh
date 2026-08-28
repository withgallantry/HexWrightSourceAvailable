#version 150

#moj_import <fog.glsl>

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float GameTime;
uniform float DiscardThreshold;

// Resonance tuning. Defaults live in ferro_cube.json.
uniform float PixelGridResolution; // logical pixels across the shader quad
uniform float AnimationSpeed;      // 1.0 = calm default cadence
uniform float BrightnessLevels;    // recommended: 4..6
uniform float IdleIntensity;       // base emissive strength
uniform float PulseIntensity;      // multiplier for resonance alignments
uniform float PulseDuration;       // seconds
uniform float ActivityState;       // 0 idle, 1 network, 2 essence, 3 activation

in float vertexDistance;
in vec2 particleCoord;
in vec4 vertexColor;

out vec4 fragColor;

const float PI  = 3.14159265358979323846;
const float TAU = 6.28318530717958647692;

// The old shader deformed this volume into ferrofluid. Keep the same overall
// occupied space, but make it a stable piece of magical infrastructure.
const float FIELD_SIZE = 0.36;
const float FIELD_ROUNDING = 0.075;
const int   MAX_STEPS = 44;
const float MAX_DISTANCE = 8.0;
const float HIT_EPSILON = 0.0025;

const vec3 COLOR_DARK  = vec3(0.028, 0.010, 0.055);
const vec3 COLOR_BASE  = vec3(0.120, 0.030, 0.205);
const vec3 COLOR_GLOW  = vec3(0.430, 0.115, 0.670);
const vec3 COLOR_PULSE = vec3(0.720, 0.360, 0.940);

float saturate(float x) {
    return clamp(x, 0.0, 1.0);
}

float quantize01(float value, float levels) {
    float safeLevels = max(floor(levels + 0.5), 2.0);
    return floor(saturate(value) * (safeLevels - 1.0) + 0.5) / (safeLevels - 1.0);
}

float sdRoundBox(vec3 p, vec3 b, float r) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r;
}

float mapScene(vec3 p) {
    return sdRoundBox(p, vec3(FIELD_SIZE), FIELD_ROUNDING);
}

vec3 estimateNormal(vec3 p) {
    vec2 e = vec2(0.003, 0.0);
    return normalize(vec3(
        mapScene(p + e.xyy) - mapScene(p - e.xyy),
        mapScene(p + e.yxy) - mapScene(p - e.yxy),
        mapScene(p + e.yyx) - mapScene(p - e.yyx)
    ));
}

bool raymarch(vec3 ro, vec3 rd, out float tHit, out vec3 pHit) {
    float t = 0.0;
    for (int i = 0; i < MAX_STEPS; ++i) {
        vec3 p = ro + rd * t;
        float d = mapScene(p);
        if (d < HIT_EPSILON) {
            tHit = t;
            pHit = p;
            return true;
        }
        t += max(d * 0.78, 0.006);
        if (t > MAX_DISTANCE) break;
    }
    return false;
}

float runeMask(vec2 cell, float res) {
    vec2 c = cell - vec2((res - 1.0) * 0.5);
    float ax = abs(c.x);
    float ay = abs(c.y);
    float halfRes = res * 0.5;

    // Central spine: reads as a carved conduit rather than a screen scanline.
    float spine = (1.0 - step(0.85, ax)) * (1.0 - step(halfRes * 0.34, ay));

    // Broken horizontal resonator bars. The segmentation keeps the pattern
    // visibly authored/pixel-like instead of becoming broad luminous bands.
    float bandRow = 1.0 - step(0.75, mod(ay + 0.25, 5.0));
    float bandExtent = 1.0 - step(halfRes * 0.34, ax);
    float segment = 1.0 - step(2.0, mod(floor(ax) + floor(ay / 5.0) * 2.0, 4.0));
    float bands = bandRow * bandExtent * segment;

    // A square resonance frame, intentionally one logical pixel thick.
    float frameRadius = floor(halfRes * 0.31);
    float frame = 1.0 - step(0.80, abs(max(ax, ay) - frameRadius));
    float frameBreaks = step(1.0, mod(floor(ax + ay), 3.0));
    frame *= frameBreaks;

    // Sparse diagonal rune strokes around the core.
    float diagA = 1.0 - step(0.70, abs(ax - ay - 2.0));
    float diagB = 1.0 - step(0.70, abs(ax - ay * 0.5 - 3.0));
    float diagLimit = step(halfRes * 0.10, ay) * (1.0 - step(halfRes * 0.28, ay));
    float diagonals = max(diagA, diagB) * diagLimit;

    return saturate(max(max(spine, bands), max(frame, diagonals)));
}

float standingWave(vec2 snappedUv, float steppedPhase) {
    // UVs are already snapped before this function is called. These waves do
    // not translate across the surface: fixed nodes brighten and dim in place.
    vec2 p = snappedUv * 2.0 - 1.0;
    float a = cos(p.x * PI * 3.0) * cos(p.y * PI * 4.0);
    float b = cos((p.x + p.y) * PI * 2.0) * 0.45;
    float spatial = saturate(0.5 + 0.38 * a + 0.12 * b);

    // The threshold moves in discrete time steps. Cells therefore switch on
    // and off one logical pixel at a time rather than morphing continuously.
    float threshold = 0.56 + 0.10 * cos(steppedPhase);
    float region = step(threshold, spatial);

    // Keep a dim secondary level visible so the field always reads as active.
    float underField = step(0.48, spatial) * 0.45;
    return max(region, underField);
}

float periodicAlignment(float seconds, float duration) {
    // A calm infrastructure heartbeat: one short alignment roughly every 11s.
    float safeDuration = clamp(duration, 0.08, 2.0);
    float local = mod(seconds, 11.0);
    if (local >= safeDuration) {
        return 0.0;
    }

    float t = local / safeDuration;
    // Four hard temporal steps: rise, align, hold, release.
    float q = floor(t * 4.0) / 4.0;
    float envelope = 1.0 - abs(q * 2.0 - 1.0);
    return quantize01(envelope, 4.0);
}

float activityPulse(vec2 cell, float res, float seconds, float state) {
    if (state < 0.5) {
        return 0.0;
    }

    float safeDuration = clamp(PulseDuration, 0.08, 2.0);
    float phase = mod(seconds, safeDuration) / safeDuration;
    float steppedPhase = floor(phase * max(res, 8.0)) / max(res, 8.0);
    float row = floor(steppedPhase * res);
    float y = cell.y;

    if (state < 1.5) {
        // Network activity: one crisp, narrow pulse through the field.
        return 1.0 - step(1.25, abs(y - row));
    }

    if (state < 2.5) {
        // Essence transfer: broader, deeper pulse with a dim shoulder.
        float core = 1.0 - step(2.25, abs(y - row));
        float shoulder = (1.0 - step(4.25, abs(y - row))) * 0.45;
        return max(core, shoulder);
    }

    // Activation: a one-off-looking whole-field resonance while the state is
    // asserted. The caller should return ActivityState to 0 after the event.
    float q = floor(phase * 6.0) / 6.0;
    return quantize01(1.0 - abs(q * 2.0 - 1.0), 5.0);
}

void main() {
    float res = clamp(floor(PixelGridResolution + 0.5), 8.0, 96.0);

    // Critical style step: snap UVs before *any* animated pattern evaluation.
    vec2 cell = min(floor(particleCoord * res), vec2(res - 1.0));
    vec2 snappedUv = (cell + 0.5) / res;

    vec2 uv = snappedUv * 2.0 - 1.0;
    uv.y = -uv.y;

    // Minecraft GameTime is treated as a 24000-tick day. Build our animation
    // from quantized steps so temporal motion is deliberately non-smooth.
    float ticks = GameTime * 24000.0;
    float seconds = ticks / 20.0;
    float stepIndex = floor(seconds * max(AnimationSpeed, 0.01) * 4.0);
    float steppedPhase = mod(stepIndex, 32.0) / 32.0 * TAU;

    // Static field volume: no ferrofluid deformation and no continuous spin.
    vec3 cameraPos = vec3(0.0, 0.0, 3.15);
    vec3 planePos = vec3(uv * 1.15, 0.0);
    vec3 rayDir = normalize(planePos - cameraPos);

    float tHit;
    vec3 pHit;
    if (!raymarch(cameraPos, rayDir, tHit, pHit)) {
        discard;
    }

    vec3 normal = estimateNormal(pHit);
    vec3 lightDir = normalize(vec3(-0.45, 0.80, 0.55));
    float diffuse = max(dot(normal, lightDir), 0.0);
    diffuse = quantize01(diffuse, 4.0);

    float rune = runeMask(cell, res);
    float wave = standingWave(snappedUv, steppedPhase);

    // Fixed spatial groups oscillate with phase offsets. This reads as a
    // standing resonance: sections brighten/dim in place instead of flowing.
    float group = mod(floor(cell.x / 4.0) + floor(cell.y / 5.0) * 2.0, 4.0);
    float groupOsc = 0.5 + 0.5 * cos(steppedPhase + group * (PI * 0.5));
    groupOsc = quantize01(groupOsc, max(BrightnessLevels, 2.0));

    float pattern = max(wave * (0.48 + groupOsc * 0.42), rune * (0.58 + groupOsc * 0.32));

    // Periodically all groups lock together for a brief resonance alignment.
    float syncPulse = periodicAlignment(seconds, PulseDuration);
    float activity = activityPulse(cell, res, seconds, ActivityState);
    float eventPulse = max(syncPulse, activity);

    float idle = clamp(IdleIntensity, 0.0, 1.5);
    float pulseBoost = eventPulse * max(PulseIntensity, 0.0);

    // Dark body + restrained emissive pattern. No smooth bloom-like wash.
    float body = 0.12 + diffuse * 0.08;
    float emissive = body + pattern * (0.24 * idle) + rune * (0.10 * idle);
    emissive += pattern * pulseBoost * 0.34;

    // Activation gets a brighter whole-pattern alignment, but stays stepped.
    if (ActivityState >= 2.5) {
        emissive += eventPulse * 0.22 * max(PulseIntensity, 0.0);
    }

    float level = quantize01(emissive, clamp(BrightnessLevels, 2.0, 8.0));
    float pulseMix = quantize01(saturate(eventPulse * 0.75), clamp(BrightnessLevels, 2.0, 8.0));

    vec3 color = mix(COLOR_DARK, COLOR_BASE, min(level * 1.7, 1.0));
    color = mix(color, COLOR_GLOW, level * 0.72);
    color = mix(color, COLOR_PULSE, pulseMix * pattern * 0.42);

    // Quantized edge lift preserves volume without creating a smooth glossy rim.
    float facing = saturate(dot(normal, normalize(cameraPos - pHit)));
    float edge = quantize01(1.0 - facing, 3.0);
    color += COLOR_GLOW * edge * 0.055;

    vec4 tint = vertexColor * ColorModulator;
    color *= tint.rgb;

    float alpha = quantize01(0.78 + level * 0.16 + pulseMix * 0.05, 5.0) * tint.a;
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
