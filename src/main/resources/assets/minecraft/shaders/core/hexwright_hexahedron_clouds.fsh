#version 150

#moj_import <hexwright_hexahedron.glsl>


uniform sampler2D Storm;
uniform vec3 CameraOffset;
uniform float Layer;
uniform float Outer;
uniform float Time;
uniform float Pulse;
uniform float Emerge;
uniform float Daylight;
uniform float Fade;
uniform vec4 FogColor;

in vec3 local;

out vec4 fragColor;

const float LIGHTNING_CELL = 72.0;
const float HOLE_REACH = 128.0;

void main() {
    float formed = smoothstep(0.0, 0.5, Emerge);
    float reach = Outer * (0.35 + 0.65 * formed);
    if (length(local.xz) > reach) {
        discard;
    }
    float texels = float(textureSize(Storm, 0).x);
    vec2 cell = clamp(floor((local.xz / Outer * 0.5 + 0.5) * texels), 0.0, texels - 1.0);
    vec2 p = ((cell + 0.5) / texels * 2.0 - 1.0) * Outer;
    float r = length(p);
    vec4 storm = texelFetch(Storm, ivec2(cell), 0);
    float density = storm.r;

    float outside = storm.g * HOLE_REACH;
    float hug = exp(-max(outside + (density - 0.5) * 80.0, 0.0) / 40.0);
    float cover = smoothstep(reach, reach * 0.6, r);
    float body = density - 0.62 * (1.0 - cover) + 0.15 * hug;
    float threshold = 0.24 + 0.3 * Layer + 0.5 * (1.0 - formed);
    float alpha = floor(smoothstep(threshold, threshold + 0.05, body) * 4.0 + 0.5) / 4.0;
    if (alpha <= 0.0) {
        discard;
    }

    float thick = floor(smoothstep(threshold, threshold + 0.35, body) * 5.0 + 0.5) / 5.0;
    vec3 color = mix(vec3(0.34, 0.35, 0.42), vec3(0.08, 0.085, 0.12), thick);
    color = mix(color, vec3(0.55, 0.56, 0.62) * (1.0 - 0.4 * thick), Layer * Layer);
    color *= 0.3 + 0.7 * Daylight;

    float flicker = storm.b;
    float glow = hug * (1.0 - 0.7 * Layer) * (1.0 - 0.5 * thick) * (0.6 + 0.4 * Pulse);
    float seam = exp(-outside / 6.0) * (1.0 - Layer) * (0.7 + 0.5 * flicker);
    color += vec3(0.2, 0.5, 1.0) * floor(glow * 8.0 + 0.5) / 8.0 * 0.9;
    color += vec3(0.5, 0.85, 1.0) * floor(seam * 4.0 + 0.5) / 4.0;
    vec2 strikeCell = floor(p / LIGHTNING_CELL);
    float struck = step(0.995, hash(strikeCell + floor(Time * 6.0) * 7.31));
    float flash = struck * smoothstep(0.5, 0.1, length(fract(p / LIGHTNING_CELL) - 0.5)) * thick;
    color += vec3(0.55, 0.75, 1.0) * floor(flash * 3.0 + 0.5) / 3.0 * 0.6 * (1.0 - 0.5 * Layer);

    float haze = (1.0 - exp(-length(local - CameraOffset) / 3000.0)) * 0.6;
    color = mix(color, FogColor.rgb, haze);
    fragColor = vec4(min(color, vec3(1.0)) * alpha * Fade, alpha * Fade);
}
