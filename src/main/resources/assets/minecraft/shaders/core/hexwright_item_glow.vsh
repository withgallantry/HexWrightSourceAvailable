#version 150

// Emissive pass for models that have no hand-painted mask: the geometry is drawn a second time
// and the fragment shader decides, per texel, what glows. Deliberately no diffuse lighting - the
// vanilla emissive shader this is based on runs minecraft_mix_light, which is right for a mob's
// eyes but wrong here, where the point is a texel that reads at its authored colour whatever the
// light and whichever way the face points.
//
// Color carries the model's glow strength, not a tint. See EmissiveItemModels.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    texCoord0 = UV0;
}
