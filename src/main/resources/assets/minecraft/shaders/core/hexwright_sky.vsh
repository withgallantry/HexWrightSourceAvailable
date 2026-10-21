#version 150


in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 SkyPose;

out vec3 skyDir;

void main() {
    skyDir = Position;
    gl_Position = ProjMat * ModelViewMat * SkyPose * vec4(Position, 1.0);
}
