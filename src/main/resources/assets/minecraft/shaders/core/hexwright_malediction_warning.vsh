#version 150


in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int Part;
uniform vec3 Origin;
uniform float CoronaWidth;

out vec3 local;
out vec3 toCamera;
out vec3 normal;
out vec2 uv;
out vec4 tint;

void main() {
    vec3 p = Position;
    if (Part == 1) {
        p += Normal * CoronaWidth;
    }
    vec4 view = ModelViewMat * vec4(p, 1.0);
    view.xyz *= 0.99975;
    gl_Position = ProjMat * view;
    local = p;
    toCamera = -(p + Origin);
    normal = Normal;
    uv = UV0;
    tint = Color;
}
