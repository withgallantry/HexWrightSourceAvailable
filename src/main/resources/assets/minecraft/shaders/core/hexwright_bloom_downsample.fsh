#version 150


uniform sampler2D DiffuseSampler;
uniform vec2 TexelOffset;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 colour = texture(DiffuseSampler, texCoord + vec2(-TexelOffset.x, -TexelOffset.y)).rgb;
    colour += texture(DiffuseSampler, texCoord + vec2(TexelOffset.x, -TexelOffset.y)).rgb;
    colour += texture(DiffuseSampler, texCoord + vec2(-TexelOffset.x, TexelOffset.y)).rgb;
    colour += texture(DiffuseSampler, texCoord + vec2(TexelOffset.x, TexelOffset.y)).rgb;
    fragColor = vec4(colour * 0.25, 1.0);
}
