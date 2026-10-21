#version 150


uniform sampler2D SamplerSceneColor;
uniform vec2 ScreenSize;
uniform float DisplaceStrength;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 screenUV = gl_FragCoord.xy / ScreenSize;

    vec2 localUV = texCoord0 * 2.0 - 1.0;
    vec2 radialDir = normalize(localUV + vec2(0.00001));
    float radius = clamp(length(localUV), 0.0, 1.0);

    float pull = smoothstep(1.0, 0.6, radius) * smoothstep(0.0, 0.15, radius);

    vec2 displacedUV = screenUV + radialDir * DisplaceStrength * pull;

    vec3 scene = texture(SamplerSceneColor, displacedUV).rgb;

    fragColor = vec4(scene, 1.0);
}
