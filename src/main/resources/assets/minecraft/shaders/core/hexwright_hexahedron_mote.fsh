#version 150

#moj_import <hexwright_hexahedron.glsl>


in vec2 across;
in vec4 mote;

out vec4 fragColor;

void main() {
    float d = length(across);
    if (d > 1.0) {
        discard;
    }
    float core = 1.0 - smoothstep(0.12, 0.3, d);
    float halo = exp(-d * d * 5.0);
    fragColor = vec4(fire(clamp(0.45 + 0.35 * mote.r + 0.4 * core, 0.0, 1.0)) * (halo + core) * mote.a, 0.0);
}
