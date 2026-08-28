#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec2 particleCoord;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexDistance = fog_distance(ModelViewMat, Position, FogShape);
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);

    // Reconstruct a stable 0..1 coordinate across the particle quad.
    int corner = gl_VertexID & 3;
    if (corner == 0) {
        particleCoord = vec2(1.0, 1.0);
    } else if (corner == 1) {
        particleCoord = vec2(0.0, 1.0);
    } else if (corner == 2) {
        particleCoord = vec2(0.0, 0.0);
    } else {
        particleCoord = vec2(1.0, 0.0);
    }
}
