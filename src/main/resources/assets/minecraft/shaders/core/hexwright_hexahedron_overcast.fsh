#version 150

#moj_import <hexwright_hexahedron.glsl>


uniform float Time;
uniform float Daylight;
uniform float Fade;
uniform float Outer;

in vec3 local;

out vec4 fragColor;

const float PIXEL = 6.0;
const vec2 WIND = vec2(3.0, 1.2);
const float PERIOD = 120.0;
const vec3 PALE = vec3(0.7, 0.72, 0.76);
const vec3 HEAVY = vec3(0.4, 0.42, 0.47);

float clouds(vec2 at, float shift, out float body) {
    vec2 drifted = at - WIND * PERIOD * shift;
    body = fbm(drifted / 280.0, 4);
    return body * 0.8 + fbm(drifted / 75.0 + 5.3, 3) * 0.2;
}

void main() {
    vec2 at = (floor(local.xz / PIXEL) + 0.5) * PIXEL;
    float edge = 1.0 - smoothstep(Outer * 0.5, Outer, length(at));
    float phase = fract(Time / PERIOD);
    float bodyA;
    float bodyB;
    float a = clouds(at, phase, bodyA);
    float b = clouds(at + 917.0, fract(phase + 0.5), bodyB);
    float toB = abs(phase * 2.0 - 1.0);
    float cover = smoothstep(0.4, 0.66, mix(a, b, toB));
    vec3 colour = mix(PALE, HEAVY, smoothstep(0.5, 0.8, mix(bodyA, bodyB, toB))) * (0.25 + 0.75 * Daylight);
    float alpha = cover * 0.85 * edge * Fade;
    fragColor = vec4(colour * alpha, alpha);
}
