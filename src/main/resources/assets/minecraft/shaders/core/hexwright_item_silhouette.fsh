#version 150


uniform sampler2D Sampler0;

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

const float SHADE_FLOOR = 0.6;

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    float alpha = texel.a * vertexColor.a;
    if (alpha < 0.1) {
        discard;
    }

    float luma = dot(texel.rgb, vec3(0.2126, 0.7152, 0.0722));
    fragColor = vec4(vertexColor.rgb * mix(SHADE_FLOOR, 1.0, luma), alpha) * ColorModulator;
}
