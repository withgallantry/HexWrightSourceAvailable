#version 150

// Janus Teeth rip. Positions arrive already in view space (the pose is baked
// in by VoidTearRenderer and ModelViewMat is identity); UV0 carries
// rectangle-local coordinates in blocks, measured from the rip's centre, which
// the fragment shader carves the jagged silhouette out of.

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 localPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    localPos = UV0;
}
