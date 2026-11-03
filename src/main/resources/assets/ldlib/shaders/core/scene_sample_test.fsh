#version 150


uniform sampler2D SamplerSceneColor;
uniform sampler2D SamplerSceneDepth;
uniform vec2 ScreenSize;
uniform float DepthDebug;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 screenUV = gl_FragCoord.xy / ScreenSize;

    vec4 scene = texture(SamplerSceneColor, screenUV);

    if (DepthDebug > 0.5) {
        float depth = texture(SamplerSceneDepth, screenUV).r;
        fragColor = vec4(vec3(pow(depth, 64.0)), 1.0);
        return;
    }

    fragColor = vec4(scene.rgb, 1.0);
}
