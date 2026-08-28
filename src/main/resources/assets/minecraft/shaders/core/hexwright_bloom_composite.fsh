#version 150

// Pass 3 of 3: add the blurred mask back over the scene.
//
// This is the only full-resolution pass, and on an integrated GPU it is the
// expensive one - not for its arithmetic (one fetch and one multiply) but for
// its memory traffic, since additive blending reads and writes every pixel of
// the main target. Nothing here is worth optimising; the cost is the bandwidth.
//
// Intensity is applied here and nowhere else. The blur passes ping-pong between
// two targets, so "the last blur" is not a fixed pass to hang a scale factor on;
// the composite is.
//
// Alpha is written as zero and the shader JSON blends alpha ZERO/ONE, so the
// main target's alpha channel comes through this pass exactly as it went in.
// Straight ONE/ONE on all four channels would accumulate into alpha as well,
// which matters under Fabulous graphics where that channel is not decorative.

uniform sampler2D DiffuseSampler;
uniform float Intensity;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    fragColor = vec4(texture(DiffuseSampler, texCoord).rgb * Intensity, 0.0);
}
