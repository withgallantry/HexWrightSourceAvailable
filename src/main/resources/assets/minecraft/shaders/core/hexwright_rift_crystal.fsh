#version 150

#moj_import <hexwright_hexahedron.glsl>


uniform vec3 CameraOffset;
uniform mat3 CrystalBasis;
uniform float HalfLength;
uniform float Radius;
uniform float Body;
uniform float Fill;
uniform float Glow;
uniform float Flare;
uniform float Dissolve;
uniform float Time;
uniform float Daylight;
uniform float Fade;
uniform float Seed;
uniform float Texel;

in vec3 local;

out vec4 fragColor;

const float SIDE = 1.0471976;
const vec3 DEEP = vec3(0.03, 0.025, 0.12);
const vec3 FACET = vec3(0.1, 0.14, 0.46);
const vec3 EDGE = vec3(0.4, 0.75, 1.0);
const vec3 ENERGY = vec3(0.298, 0.839, 1.0);
const float SWEEP = 0.6;

void main() {
    mat3 toCrystal = transpose(CrystalBasis);
    vec3 q = toCrystal * local;
    vec3 qs = (floor(q / Texel) + 0.5) * Texel;

    float energy = 0.0;
    if (Dissolve > 0.0) {
        float up = clamp(local.y / (2.0 * HalfLength) + 0.5, 0.0, 1.0);
        float own = clamp(Dissolve * (1.0 + SWEEP) - (1.0 - up) * SWEEP, 0.0, 1.0);
        vec3 at = floor(q / (Texel * 2.0)) * 0.45 + vec3(Seed, Dissolve * 1.4, 0.0);
        float grain = 0.65 * noise(at) + 0.35 * noise(at * 2.7 + 11.3);
        if (grain <= own) {
            discard;
        }
        energy = max(1.0 - smoothstep(own, own + 0.09, grain), own * 0.4);
    }

    vec3 n = normalize(cross(dFdx(local), dFdy(local)));
    vec3 view = normalize(local - CameraOffset);
    if (dot(n, view) > 0.0) {
        n = -n;
    }

    float around = atan(qs.z, qs.x) / SIDE;
    float region = qs.y > Body ? 1.0 : (qs.y < -Body ? -1.0 : 0.0);
    float across = abs(qs.y) < Body ? Radius : Radius * max(HalfLength - abs(qs.y), 0.0) / (HalfLength - Body);
    float toCorner = abs(fract(around + 0.5) - 0.5) * SIDE * across;
    float toRing = abs(abs(qs.y) - Body);
    float edge = max(exp(-toCorner / (0.9 * Texel)), exp(-toRing / (0.9 * Texel)));

    vec3 inside = q + refract(toCrystal * view, toCrystal * n, 0.66) * Radius * 0.85;
    float fromAxis = length(inside.xz) / Radius;
    float h = inside.y / HalfLength;
    float level = mix(-1.0, 1.0, Fill);
    float filled = 1.0 - smoothstep(level - 0.04, level + 0.04, h);
    float swirl = noise(vec3(inside.xz * (0.3 / Texel), inside.y * (0.125 / Texel) - Time * 1.6) + Seed);
    float lit = exp(-fromAxis * fromAxis * 6.0) * (0.25 + 0.75 * swirl) * mix(0.15, 1.0, filled) * (0.4 + 0.6 * Glow);
    if (Fill > 0.001 && Fill < 0.999) {
        lit += exp(-abs(h - level) * 40.0) * exp(-fromAxis * 2.0) * 0.8;
    }

    float shade = (0.35 + 0.65 * Daylight) * (0.45 + 0.55 * max(dot(n, normalize(vec3(0.3, 1.0, 0.2))), 0.0));
    float tone = 0.85 + 0.3 * hash(vec2(floor(around), region) + Seed);
    vec3 color = mix(DEEP, FACET, shade * tone);
    color += EDGE * pow(1.0 - abs(dot(n, view)), 3.0) * 0.35;
    color += fire(clamp(0.45 + 0.5 * lit, 0.0, 1.0)) * lit;
    color += EDGE * edge * (0.3 + 0.7 * Glow);
    float glint = step(0.993, hash(qs * 3.1 + floor(Time * 3.0 + hash(qs.xz) * 7.0)));
    color += vec3(0.8, 0.95, 1.0) * glint * 0.6;
    color += ENERGY * Flare * 0.5;
    color = mix(color, ENERGY * 1.8, energy);
    fragColor = vec4(min(color, vec3(1.0)) * Fade, Fade);
}
