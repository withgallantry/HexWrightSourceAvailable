#version 150

// Scene-sampler displacement test: pulls scene pixels from DisplaceStrength further out
// along each fragment's radial direction, so the background appears sucked toward the
// particle's center. Fades to a plain passthrough at the quad edge so the seam between
// displaced and real scene stays invisible.

uniform sampler2D SamplerSceneColor;
uniform vec2 ScreenSize;
uniform float DisplaceStrength;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 screenUV = gl_FragCoord.xy / ScreenSize;

    // Quad-local coordinates: center (0,0), edges at +-1.
    vec2 localUV = texCoord0 * 2.0 - 1.0;
    vec2 radialDir = normalize(localUV + vec2(0.00001));
    float radius = clamp(length(localUV), 0.0, 1.0);

    // Strongest pull mid-ring, zero at the rim (seamless) and at the exact center
    // (radialDir is meaningless there).
    float pull = smoothstep(1.0, 0.6, radius) * smoothstep(0.0, 0.15, radius);

    vec2 displacedUV = screenUV + radialDir * DisplaceStrength * pull;

    vec3 scene = texture(SamplerSceneColor, displacedUV).rgb;

    fragColor = vec4(scene, 1.0);
}
