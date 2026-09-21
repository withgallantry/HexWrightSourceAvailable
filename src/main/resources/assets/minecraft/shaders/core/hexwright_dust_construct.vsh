#version 150

// Controlled dust, formed - the compacted construct. The geometry is only a proxy: a box around the
// Region, whose back faces start a ray per pixel (see the fragment shader).
//
// Position is camera-relative world space. The camera rotation stays in ModelViewMat rather than in
// the vertices, so the fragment stage gets a direction it can march along in world orientation.

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 rayRel;

void main() {
    rayRel = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
