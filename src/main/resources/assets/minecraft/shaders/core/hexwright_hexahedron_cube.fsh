#version 150

#moj_import <hexwright_hexahedron.glsl>


uniform sampler2D Sigils;
uniform vec3 CameraOffset;
uniform vec3 CubeCenter;
uniform mat3 CubeBasis;
uniform float HalfSize;
uniform float Time;
uniform float Pulse;
uniform float Emerge;
uniform float Daylight;
uniform float Fade;
uniform float Dissolve;
uniform vec4 FogColor;

in vec3 local;

out vec4 fragColor;

const float PIXELS = 80.0;
const float SIGIL_EXTENT = 1.0;
const float MAX_DIST = 0.3;
const float TILE = 192.0;
const vec2 ATLAS = vec2(3.0, 2.0);
const float COURSE = 16.0;
const int FLAME_STEPS = 8;
const float FLAME_HEIGHT = 0.2;

const vec3 STONE = vec3(0.05, 0.055, 0.095);
const vec3 EDGE = vec3(0.22, 0.55, 1.0);
const vec3 RIM = vec3(0.55, 0.85, 1.0);
const float SWEEP = 0.7;

vec4 sigil(vec2 s, float face) {
    vec2 inTile = clamp(vec2(s.x, -s.y) / (2.0 * SIGIL_EXTENT) + 0.5, 0.5 / TILE, 1.0 - 0.5 / TILE);
    vec2 tile = vec2(mod(face, 3.0), floor(face / 3.0));
    return texture(Sigils, (tile + inTile) / ATLAS);
}

void main() {
    if (local.y > 0.05) {
        discard;
    }
    mat3 toCube = transpose(CubeBasis);
    vec3 q = toCube * (local - CubeCenter) / HalfSize;
    vec3 aq = abs(q);

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

    vec2 cell = min(floor((clamp(ab, -1.0, 1.0) * 0.5 + 0.5) * PIXELS), PIXELS - 1.0);
    vec2 snapped = (cell + 0.5) / PIXELS * 2.0 - 1.0;
    vec3 qs = n + ta * snapped.x + tb * snapped.y;

    float energy = 0.0;
    if (Dissolve > 0.0) {
        float low = clamp(0.5 - dot(qs, toCube * vec3(0.0, 1.0, 0.0)) * 0.2886751, 0.0, 1.0);
        float own = clamp(Dissolve * (1.0 + SWEEP) - (1.0 - low) * SWEEP, 0.0, 1.0);
        vec3 at = floor(qs * PIXELS * 0.125) + vec3(0.0, Dissolve * 3.0, 0.0);
        float grain = 0.6 * noise(at * 0.5) + 0.4 * noise(at * 1.3 + 7.1);
        if (grain <= own) {
            discard;
        }
        energy = max(1.0 - smoothstep(own, own + 0.1, grain), own * 0.5);
    }

    vec3 upLocal = toCube * vec3(0.0, 1.0, 0.0);
    vec3 up = normalize(upLocal - dot(upLocal, n) * n);
    vec3 right = cross(up, n);
    vec2 s = vec2(dot(qs, right), dot(qs, up));

    vec3 worldNormal = CubeBasis * n;
    float light = (0.35 + 0.65 * Daylight) * (0.78 + 0.22 * worldNormal.y);
    vec2 course = mod(cell, COURSE);
    float joint = (course.x < 1.0 || course.y < 1.0) ? 0.55 : 1.0;
    vec3 body = STONE * (0.8 + 0.4 * hash(cell + face * 97.0)) * joint * light;
    vec3 eye = normalize(local - CameraOffset);
    body += EDGE * pow(1.0 - abs(dot(worldNormal, eye)), 4.0) * 0.12 * light;
    float haze = (1.0 - exp(-length(local - CameraOffset) / 3000.0)) * 0.55;
    body = mix(body, FogColor.rgb, haze);

    vec4 ink = sigil(s, face);
    float d = ink.r * MAX_DIST;
    float core = 1.0 - smoothstep(0.03, 0.046, d);
    float halo = exp(-d / 0.06);
    float rising = fbm(vec2(s.x * 16.0, s.y * 7.0 - Time * 2.2) + face * 13.0, 3);
    float flame = 0.0;
    for (int step = 1; step <= FLAME_STEPS; step++) {
        float climb = float(step) / float(FLAME_STEPS);
        float below = sigil(s - vec2(0.0, climb * FLAME_HEIGHT), face).r * MAX_DIST;
        float carried = smoothstep(0.28 + 0.3 * climb, 0.4 + 0.3 * climb, rising);
        flame = max(flame, exp(-below / 0.03) * carried * (1.0 - 0.6 * climb));
    }
    float behind = fract(Time * 0.11 + face * 0.37) - ink.g;
    float run = behind >= 0.0 ? exp(-behind * 7.0) : 0.0;
    float start = 1.0 - smoothstep(0.035, 0.06, ink.b * MAX_DIST);
    float flicker = 0.85 + 0.15 * noise(vec2(Time * 7.0, face * 5.0));
    float heat = max(max(core * (0.75 + 0.4 * run), flame), halo * 0.4) * flicker;
    heat = max(heat, start) * (1.0 + 0.5 * Pulse);
    heat = floor(heat * 6.0 + 0.5) / 6.0;

    float fromEdge = min(min(cell.x, cell.y), min(PIXELS - 1.0 - cell.x, PIXELS - 1.0 - cell.y));
    float edge = exp(-fromEdge / 1.3) * (0.7 + 0.3 * Pulse);
    float frame = fromEdge == 6.0 ? 0.3 + 0.3 * Pulse : 0.0;
    vec3 snappedLocal = CubeCenter + CubeBasis * (qs * HalfSize);
    float depth = max(-snappedLocal.y, 0.0);
    float crackle = noise(vec2(atan(snappedLocal.z, snappedLocal.x) * 40.0, Time * 3.0));
    float rim = (exp(-depth / 2.5) * (0.7 + 0.6 * crackle) + exp(-depth / 14.0) * 0.25)
        * (1.0 + 1.5 * (1.0 - Emerge));

    vec3 color = mix(body, fire(min(heat, 1.0)), clamp(heat * 1.6, 0.0, 1.0));
    color += EDGE * (edge + frame) + RIM * rim;
    color = mix(color, vec3(0.6, 0.9, 1.0) * 1.4, energy);
    fragColor = vec4(min(color, vec3(1.0)) * Fade, Fade);
}
