#version 150

// Pass 2 of 3, run twice: separable Gaussian, horizontally then vertically.
//
// Nine-tap Gaussian for the price of five fetches. The pairs at +-1.3846 and
// +-3.2308 texels sit BETWEEN texel centres on purpose: with GL_LINEAR
// filtering (set by SceneBloomFramebuffers) each of those fetches returns the
// hardware's interpolation of two neighbouring texels, so one fetch does the
// work of two taps. The offsets and weights are the standard pair for a nine-
// tap kernel collapsed this way - they are not arbitrary, and they only give a
// true Gaussian while the source is filtered linear.
//
// Separable, so the two passes together are 10 fetches per pixel rather than
// the 81 a 9x9 two-dimensional kernel would need. At quarter resolution the
// whole thing is well under a tenth of a millisecond.
//
// BlurDelta carries direction, texel size and radius pre-multiplied, so this
// shader has no idea which axis it is running on and needs no branch.

uniform sampler2D DiffuseSampler;
uniform vec2 BlurDelta;

in vec2 texCoord;

out vec4 fragColor;

const float WEIGHT_CENTRE = 0.2270270270;
const float WEIGHT_NEAR = 0.3162162162;
const float WEIGHT_FAR = 0.0702702703;
const float OFFSET_NEAR = 1.3846153846;
const float OFFSET_FAR = 3.2307692308;

void main() {
    vec3 colour = texture(DiffuseSampler, texCoord).rgb * WEIGHT_CENTRE;

    colour += texture(DiffuseSampler, texCoord + BlurDelta * OFFSET_NEAR).rgb * WEIGHT_NEAR;
    colour += texture(DiffuseSampler, texCoord - BlurDelta * OFFSET_NEAR).rgb * WEIGHT_NEAR;
    colour += texture(DiffuseSampler, texCoord + BlurDelta * OFFSET_FAR).rgb * WEIGHT_FAR;
    colour += texture(DiffuseSampler, texCoord - BlurDelta * OFFSET_FAR).rgb * WEIGHT_FAR;

    fragColor = vec4(colour, 1.0);
}
