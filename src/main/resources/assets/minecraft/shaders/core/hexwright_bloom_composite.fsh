#version 150


uniform sampler2D DiffuseSampler;
uniform float Intensity;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    fragColor = vec4(texture(DiffuseSampler, texCoord).rgb * Intensity, 0.0);
}
