#version 150

#moj_import <hexwright_hexahedron.glsl>


uniform float Time;
uniform float Life;
uniform float Orb;
uniform float Light;

in vec2 along;

out vec4 fragColor;

void main() {
    float across = Orb > 0.5 ? length(along) : abs(along.y);
    if (across > 1.0) {
        discard;
    }
    float flow = noise(vec2((Orb > 0.5 ? atan(along.y, along.x) * 3.0 : along.x * 0.8) - Time * 24.0, across * 4.0));
    float core = 1.0 - smoothstep(0.32, 0.44, across);
    float edge = exp(-pow((across - 0.46) / 0.11, 2.0));
    float glow = exp(-across * 3.5) * 0.35 * (1.0 - core);
    if (Light > 0.5) {
        float hot = 1.0 - smoothstep(0.0, 0.5, across);
        float body = exp(-across * across * 5.0) * (0.55 + 0.45 * flow);
        fragColor = vec4(fire(clamp(0.45 + 0.55 * hot * (0.7 + 0.3 * flow), 0.0, 1.0)) * body * Life,
            min(body * Life * 0.5, 1.0));
        return;
    }
    vec3 rim = mix(vec3(0.45, 0.18, 1.0), vec3(0.55, 0.9, 1.0), flow);
    float alpha = core * 0.95 * Life;
    vec3 color = vec3(0.012, 0.0, 0.03) * alpha + rim * (edge * 1.5 + glow) * Life;
    fragColor = vec4(color, alpha);
}
