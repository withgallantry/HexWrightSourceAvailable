
#define STYLE_NEBULA 0
#define STYLE_DAY 1
#define STYLE_LATTICE 2
#define STYLE_ORRERY 3
#define STYLE_PRISM 4

vec3 faceDirection(int face, vec2 uv) {
    float side = (face & 1) == 0 ? 1.0 : -1.0;
    if (face < 2) {
        return vec3(side, uv.y, uv.x);
    }
    if (face < 4) {
        return vec3(uv.x, side, uv.y);
    }
    return vec3(uv.x, uv.y, side);
}

int faceOf(vec3 dir, out vec2 uv) {
    vec3 a = abs(dir);
    if (a.x >= a.y && a.x >= a.z) {
        uv = dir.zy / a.x;
        return dir.x > 0.0 ? 0 : 1;
    }
    if (a.y >= a.z) {
        uv = dir.xz / a.y;
        return dir.y > 0.0 ? 2 : 3;
    }
    uv = dir.xy / a.z;
    return dir.z > 0.0 ? 4 : 5;
}

mat2x3 faceAxes(int face) {
    if (face < 2) {
        return mat2x3(vec3(0.0, 0.0, 1.0), vec3(0.0, 1.0, 0.0));
    }
    if (face < 4) {
        return mat2x3(vec3(1.0, 0.0, 0.0), vec3(0.0, 0.0, 1.0));
    }
    return mat2x3(vec3(1.0, 0.0, 0.0), vec3(0.0, 1.0, 0.0));
}

uint hash(uint x) {
    x ^= x >> 16;
    x *= 0x7feb352du;
    x ^= x >> 15;
    x *= 0x846ca68bu;
    x ^= x >> 16;
    return x;
}

float unit(uint h) {
    return float(h & 0xffu) / 255.0;
}
