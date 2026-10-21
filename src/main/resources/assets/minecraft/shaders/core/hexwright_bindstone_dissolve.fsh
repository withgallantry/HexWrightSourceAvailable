#version 150


uniform sampler2D Sampler0;

uniform vec4 ColorModulator;

uniform vec4 Dissolve;

uniform vec4 Shape;

uniform vec4 Energy;

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 localPos;

out vec4 fragColor;

float hash(vec3 cell) {
    vec3 p = fract(cell * 0.3183099 + vec3(0.71, 0.113, 0.419));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float valueNoise(vec3 at) {
    vec3 cell = floor(at);
    vec3 f = fract(at);
    f = f * f * (3.0 - 2.0 * f);
    float x00 = mix(hash(cell + vec3(0.0, 0.0, 0.0)), hash(cell + vec3(1.0, 0.0, 0.0)), f.x);
    float x10 = mix(hash(cell + vec3(0.0, 1.0, 0.0)), hash(cell + vec3(1.0, 1.0, 0.0)), f.x);
    float x01 = mix(hash(cell + vec3(0.0, 0.0, 1.0)), hash(cell + vec3(1.0, 0.0, 1.0)), f.x);
    float x11 = mix(hash(cell + vec3(0.0, 1.0, 1.0)), hash(cell + vec3(1.0, 1.0, 1.0)), f.x);
    return mix(mix(x00, x10, f.y), mix(x01, x11, f.y), f.z);
}

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    if (texel.a < 0.1) {
        discard;
    }

    float height = max(Shape.x, 1.0e-3);
    float up = clamp(localPos.y / height, 0.0, 1.0);
    float sweep = max(Dissolve.y, 0.0);

    float own = clamp(Dissolve.x * (1.0 + sweep) - (1.0 - up) * sweep, 0.0, 1.0);

    vec3 at = localPos * Shape.y + vec3(Dissolve.w, Dissolve.x * 0.7, Dissolve.w * 0.5);
    float noise = 0.65 * valueNoise(at) + 0.35 * valueNoise(at * Shape.z + 11.3);

    if (noise <= own) {
        discard;
    }

    float front = 1.0 - smoothstep(own, own + max(Dissolve.z, 1.0e-3), noise);
    float heat = own * Shape.w;

    vec3 stone = texel.rgb * vertexColor.rgb;
    vec3 glow = Energy.rgb * max(Energy.a, 0.0);
    vec3 colour = mix(stone, glow, clamp(front + heat, 0.0, 1.0));

    fragColor = vec4(colour, texel.a * vertexColor.a) * ColorModulator;
}
