#version 150

// Cloak field dome. The fragment shader works entirely in view space, so the
// position is handed over there as well as projected. UV0 is where on the dome
// the vertex sits - round it in x, up it in y, each 0..1 - for the ward.

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 viewPos;
out vec2 surfaceUV;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    viewPos = view.xyz;
    surfaceUV = UV0;
    gl_Position = ProjMat * view;
}
