#version 150

// Scene-sampler smoke test: the particle quad should be invisible, i.e. reproduce
// exactly the scene pixels behind itself. Set DepthDebug to 1 (uniform settings in
// the Photon editor) to view the captured scene depth as grayscale instead.

uniform sampler2D SamplerSceneColor;
uniform sampler2D SamplerSceneDepth;
uniform vec2 ScreenSize;
uniform float DepthDebug;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    // Snapshot is a 1:1 blit of the main framebuffer; gl_FragCoord and the snapshot
    // texture share a bottom-left origin, so no Y flip.
    vec2 screenUV = gl_FragCoord.xy / ScreenSize;

    vec4 scene = texture(SamplerSceneColor, screenUV);

    if (DepthDebug > 0.5) {
        float depth = texture(SamplerSceneDepth, screenUV).r;
        // Raw depth crowds towards 1.0; sharpen it so nearby geometry reads as dark.
        fragColor = vec4(vec3(pow(depth, 64.0)), 1.0);
        return;
    }

    fragColor = vec4(scene.rgb, 1.0);
}
