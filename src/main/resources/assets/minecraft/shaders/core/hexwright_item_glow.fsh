#version 150

// Which texels of an unmasked model glow.
//
// Two tests, both of which a texel must pass.
//
// Brightness, because that is what separates a gem from the shadow behind it. On its own it is
// not enough: it says yes to anything pale, so a bone staff or a white cloth wrap lights up
// along its whole length, which reads as a mistake rather than as magic.
//
// Saturation, because that is what separates a gem from a highlight. Bone, cloth, steel and stone
// are all near-grey however bright they get; amethyst, flame and rune-light are not. Requiring
// some colour is what stops the glow spreading over the parts of a staff that were only ever
// meant to catch the light.
//
// Both thresholds arrive per-vertex rather than as uniforms, so one model can be tuned without
// forcing a draw of its own - see EmissiveItemModels for what each channel carries.

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
    // A floor of zero has to mean "no colour requirement at all", which smoothstep would not
    // give: its lower edge would sit at zero and a perfectly grey texel would still ramp in.
    if (saturationFloor > 0.0) {
        mask *= smoothstep(max(saturationFloor - knee, 0.0), saturationFloor, saturation);
    }
    if (mask <= 0.0) {
        discard;
    }

    fragColor = vec4(texel.rgb * strength, texel.a * mask) * ColorModulator;
}
