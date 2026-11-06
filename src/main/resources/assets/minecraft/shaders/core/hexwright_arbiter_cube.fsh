#version 150

#moj_import <hexwright_hexahedron.glsl>
#moj_import <fog.glsl>


uniform sampler2D Sigils;
uniform vec3 CameraOffset;
uniform mat3 CubeBasis;
uniform float HalfSize;
uniform float Pixels;
uniform float Time;
uniform vec3 TilesLow;
uniform vec3 TilesHigh;
uniform float LitFace;
uniform float Burn;
uniform float Heat;
uniform float Flare;
uniform vec4 Cut;
uniform float Daylight;
uniform float Fade;
uniform float FaceShow;
uniform float Mood;
uniform float Menace;
uniform float Count;
uniform float Tally;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec3 local;

out vec4 fragColor;

const float MAX_DIST = 0.3;
const float TILE = 192.0;
const vec2 ATLAS = vec2(4.0, 4.0);
const int FLAME_STEPS = 5;
const float FLAME_HEIGHT = 0.16;

const vec3 STONE = vec3(0.06, 0.06, 0.11);
const vec3 EDGE = vec3(0.22, 0.55, 1.0);
const float ASKING = 2.0;

vec4 sigil(vec2 s, float tile) {
    vec2 inTile = clamp(vec2(s.x, -s.y) * 0.5 + 0.5, 0.5 / TILE, 1.0 - 0.5 / TILE);
    vec2 cell = vec2(mod(tile, ATLAS.x), floor(tile / ATLAS.x));
    return texture(Sigils, (cell + inTile) / ATLAS);
}

float tileOf(float face) {
    vec3 tiles = face < 3.0 ? TilesLow : TilesHigh;
    float axis = mod(face, 3.0);
    return axis < 0.5 ? tiles.x : axis < 1.5 ? tiles.y : tiles.z;
}

const int DIGITS[10] = int[10](31599, 11415, 29671, 29647, 23497, 31183, 31215, 29257, 31727, 31695);

float digit(vec2 at, int value) {
    if (at.x < 0.0 || at.x >= 3.0 || at.y < 0.0 || at.y >= 5.0) {
        return 0.0;
    }
    int bit = 14 - (int(at.y) * 3 + int(at.x));
    return float((DIGITS[value] >> bit) & 1);
}

float countHeat(vec2 s) {
    int count = int(Count + 0.5);
    int tens = count / 10;
    float pixel = 6.0 / Pixels;
    float width = tens > 0 ? 7.0 : 3.0;
    vec2 at = vec2(s.x / pixel + width * 0.5, -s.y / pixel + 2.5);
    float lit = tens > 0 ? max(digit(at, tens), digit(at - vec2(4.0, 0.0), count - tens * 10)) : digit(at, count);
    return lit * 0.95;
}

float faceHeat(vec2 s) {
    float open = Menace > 0.5 ? 1.0 : step(0.07, fract(Time * 0.21 + 0.37));
    vec2 e = vec2(abs(s.x) - 0.34, s.y - 0.24);
    vec2 eye = vec2(e.x / mix(0.09, 0.15, Menace), (e.y - Menace * 0.55 * e.x) / mix(0.164, 0.05, Menace));
    float eyes = step(dot(eye, eye), 1.0) * open;
    float x = s.x / mix(0.56, 0.7, Menace);
    float teeth = Menace * 0.12 * (abs(fract(x * 3.0 + 0.5) - 0.5) * 4.0 - 1.0);
    float mouth = step(abs(x), 1.0) * step(abs(s.y + 0.34 - Mood * 0.3 * (x * x - 0.4) - teeth), 0.055 + Menace * 0.06);
    return max(eyes, mouth) * 0.95;
}

void main() {
    mat3 toCube = transpose(CubeBasis);
    vec3 q = toCube * local / HalfSize;
    bool cut = Cut.w < 1.5;
    if (cut && dot(q, Cut.xyz) > Cut.w + 0.002) {
        discard;
    }
    vec3 aq = abs(q);
    vec3 eye = normalize(local - CameraOffset);
    vec3 shadeColor;

    if (cut && max(aq.x, max(aq.y, aq.z)) < 0.985) {
        vec2 grain = floor(q.xy * Pixels * 0.5 + q.z * 7.0);
        float glow = 0.55 + 0.45 * noise(grain * 0.7 + Time * 1.5);
        vec3 inner = fire(clamp(0.45 * glow + 0.4 * Heat + Flare, 0.0, 1.0));
        fragColor = linear_fog(vec4(inner * Fade, Fade), length(local - CameraOffset), FogStart, FogEnd, FogColor);
        return;
    }

    vec3 n;
    vec3 ta;
    vec3 tb;
    vec2 ab;
    float face;
    if (aq.x >= aq.y && aq.x >= aq.z) {
        n = vec3(sign(q.x), 0.0, 0.0);
        ta = vec3(0.0, 1.0, 0.0);
        tb = vec3(0.0, 0.0, 1.0);
        ab = q.yz;
        face = q.x < 0.0 ? 0.0 : 3.0;
    } else if (aq.y >= aq.z) {
        n = vec3(0.0, sign(q.y), 0.0);
        ta = vec3(1.0, 0.0, 0.0);
        tb = vec3(0.0, 0.0, 1.0);
        ab = q.xz;
        face = q.y < 0.0 ? 1.0 : 4.0;
    } else {
        n = vec3(0.0, 0.0, sign(q.z));
        ta = vec3(1.0, 0.0, 0.0);
        tb = vec3(0.0, 1.0, 0.0);
        ab = q.xy;
        face = q.z < 0.0 ? 2.0 : 5.0;
    }

    vec2 cell = min(floor((clamp(ab, -1.0, 1.0) * 0.5 + 0.5) * Pixels), Pixels - 1.0);
    vec2 snapped = (cell + 0.5) / Pixels * 2.0 - 1.0;
    vec3 qs = n + ta * snapped.x + tb * snapped.y;

    vec3 upLocal = toCube * vec3(0.0, 1.0, 0.0);
    vec3 up = upLocal - dot(upLocal, n) * n;
    up = dot(up, up) < 1.0e-4 ? ta : normalize(up);
    vec3 right = cross(up, n);
    vec2 s = vec2(dot(qs, right), dot(qs, up));

    vec3 worldNormal = CubeBasis * n;
    float light = (0.4 + 0.6 * Daylight) * (0.7 + 0.3 * max(dot(worldNormal, normalize(vec3(0.3, 1.0, 0.2))), 0.0));
    float course = Pixels / 4.0;
    vec2 inCourse = mod(cell, course);
    float joint = (inCourse.x < 1.0 || inCourse.y < 1.0) ? 0.6 : 1.0;
    vec3 body = STONE * (0.8 + 0.4 * hash(cell + face * 97.0)) * joint * light;
    body += EDGE * pow(1.0 - abs(dot(worldNormal, eye)), 4.0) * 0.15;

    float heat = 0.0;
    float tile = tileOf(face);
    if (tile >= 0.0) {
        bool lit = abs(face - LitFace) < 0.5;
        vec4 ink = sigil(s, tile);
        float d = ink.r * MAX_DIST;
        float core = 1.0 - smoothstep(0.035, 0.055, d);
        float halo = exp(-d / 0.07);
        float rising = fbm(vec2(s.x * 12.0, s.y * 6.0 - Time * 2.4) + face * 13.0, 3);
        float flame = 0.0;
        for (int step = 1; step <= FLAME_STEPS; step++) {
            float climb = float(step) / float(FLAME_STEPS);
            float below = sigil(s - vec2(0.0, climb * FLAME_HEIGHT), tile).r * MAX_DIST;
            float carried = smoothstep(0.3 + 0.3 * climb, 0.42 + 0.3 * climb, rising);
            flame = max(flame, exp(-below / 0.035) * carried * (1.0 - 0.6 * climb));
        }
        float start = 1.0 - smoothstep(0.04, 0.07, ink.b * MAX_DIST);
        float strength = lit ? 1.0 : 0.3;
        heat = max(max(core * 0.8, flame * (lit ? 1.0 : 0.4)), halo * 0.35) * strength;
        if (lit) {
            float eaten = step(ink.g, Burn);
            float front = exp(-abs(ink.g - Burn) * 28.0) * core;
            heat = max(heat, max(core * mix(0.85, 1.0, eaten), front * 1.4));
            heat = max(heat, start * (0.8 + 0.2 * sin(Time * 9.0)));
        }
        heat *= Heat;
    }
    float faceLit = 0.0;
    if (abs(face - ASKING) < 0.5 && FaceShow > 0.0) {
        float drawn = mix(faceHeat(s), countHeat(s), Tally);
        heat = mix(heat, drawn, FaceShow);
        faceLit = drawn * FaceShow;
    }
    float flicker = 0.85 + 0.15 * noise(vec2(Time * 7.0, face * 5.0));
    heat = floor((heat * flicker + Flare) * 6.0 + 0.5) / 6.0;

    float fromEdge = min(min(cell.x, cell.y), min(Pixels - 1.0 - cell.x, Pixels - 1.0 - cell.y));
    float edge = exp(-fromEdge / 0.9) * (0.35 + 0.65 * max(Heat, Flare));
    vec3 snappedQ = qs;
    float rim = 0.0;
    if (cut) {
        float toCut = (Cut.w - dot(snappedQ, Cut.xyz)) * Pixels * 0.5;
        rim = exp(-max(toCut, 0.0) / 1.2) * (0.7 + 0.5 * noise(vec2(dot(snappedQ, vec3(13.0, 7.0, 3.0)) * 4.0, Time * 3.0)));
    }

    vec3 color = mix(body, fire(min(heat, 1.0)), clamp(heat * 1.6, 0.0, 1.0));
    color += EDGE * edge + vec3(0.55, 0.85, 1.0) * rim;
    color = mix(color, vec3(1.0, 0.2, 0.3), faceLit * Menace * 0.85);
    fragColor = linear_fog(vec4(min(color, vec3(1.0)) * Fade, Fade), length(local - CameraOffset), FogStart, FogEnd, FogColor);
}
