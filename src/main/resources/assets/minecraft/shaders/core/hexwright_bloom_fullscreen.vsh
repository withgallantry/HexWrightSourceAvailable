#version 150

// Shared by all three bloom passes. Positions arrive already in clip space, so
// this never touches ModelViewMat/ProjMat - the shader JSONs do not declare
// them, and the pass therefore does not care what camera matrices happen to be
// live on RenderSystem when it runs.

in vec3 Position;
in vec2 UV0;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = UV0;
}
