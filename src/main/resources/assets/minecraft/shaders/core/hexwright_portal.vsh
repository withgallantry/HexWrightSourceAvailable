#version 150

// Janus Threshold pane. Positions arrive already in view space (the pose is
// baked in by PortalPlaneRenderer and ModelViewMat is identity); UV0 carries
// rectangle-local coordinates in blocks, which the fragment shader uses for
// the feather and the opening reveal.

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 localPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    localPos = UV0;
}
