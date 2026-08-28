#version 150

// Halves the resolution of the glow buffer, one step at a time.
//
// The point of stepping down rather than jumping straight to the final size is that a single
// large jump point-samples the source: five taps spread across quarter-resolution spacing simply
// miss most of a full-resolution image, so a small bright shape shimmers as the camera moves and
// a thin one can vanish between taps. Each halving here averages every source texel exactly once,
// so nothing is skipped on the way down.
//
// Four fetches, not sixteen. TexelOffset is half the source texel size, so each bilinear fetch is
// already the average of a 2x2 block and the four together cover a 4x4 neighbourhood - the
// hardware's filtering does three quarters of the work.

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
