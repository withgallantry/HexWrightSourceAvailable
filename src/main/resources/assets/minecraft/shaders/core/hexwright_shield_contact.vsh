#version 150

// Quartz core shield, contact-glow pass. Six quads - the shield's own faces.
//
// Position arrives in camera-relative world space rather than view space: the
// caller leaves the camera rotation in ModelViewMat instead of baking it into
// the vertices, so the fragment shader can be handed a position that only
// moves when the *shield* moves. Ink that swam around with the camera would
// give the whole effect away as a screen-space trick.
//
// UV0 addresses this face's tile in the contact-distance atlas, and is derived
// on the CPU from each corner's world coordinate rather than assumed to be 0
// and 1 - see ShieldContactField.fraction().

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec3 shieldPos;
out vec2 fieldUV;

void main() {
    shieldPos = Position;
    vertexColor = Color;
    fieldUV = UV0;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
