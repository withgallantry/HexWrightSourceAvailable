#version 150

#moj_import <fog.glsl>

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float GameTime;
uniform float DiscardThreshold;

in float vertexDistance;
in vec2 particleCoord;
in vec4 vertexColor;

out vec4 fragColor;

const float PI  = 3.14159265358979323846;
const float TAU = 6.28318530717958647692;

// --------- Tuning ---------
const float PORTAL_RADIUS = 0.98;
const float EDGE_SOFTNESS = 0.18;
const float CUBES_PER_DAY = 95.0;   // repeating inward-collapse speed
const float SPIN_PER_DAY  = 70.0;   // overall scene spin

const vec3 VOID_PURPLE   = vec3(0.028, 0.008, 0.055);
const vec3 DARK_PURPLE   = vec3(0.105, 0.028, 0.180);
const vec3 MID_PURPLE    = vec3(0.290, 0.090, 0.470);
const vec3 BRIGHT_PURPLE = vec3(0.590, 0.250, 0.860);
const vec3 HOT_PURPLE    = vec3(0.860, 0.520, 1.000);

float saturate(float x) {
    return clamp(x, 0.0, 1.0);
}

mat2 rotate2D(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat2(c, -s, s, c);
}

vec3 rotateX(vec3 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec3(p.x, c * p.y - s * p.z, s * p.y + c * p.z);
}

vec3 rotateY(vec3 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec3(c * p.x + s * p.z, p.y, -s * p.x + c * p.z);
}

vec3 rotateZ(vec3 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec3(c * p.x - s * p.y, s * p.x + c * p.y, p.z);
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);

    float a = hash21(i + vec2(0.0, 0.0));
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));

    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 m = mat2(1.6, 1.2, -1.2, 1.6);
    for (int i = 0; i < 4; ++i) {
        v += a * noise(p);
        p = m * p;
        a *= 0.5;
    }
    return v;
}

float segmentDistance(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 0.0001), 0.0, 1.0);
    return length(pa - ba * h);
}

vec3 cubeVertex(int id, float halfSize) {
    float x = (id == 0 || id == 2 || id == 4 || id == 6) ? -halfSize : halfSize;
    float y = (id == 0 || id == 1 || id == 4 || id == 5) ? -halfSize : halfSize;
    float z = (id < 4) ? -halfSize : halfSize;
    return vec3(x, y, z);
}

vec3 manifoldTransform(vec3 p, float halfSize, float spin, float fold, float collapse) {
    // Collapse/squash inward before rotation.
    p *= mix(1.0, 0.84, collapse);
    p.y *= mix(1.0, 0.68, collapse);

    float yMask = 1.0 - abs(p.y) / max(halfSize, 0.001);
    float xMask = 1.0 - abs(p.x) / max(halfSize, 0.001);

    // "Folding" deformation so the cube does not just shrink, it buckles.
    p.x += sign(p.z) * fold * yMask * halfSize * 0.42;
    p.z -= sign(p.x) * fold * yMask * halfSize * 0.38;
    p.y += sign(p.x) * fold * xMask * halfSize * 0.16;

    p = rotateY(p, spin);
    p = rotateX(p, spin * 0.74 + fold * 0.65 + 0.6);
    p = rotateZ(p, spin * 0.41 - fold * 0.22);

    return p;
}

vec2 projectPoint(vec3 p) {
    float invZ = 1.0 / (p.z + 3.9);
    return p.xy * invZ * 1.75;
}

void cubeLayer(vec2 uv, float globalTime, float phase, out float edges, out float shell, out float aura) {
    float progress = fract(globalTime + phase);
    float collapse = pow(progress, 0.82);
    float visibility = smoothstep(0.00, 0.08, progress)
                     * (1.0 - smoothstep(0.84, 1.00, progress));

    float halfSize = mix(1.18, 0.18, collapse);
    float spin = globalTime * TAU * 0.55 + phase * TAU * 0.7;
    float fold = sin(globalTime * TAU * 0.9 + phase * TAU * 2.0) * 0.55
               + (1.0 - collapse) * 0.20;

    vec2 p0 = projectPoint(manifoldTransform(cubeVertex(0, halfSize), halfSize, spin, fold, collapse));
    vec2 p1 = projectPoint(manifoldTransform(cubeVertex(1, halfSize), halfSize, spin, fold, collapse));
    vec2 p2 = projectPoint(manifoldTransform(cubeVertex(2, halfSize), halfSize, spin, fold, collapse));
    vec2 p3 = projectPoint(manifoldTransform(cubeVertex(3, halfSize), halfSize, spin, fold, collapse));
    vec2 p4 = projectPoint(manifoldTransform(cubeVertex(4, halfSize), halfSize, spin, fold, collapse));
    vec2 p5 = projectPoint(manifoldTransform(cubeVertex(5, halfSize), halfSize, spin, fold, collapse));
    vec2 p6 = projectPoint(manifoldTransform(cubeVertex(6, halfSize), halfSize, spin, fold, collapse));
    vec2 p7 = projectPoint(manifoldTransform(cubeVertex(7, halfSize), halfSize, spin, fold, collapse));

    float d = 1e9;
    // Back face
    d = min(d, segmentDistance(uv, p0, p1));
    d = min(d, segmentDistance(uv, p1, p3));
    d = min(d, segmentDistance(uv, p3, p2));
    d = min(d, segmentDistance(uv, p2, p0));
    // Front face
    d = min(d, segmentDistance(uv, p4, p5));
    d = min(d, segmentDistance(uv, p5, p7));
    d = min(d, segmentDistance(uv, p7, p6));
    d = min(d, segmentDistance(uv, p6, p4));
    // Connections
    d = min(d, segmentDistance(uv, p0, p4));
    d = min(d, segmentDistance(uv, p1, p5));
    d = min(d, segmentDistance(uv, p2, p6));
    d = min(d, segmentDistance(uv, p3, p7));

    float lineWidth = mix(0.028, 0.010, collapse);
    edges = (1.0 - smoothstep(lineWidth, lineWidth + 0.020, d)) * visibility;

    // Additional square-shell fold ripples to make the space feel like it is
    // folding through nested cube-like layers.
    vec2 q = rotate2D(spin * 0.35 + phase * 2.0) * uv;
    q.x += q.y * fold * 0.28;
    q.y -= q.x * fold * 0.10;
    float boxRadius = max(abs(q.x), abs(q.y));
    float shellRadius = mix(0.82, 0.10, collapse);
    shell = (1.0 - smoothstep(0.020, 0.075, abs(boxRadius - shellRadius)))
          * visibility * 0.75;

    aura = exp(-length(uv) * (2.8 + collapse * 1.6)) * visibility * (0.18 + (1.0 - collapse) * 0.15);
}

void main() {
    vec2 uv = particleCoord * 2.0 - 1.0;
    uv.y = -uv.y;

    float radius = length(uv);
    float tCollapse = GameTime * CUBES_PER_DAY;
    float tSpin = GameTime * TAU * SPIN_PER_DAY;

    // Slow overall viewport rotation so the manifold space feels active.
    uv = rotate2D(tSpin * 0.12) * uv;

    float background = 1.0 - smoothstep(0.08, 0.92, radius);
    float foldNoise = fbm(uv * 3.2 + vec2(GameTime * 40.0, -GameTime * 27.0));
    float foldNoise2 = fbm(uv * 5.6 + vec2(-GameTime * 18.0, GameTime * 33.0));

    float edges = 0.0;
    float shells = 0.0;
    float aura = 0.0;

    float e, s, a;
    cubeLayer(uv, tCollapse, 0.00, e, s, a); edges += e; shells += s; aura += a;
    cubeLayer(uv, tCollapse, 0.18, e, s, a); edges += e; shells += s; aura += a;
    cubeLayer(uv, tCollapse, 0.36, e, s, a); edges += e; shells += s; aura += a;
    cubeLayer(uv, tCollapse, 0.54, e, s, a); edges += e; shells += s; aura += a;
    cubeLayer(uv, tCollapse, 0.72, e, s, a); edges += e; shells += s; aura += a;

    float centreWell = 1.0 - smoothstep(0.0, 0.46, radius);
    float outerFade = 1.0 - smoothstep(PORTAL_RADIUS - EDGE_SOFTNESS,
                                       PORTAL_RADIUS,
                                       radius);

    // Break up the silhouette slightly so it dissipates instead of remaining a perfect disc.
    float irregularEdge = smoothstep(0.20, 0.82,
        outerFade + (foldNoise - 0.5) * 0.35);

    vec3 colour = VOID_PURPLE * 0.34;
    colour += DARK_PURPLE * background * 0.60;
    colour += MID_PURPLE * aura * 1.15;
    colour += MID_PURPLE * shells * 0.85;
    colour += BRIGHT_PURPLE * edges * 0.95;
    colour += HOT_PURPLE * edges * edges * 0.45;
    colour += BRIGHT_PURPLE * centreWell * 0.10;
    colour += MID_PURPLE * (foldNoise2 * 0.18 + foldNoise * 0.10) * background * 0.30;

    float alpha = 0.0;
    alpha += background * 0.30;
    alpha += aura * 0.42;
    alpha += shells * 0.34;
    alpha += edges * 0.58;
    alpha *= irregularEdge;
    alpha = max(alpha, centreWell * 0.16);
    alpha = saturate(alpha);

    vec4 tint = vertexColor * ColorModulator;
    colour *= tint.rgb;
    alpha *= tint.a;

    if (alpha < DiscardThreshold || radius > PORTAL_RADIUS + 0.04) {
        discard;
    }

    fragColor = linear_fog(
        vec4(colour, alpha),
        vertexDistance,
        FogStart,
        FogEnd,
        FogColor
    );
}
