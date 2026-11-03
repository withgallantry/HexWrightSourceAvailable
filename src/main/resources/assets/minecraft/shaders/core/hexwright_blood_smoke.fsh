#version 150


uniform float Time;

in vec4 vertexColor;
in vec2 ribbon;

out vec4 fragColor;

const float PIXELS = 16.0;
const vec3 DEEP = vec3(0.42, 0.0, 0.05);
const vec3 HOT = vec3(1.0, 0.2, 0.28);

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float sum = 0.0;
    float amplitude = 0.5;
    for (int octave = 0; octave < 3; octave++) {
        sum += amplitude * noise(p);
        p = p * 2.07 + vec2(1.7, 9.2);
        amplitude *= 0.5;
    }
    return sum / 0.875;
}

void main() {
    float halfWidth = max(vertexColor.r, 1.0 / PIXELS);
    vec2 cell = (floor(vec2(ribbon.x * halfWidth, ribbon.y) * PIXELS) + 0.5) / PIXELS;
    float across = clamp(cell.x / halfWidth, -1.0, 1.0);

    vec2 p = vec2(cell.x * 2.0, cell.y * 1.2 + Time * 0.3);
    vec2 warp = vec2(fbm(p + vec2(0.0, Time * 0.25)), fbm(p + vec2(5.2, 1.3) - vec2(0.0, Time * 0.2)));
    float body = fbm(p * 1.4 + (warp - 0.5) * 2.5) * (1.0 - across * across * across * across);

    float threshold = 0.55 - 0.35 * vertexColor.a;
    float smoke = smoothstep(threshold, threshold + 0.3, body) * min(1.0, vertexColor.a * 4.0);
    smoke = floor(smoke * 5.0 + 0.5) / 5.0;
    if (smoke <= 0.0) {
        discard;
    }
    vec3 colour = mix(DEEP, HOT, smoothstep(0.5, 0.8, body));
    fragColor = vec4(colour * smoke, smoke * 0.45);
}
