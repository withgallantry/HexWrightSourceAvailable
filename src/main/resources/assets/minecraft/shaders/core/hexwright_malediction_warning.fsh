#version 150


uniform int Part;
uniform float Side;
uniform float Time;
uniform float Seed;
uniform vec3 Colour;
uniform vec2 Middle;
uniform float Reach;
uniform float Charge;
uniform float Charging;
uniform float Flash;
uniform float Fade;

in vec3 local;
in vec3 toCamera;
in vec3 normal;
in vec2 uv;
in vec4 tint;

out vec4 fragColor;

const int SHELL = 0;
const int CORONA = 1;
const int TENDRIL = 2;

float hash(ivec3 p) {
    uvec3 q = uvec3(p + ivec3(1 << 20));
    uint h = (q.x * 1597334673u) ^ (q.y * 3812015801u) ^ (q.z * 2798796415u);
    h = (h ^ (h >> 16u)) * 2246822519u;
    h ^= h >> 13u;
    return float(h) * (1.0 / 4294967295.0);
}

float noise(vec3 x) {
    ivec3 i = ivec3(floor(x));
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + ivec3(1, 0, 0)), f.x),
                   mix(hash(i + ivec3(0, 1, 0)), hash(i + ivec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + ivec3(0, 0, 1)), hash(i + ivec3(1, 0, 1)), f.x),
                   mix(hash(i + ivec3(0, 1, 1)), hash(i + ivec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    return (noise(p) * 2.0 + noise(p * 2.07 + 13.1)) / 3.0;
}

float facing(vec3 n) {
    return min(abs(dot(n, normalize(toCamera))), 1.0);
}

vec3 hot() {
    return mix(Colour, vec3(1.0), 0.55);
}

float filled(vec3 p, out float front) {
    if (Charging < 0.5) {
        front = 0.0;
        return 1.0;
    }
    float ragged = (noise(p * 1.3 + vec3(0.0, Time * 1.1, Seed)) - 0.5) * 0.9;
    float ahead = length(p.xz - Middle) + ragged - Charge * Reach;
    front = exp(-abs(ahead) * 7.0);
    return smoothstep(0.25, -0.25, ahead);
}

vec4 shell() {
    if (dot(normal, toCamera) * Side < 0.0) {
        discard;
    }
    float rim = pow(1.0 - facing(normalize(normal)), 3.0);
    float front;
    float energy = filled(local, front);

    vec3 q = local * 0.9 + Seed;
    vec3 warp = vec3(noise(q + vec3(0.0, Time * 0.35, 0.0)),
                     noise(q + vec3(5.2, -Time * 0.3, 1.3)),
                     noise(q + vec3(-3.1, 7.7, Time * 0.25)));
    float field = noise(q * 1.7 + warp * 2.4 + vec3(0.0, -Time * 0.6, 0.0));
    float vein = pow(max(1.0 - abs(field * 2.0 - 1.0), 0.0), 18.0);
    float smoke = fbm(q * 0.8 + warp * 1.5 + vec3(0.0, Time * 0.15, 0.0));

    float alpha = (0.08 + 0.36 * energy) * (0.35 + 1.1 * smoke) + rim * 0.3;
    vec3 light = Colour * (vein * (0.1 + 0.9 * energy) + rim * (0.25 + 0.35 * energy))
        + hot() * (front * 0.8 + vein * energy * 0.3);
    light += hot() * Flash * (0.6 + 1.6 * vein);
    alpha = clamp(alpha + Flash * 0.2, 0.0, 1.0);
    return vec4(Colour * 0.06 * alpha + light, alpha);
}

vec4 corona() {
    vec3 n = normalize(normal);
    float edgeOn = facing(n);
    float front;
    float energy = filled(local, front);
    float wisps = fbm(local * vec3(1.6, 0.8, 1.6) - vec3(0.0, Time * 1.2, 0.0) + Seed);
    float tongue = edgeOn + (wisps - 0.5) * 0.4;
    float band = smoothstep(0.02, 0.14, tongue) * (1.0 - smoothstep(0.2, 0.5, edgeOn));
    float alpha = band * smoothstep(0.25, 0.65, wisps) * (0.5 + 0.4 * energy + 0.3 * Flash);
    return vec4(Colour * 0.1 * alpha, alpha);
}

vec4 tendril() {
    float across = min(abs(uv.y), 1.0);
    float along = uv.x;
    float ends = smoothstep(0.0, 0.1, along) * (1.0 - smoothstep(0.5, 1.0, along));
    float core = pow(1.0 - across, 2.0);
    float pulse = 0.5 + 0.5 * sin(along * 13.0 - Time * 9.0 + tint.r * 40.0);
    float life = tint.a * ends;
    float alpha = (1.0 - smoothstep(0.45, 1.0, across)) * 0.65 * life;
    vec3 light = (Colour * core * (0.7 + 0.6 * pulse) + hot() * pow(1.0 - across, 10.0) * pulse) * life;
    light *= 1.0 + Flash * 1.5;
    return vec4(Colour * 0.08 * alpha + light, alpha);
}

void main() {
    vec4 colour = Part == SHELL ? shell() : Part == CORONA ? corona() : tendril();
    fragColor = colour * Fade;
}
