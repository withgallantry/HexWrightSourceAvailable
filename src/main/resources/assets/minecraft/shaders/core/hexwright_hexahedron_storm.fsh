#version 150

#moj_import <hexwright_hexahedron.glsl>


uniform float Outer;
uniform vec4 HoleA;
uniform vec4 HoleB;
uniform vec4 HoleC;
uniform float HoleRadius;
uniform float Time;
uniform float Fast;

in vec2 across;

out vec4 fragColor;

const float PERIOD = 48.0;
const float SWIRL = 0.04;
const float FEATURE = 90.0;
const float HOLE_REACH = 128.0;

float outsideHole(vec2 p) {
    vec2 corners[6] = vec2[6](HoleA.xy, HoleA.zw, HoleB.xy, HoleB.zw, HoleC.xy, HoleC.zw);
    vec2 centre = (corners[0] + corners[1] + corners[2] + corners[3] + corners[4] + corners[5]) / 6.0;
    float distance = -1.0e9;
    bool any = false;
    for (int i = 0; i < 6; i++) {
        vec2 a = corners[i];
        vec2 edge = corners[(i + 1) % 6] - a;
        float length = length(edge);
        if (length < 0.01) {
            continue;
        }
        vec2 normal = vec2(edge.y, -edge.x) / length;
        if (dot(normal, a - centre) < 0.0) {
            normal = -normal;
        }
        distance = max(distance, dot(p - a, normal));
        any = true;
    }
    return any ? distance : 1.0e9;
}

vec2 turn(vec2 p, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

float storm(vec2 p, float r, float clock, vec2 seed, int octaves) {
    float spin = SWIRL * clamp(HoleRadius / max(r, 1.0), 0.12, 1.6) * clock;
    vec2 q = turn(p, -spin) / FEATURE + seed;
    vec2 warp = vec2(fbm(q * 0.6 + Time * 0.015, 2), fbm(q * 0.6 + 5.2 - Time * 0.015, 2));
    return fbm(q + (warp - 0.5) * 2.2, octaves);
}

void main() {
    vec2 p = across * Outer;
    float r = length(p);
    int octaves = Fast > 0.5 ? 4 : 5;
    float clockA = fract(Time / PERIOD);
    float clockB = fract(clockA + 0.5);
    float weightA = 1.0 - abs(clockA * 2.0 - 1.0);
    float weightB = 1.0 - weightA;
    float density = storm(p, r, clockA * PERIOD, vec2(0.0), octaves) * weightA
        + storm(p, r, clockB * PERIOD, vec2(17.3, 41.9), octaves) * weightB;
    density = 0.5 + (density - 0.5) / sqrt(weightA * weightA + weightB * weightB);
    float outside = clamp(outsideHole(p) / HOLE_REACH, 0.0, 1.0);
    float flicker = noise(vec2(atan(p.y, p.x) * 24.0, Time * 4.0));
    fragColor = vec4(clamp(density, 0.0, 1.0), outside, flicker, 1.0);
}
