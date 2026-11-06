#version 150


in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float Scale;
uniform vec3 CameraOffset;

out vec3 local;

void main() {
    local = Position * Scale + CameraOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
