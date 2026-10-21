#version 150


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
