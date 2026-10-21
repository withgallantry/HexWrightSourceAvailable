#version 150


uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float GlowKnee;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    if (texel.a < 0.1) {
        discard;
    }

    float strength = vertexColor.r;
    float threshold = vertexColor.g;
    float saturationFloor = vertexColor.b;
    float knee = max(GlowKnee, 1.0e-4);

    float brightest = max(max(texel.r, texel.g), texel.b);
    float darkest = min(min(texel.r, texel.g), texel.b);
    float saturation = brightest <= 1.0e-5 ? 0.0 : (brightest - darkest) / brightest;

    float mask = smoothstep(threshold, threshold + knee, brightest);
    if (saturationFloor > 0.0) {
        mask *= smoothstep(max(saturationFloor - knee, 0.0), saturationFloor, saturation);
    }
    if (mask <= 0.0) {
        discard;
    }

    fragColor = vec4(texel.rgb * strength, texel.a * mask) * ColorModulator;
}
