#version 150


uniform int Style;
uniform float Progress;
uniform float Age;
uniform float Seed;
uniform vec2 Extent;
uniform vec4 Segment;
uniform vec3 Color0;
uniform vec3 Color1;
uniform vec3 Color2;
uniform vec3 Color3;
uniform float Intensity;

in vec2 localPos;

out vec4 fragColor;

const float PI = 3.14159265;
const float TAU = 6.28318531;

const int SEAL = 0;
const int LATTICE = 1;
const int STROKE = 2;
const int BEAM = 3;
const int STAR = 4;
const int RING = 5;
const int ORBIT = 6;
const int THREAD = 7;
const int PETALS = 8;
const int SHELL = 9;
const int VORTEX = 10;
const int BAND = 11;
const int STREAK = 12;
const int ARC = 13;
const int EYE = 14;
const int ECLIPSE = 15;
const int SHARD = 16;
const int WINGS = 17;
const int RIPPLE = 18;
const int GEARS = 19;
const int FLAME = 20;

float hold = 0.0;

float hash11(float n) {
    return fract(sin(n * 127.1 + Seed * 3.7) * 43758.5453);
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21) + Seed * 0.173);
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float hashCell(vec2 cell) {
    uvec2 q = uvec2(ivec2(cell)) * uvec2(1597334673u, 3812015801u);
    uint n = (q.x ^ q.y ^ uint(Seed * 997.0)) * 1597334673u;
    return float(n) * (1.0 / 4294967295.0);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hashCell(i);
    float b = hashCell(i + vec2(1.0, 0.0));
    float c = hashCell(i + vec2(0.0, 1.0));
    float d = hashCell(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float ease(float x) {
    x = clamp(x, 0.0, 1.0);
    return 1.0 - pow(1.0 - x, 3.0);
}

float easeBack(float x) {
    x = clamp(x, 0.0, 1.0) - 1.0;
    return 1.0 + 2.70158 * x * x * x + 1.70158 * x * x;
}

mat2 rot(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat2(c, -s, s, c);
}

vec3 palette(float t) {
    float x = fract(t) * 4.0;
    float f = smoothstep(0.0, 1.0, fract(x));
    if (x < 1.0) return mix(Color0, Color1, f);
    if (x < 2.0) return mix(Color1, Color2, f);
    if (x < 3.0) return mix(Color2, Color3, f);
    return mix(Color3, Color0, f);
}

vec3 hot(vec3 c, float k) {
    return mix(c, vec3(1.0), clamp(k, 0.0, 1.0));
}

float line(float d, float w) {
    float aa = fwidth(d) * 1.2 + 1e-5;
    float core = 1.0 - smoothstep(w - aa, w + aa, d);
    float halo = exp(-(d * d) / (w * w * 18.0)) * 0.45;
    return core + halo;
}

float segment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-6), 0.0, 1.0);
    return length(pa - ba * h);
}

float jag(float x, float seed) {
    float i = floor(x);
    return mix(hashCell(vec2(i, seed)), hashCell(vec2(i + 1.0, seed)), fract(x)) - 0.5;
}

float discEdge(float r) {
    return 1.0 - smoothstep(1.0, 1.1, r);
}

vec4 seal(vec2 p) {
    float t = Progress;
    float grow = ease(t / 0.16);
    float fade = 1.0 - smoothstep(0.6, 1.0, t);
    vec2 q = p / max(grow, 0.02);
    float r = length(q);
    float a = atan(q.y, q.x);
    float w = 0.03 / Extent.x;
    float lit = 0.0;

    lit += line(abs(r - 0.96), w * 1.3);
    lit += line(abs(r - 0.84), w * 0.8) * 0.8;

    float cells = 30.0;
    float ca = (a + Age * 0.35) / TAU * cells;
    float cell = mod(floor(ca), cells);
    float along = (fract(ca) - 0.5) * TAU * 0.90 / cells;
    float kind = hash11(cell);
    float reach = kind > 0.66 ? 0.035 : 0.018;
    float radial = max(abs(r - 0.90) - reach, 0.0);
    lit += kind > 0.2 ? line(length(vec2(along, radial)), w * 0.7) * 0.9 : 0.0;

    vec2 s = rot(-Age * 0.5) * q;
    float star = 0.0;
    float nodes = 0.0;
    for (int k = 0; k < 6; k++) {
        float th = float(k) * PI / 3.0 + PI / 6.0;
        vec2 n = vec2(cos(th), sin(th));
        star = max(star, line(abs(dot(s, n) - 0.39), w));
        nodes += line(abs(length(s - n * 0.78) - 0.055), w * 0.8);
    }
    float raa = fwidth(r) + 1e-5;
    star *= 1.0 - smoothstep(0.78 - raa, 0.78 + raa, r);
    lit += star + nodes;

    lit += line(abs(r - 0.24), w * 0.8) * 0.7;
    lit += exp(-r * r / 0.004) * 0.6;

    float fill = 0.07 * (1.0 - smoothstep(0.92, 0.97, r));
    float stamp = line(abs(length(p) - ease(t / 0.2)), w * 2.0) * (1.0 - smoothstep(0.1, 0.25, t)) * 1.5;

    vec3 col = palette(a / TAU + Age * 0.06);
    float total = (lit + fill) * fade * discEdge(r) + stamp * discEdge(length(p));
    return vec4(hot(col, lit * 0.15), total);
}

float hexDistance(vec2 ij) {
    return max(max(abs(ij.x), abs(ij.y)), abs(ij.x + ij.y));
}

vec4 lattice(vec2 p) {
    float t = Progress;
    float pitch = 0.3 / Extent.x;
    vec2 e1 = vec2(pitch, 0.0);
    vec2 e2 = vec2(0.5, 0.8660254) * pitch;

    float j = p.y / e2.y;
    float i = p.x / pitch - 0.5 * j;
    vec2 base = floor(vec2(i, j));
    float best = 1e9;
    vec2 id = base;
    for (int dx = 0; dx <= 1; dx++) {
        for (int dy = 0; dy <= 1; dy++) {
            vec2 c = base + vec2(float(dx), float(dy));
            float d = length(p - (c.x * e1 + c.y * e2));
            if (d < best) {
                best = d;
                id = c;
            }
        }
    }
    vec2 dotPos = id.x * e1 + id.y * e2;
    float front = ease(t / 0.7) * 1.1;
    float hd = hexDistance(id) * pitch;
    float wave = exp(-pow((hd - front) / 0.09, 2.0));
    float wake = hd < front ? exp(-(front - hd) * 4.0) * 0.35 : 0.0;
    float dotR = 0.05 / Extent.x;
    float dotGlow = exp(-best * best / (dotR * dotR)) + exp(-best * best / (dotR * dotR * 9.0)) * 0.3;
    float rim = 1.0 - smoothstep(0.75, 1.0, length(dotPos));
    float lit = dotGlow * (wave * 2.0 + wake * 1.5 + 0.12) * rim;

    vec2 steps[3] = vec2[3](vec2(1.0, 0.0), vec2(0.0, 1.0), vec2(-1.0, 1.0));
    float w = 0.022 / Extent.x;
    float strokes = 0.0;
    for (int k = 0; k < 6; k++) {
        vec2 hop = k < 3 ? steps[k] : -steps[k - 3];
        vec2 lower = k < 3 ? id : id + hop;
        float axis = float(k < 3 ? k : k - 3);
        if (hash21(lower * 1.7 + axis * 13.1) < 0.78) {
            continue;
        }
        vec2 dir = hop.x * e1 + hop.y * e2;
        vec2 rel = p - dotPos;
        float h = clamp(dot(rel, dir) / dot(dir, dir), 0.0, 1.0);
        float dist = length(rel - dir * h);
        float hm = hexDistance(id + hop * 0.5) * pitch;
        float edgeWave = exp(-pow((hm - front) / 0.12, 2.0)) + (hm < front ? exp(-(front - hm) * 3.0) * 0.5 : 0.0);
        strokes = max(strokes, line(dist, w) * edgeWave);
    }
    lit += strokes * rim;

    float fade = 1.0 - smoothstep(0.6, 1.0, t);
    vec3 col = palette(hd * 0.8 - Age * 0.15);
    return vec4(hot(col, wave * 0.4), lit * fade * discEdge(length(p)));
}

vec4 stroke(vec2 uv) {
    float t = Progress;
    float len = Extent.x;
    float w = Extent.y;
    float trace = clamp(t / 0.5, 0.0, 1.0);
    float drawn = clamp((trace - Segment.x) / max(Segment.y - Segment.x, 1e-4), 0.0, 1.0);
    if (drawn <= 0.0) {
        return vec4(0.0);
    }
    float head = drawn * len;
    float dx = max(max(-uv.x, uv.x - head), 0.0);
    float lit = line(length(vec2(dx, uv.y)), w);
    vec2 toHead = uv - vec2(head, 0.0);
    float nib = drawn < 1.0 ? exp(-dot(toHead, toHead) / (w * w * 6.0)) * 1.5 : 0.0;
    float node = exp(-dot(uv, uv) / (w * w * 5.0)) * 0.5;
    float fade = 1.0 - smoothstep(0.65, 1.0, t);
    float along = mix(Segment.z, Segment.w, clamp(uv.x / len, 0.0, 1.0));
    vec3 col = palette(along * 0.75 + Age * 0.1);
    return vec4(hot(col, nib * 0.6 + 0.15), (lit + nib + node) * fade);
}

vec4 beam(vec2 uv) {
    float t = Progress;
    float h = uv.x / Extent.x;
    float rise = ease(t / 0.2);
    float narrow = smoothstep(0.0, 0.06, t) * (1.0 - 0.75 * smoothstep(0.35, 1.0, t));
    float w = max(Extent.y * narrow, 1e-3);
    float x = uv.y;
    float core = exp(-pow(x / (w * 0.22), 2.0));
    float glow = exp(-pow(x / (w * 0.7), 2.0)) * 0.5;
    float grain = noise(vec2(x / w * 3.0, uv.x * 1.2 - Age * 5.0));
    grain = 0.55 + 0.9 * grain * grain;
    float vertical = smoothstep(0.0, 0.03, h) * (1.0 - smoothstep(rise * 0.55, rise + 1e-3, h));
    float fade = 1.0 - smoothstep(0.55, 1.0, t);
    vec3 col = palette(h * 0.6 - Age * 0.25);
    return vec4(hot(col, core * 0.7), (core * 1.3 + glow * grain) * vertical * fade);
}

vec4 star(vec2 p) {
    float t = Progress;
    float r = length(p);
    float a = atan(p.y, p.x);
    float grow = ease(t / 0.2);
    float flash = exp(-t * 7.0);
    float core = exp(-r * r / (0.012 + 0.03 * flash)) * (0.8 + 2.2 * flash);
    float rays = pow(abs(cos(3.0 * (a + Age * 0.6))), 48.0) * exp(-r / (0.35 * grow + 0.01));
    float rays2 = pow(abs(cos(6.0 * (a - Age * 0.9) + PI / 2.0)), 90.0) * exp(-r / (0.18 * grow + 0.01)) * 0.7;
    float halo = exp(-r * r / 0.15) * 0.25;
    float fade = 1.0 - smoothstep(0.3, 1.0, t);
    vec3 col = palette(a / TAU + Age * 0.1);
    return vec4(hot(col, core * 0.5 + 0.2), (core + rays + rays2 + halo) * fade * discEdge(r));
}

vec4 ring(vec2 p) {
    float t = Progress;
    float r = length(p);
    float a = atan(p.y, p.x);
    float radius = 0.1 + 0.88 * ease(t);
    float w = min(mix(0.04, 0.012, t), 0.08 / Extent.x);
    float spread = min(0.12, 0.3 / Extent.x);
    float lit = line(abs(r - radius), w) * 1.2;
    float haze = exp(-pow((r - radius + spread) / spread, 2.0)) * 0.25;
    float fade = pow(1.0 - t, 1.5);
    vec3 col = palette(a / TAU * 2.0 + Age * 0.2);
    return vec4(hot(col, 0.25), (lit + haze) * fade * discEdge(r));
}

vec4 orbit(vec2 p) {
    float t = Progress;
    float r = length(p);
    float a = atan(p.y, p.x);
    float grow = ease(t / 0.18);
    float fade = 1.0 - smoothstep(0.65, 1.0, t);
    float radius = 0.88 * grow;
    float w = 0.02 / Extent.x;
    float speed = Segment.x;
    float head = Seed * 1.7 + Age * speed;
    float behind = mod((head - a) * sign(speed), TAU);
    float tail = exp(-behind * 1.4);
    vec2 headPos = radius * vec2(cos(head), sin(head));
    vec2 toHead = p - headPos;
    float comet = exp(-dot(toHead, toHead) / (0.004 + w * w * 4.0)) * 1.6;
    float lit = line(abs(r - radius), w) * (0.12 + tail * 1.1) + comet;
    vec3 col = palette(Segment.y + behind / TAU * 0.5);
    return vec4(hot(col, comet * 0.5 + tail * 0.2), lit * fade * discEdge(r));
}

vec4 thread(vec2 uv) {
    float t = Progress;
    float len = Extent.x;
    float u = uv.x / len;
    float pinned = sin(clamp(u, 0.0, 1.0) * PI);
    float phase = uv.x * 2.2 - Age * 9.0 + Seed;
    float sway = sin(phase) * Extent.y * 0.45 * pinned;
    float w = 0.022;
    float near = 0.5 + 0.5 * cos(phase);
    float lit = line(abs(uv.y - sway), w) * (0.55 + 0.45 * near)
        + line(abs(uv.y + sway), w) * (0.55 + 0.45 * (1.0 - near)) * 0.8;
    float head = ease(t / 0.3);
    float tail = smoothstep(0.4, 0.95, t);
    float shown = (1.0 - smoothstep(head - 0.02, head + 0.02, u)) * smoothstep(tail - 0.03, tail + 0.03, u);
    float travel = (u - head) * len;
    float spark = head < 1.0 ? exp(-travel * travel / 0.02 - uv.y * uv.y / 0.01) * 2.0 : 0.0;
    vec3 col = palette(u * 0.8 + Age * 0.3);
    return vec4(hot(col, spark * 0.5 + 0.1), lit * shown + spark);
}


float petalDistance(float r, float a, float size, float k) {
    float c = cos(3.0 * a);
    float ac = max(abs(c), 1e-4);
    float f = size * pow(ac, k);
    float df = size * k * pow(ac, k - 1.0) * -3.0 * sin(3.0 * a) * sign(c);
    return (r - f) / sqrt(1.0 + min(df * df / max(r * r, 1e-4), 9.0));
}

vec4 petals(vec2 p) {
    float t = Progress;
    float open1 = easeBack(t / 0.3);
    float open2 = easeBack((t - 0.08) / 0.3);
    float fade = 1.0 - smoothstep(0.6, 1.0, t);
    vec2 q = rot(Age * 0.3) * p;
    float r = length(q);
    float a = atan(q.y, q.x);
    float w = 0.028 / Extent.x;
    float outer = petalDistance(r, a, 0.95 * open1, 0.55);
    float inner = petalDistance(r, a + PI / 6.0, 0.6 * open2, 0.55);
    float lit = line(abs(outer), w) + line(abs(inner), w) * 0.8;
    float fill = (1.0 - smoothstep(-w, w, outer)) * 0.1 + (1.0 - smoothstep(-w, w, inner)) * 0.08;
    float sector = PI / 3.0;
    float offAxis = mod(a + sector * 0.5, sector) - sector * 0.5;
    float vein = line(abs(r * sin(offAxis)), w * 0.6) * (1.0 - smoothstep(0.0, 0.05, outer)) * 0.5 * step(0.08, r);
    float centre = line(abs(r - 0.1 * open1), w) + exp(-r * r / 0.002);
    vec3 col = palette(r * 0.9 + Age * 0.05);
    return vec4(hot(col, 0.2), (lit + fill + vein + centre) * fade * discEdge(length(p)));
}


vec4 hexTile(vec2 p) {
    const vec2 s = vec2(1.0, 1.7320508);
    vec4 centres = floor(vec4(p, p - vec2(0.5, 1.0)) / s.xyxy) + 0.5;
    vec4 rel = vec4(p - centres.xy * s, p - (centres.zw + 0.5) * s);
    return dot(rel.xy, rel.xy) < dot(rel.zw, rel.zw)
        ? vec4(rel.xy, centres.xy)
        : vec4(rel.zw, centres.zw + 0.5);
}

vec4 shell(vec2 p) {
    float t = Progress;
    float grow = easeBack(t / 0.22);
    vec2 q = p / max(grow, 0.02);
    float r = length(q);
    float fade = 1.0 - smoothstep(0.65, 1.0, t);
    float outside = exp(-pow(max(r - 1.0, 0.0) / 0.04, 2.0)) * (1.0 - smoothstep(1.04, 1.1, r));
    if (r > 1.0) {
        return vec4(palette(Age * 0.1), outside * 0.8 * fade);
    }
    float z = sqrt(max(1.0 - r * r, 0.0));
    vec3 n = vec3(q, z);
    float limb = pow(1.0 - z, 2.2);
    float lon = atan(n.x, n.z) + Age * 0.25;
    float lat = asin(clamp(n.y, -1.0, 1.0));
    float density = 1.6 + Extent.x * 0.6;
    vec4 cell = hexTile(vec2(lon, lat) * density);
    vec2 rel = abs(cell.xy);
    float edge = 0.5 - max(dot(rel, vec2(0.5, 0.8660254)), rel.x);
    float h = hash21(cell.zw);
    float sweep = ease(t / 0.45) * 2.2;
    float band = exp(-pow(((1.0 - n.y) - sweep) / 0.25, 2.0));
    float breakup = 1.0 - smoothstep(h - 0.05, h + 0.05, (t - 0.55) / 0.4);
    float lines = line(edge, 0.03);
    float fillLight = (0.03 + band * 0.3) * (0.6 + 0.4 * h);
    float lit = (lines * (0.12 + limb * 0.9 + band * 0.8) + fillLight + limb * 0.35) * breakup;
    vec3 col = palette(lat / PI + h * 0.3 + Age * 0.05);
    return vec4(hot(col, limb * 0.3), lit * fade);
}

vec4 vortex(vec2 p) {
    float t = Progress;
    float pull = t * t;
    vec2 q = p / max(1.0 - 0.85 * pull, 0.05);
    float r = length(q);
    float a = atan(q.y, q.x);
    float spin = Age * (2.0 + 6.0 * pull);
    float s = a / TAU * 3.0 + log(r + 0.04) * 1.1 + spin / TAU;
    float arm = pow(0.5 + 0.5 * cos(TAU * s), 9.0);
    float grain = 0.6 + 0.6 * noise(vec2(a * 3.0 + spin, r * 8.0));
    float mask = (1.0 - smoothstep(0.7, 1.0, r)) * smoothstep(0.02, 0.18, r);
    float body = arm * grain * mask * 1.5 + line(abs(r - 0.97), 0.012) * 0.5;
    float shown = smoothstep(0.0, 0.15, t) * (1.0 - smoothstep(0.82, 0.95, t));
    float pr = length(p);
    float flash = exp(-pr * pr / 0.012) * smoothstep(0.8, 0.9, t) * (1.0 - smoothstep(0.9, 1.0, t)) * 3.0;
    float thrown = clamp((t - 0.88) / 0.12, 0.0, 1.0);
    float burst = t > 0.88 ? line(abs(pr - thrown * 0.8), 0.03) * (1.0 - thrown) : 0.0;
    vec3 col = palette(r * 0.7 - spin / TAU * 0.3);
    return vec4(hot(col, flash * 0.5 + 0.1), body * shown + (flash + burst) * discEdge(pr));
}

vec4 band(vec2 uv) {
    float t = Progress;
    float circumference = Extent.x;
    float rise = Extent.y * ease(t / 0.2);
    float fade = 1.0 - smoothstep(0.6, 1.0, t);
    float cells = max(floor(circumference / 0.32), 6.0);
    float pitch = circumference / cells;
    float x = mod(uv.x + Age * 0.5, circumference);
    float cell = floor(x / pitch);
    vec2 q = vec2((fract(x / pitch) - 0.5) * pitch, uv.y);
    float w = 0.018;
    float lit = line(abs(uv.y - rise), w) + line(abs(uv.y + rise), w);
    float s = min(pitch * 0.28, rise * 0.45);
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        vec2 from = floor(vec2(hash11(cell * 7.0 + fk * 1.3), hash11(cell * 13.0 + fk * 2.9 + 5.0)) * 3.0) - 1.0;
        float pick = floor(hash11(cell * 3.1 + fk * 4.7 + 9.0) * 4.0);
        vec2 hop = pick < 1.0 ? vec2(1.0, 0.0) : pick < 2.0 ? vec2(0.0, 1.0) : pick < 3.0 ? vec2(1.0, 1.0) : vec2(1.0, -1.0);
        vec2 to = clamp(from + hop, -1.0, 1.0);
        lit += line(segment(q, from * s, to * s), w) * 0.9;
    }
    lit += (1.0 - smoothstep(rise - 0.01, rise + 0.01, abs(uv.y))) * 0.05;
    vec3 col = palette(cell / cells + Age * 0.05);
    return vec4(hot(col, 0.2), lit * fade);
}

vec4 streak(vec2 uv) {
    float len = Extent.x;
    float w = Extent.y;
    float local = (Progress - Segment.x) / max(Segment.y, 1e-4);
    if (local <= 0.0) {
        return vec4(0.0);
    }
    float head = pow(min(local, 1.0), 1.6) * len;
    float behind = head - uv.x;
    float tail = behind >= 0.0 ? exp(-behind * 1.6) : 0.0;
    float core = exp(-uv.y * uv.y / (w * w));
    float glow = exp(-uv.y * uv.y / (w * w * 16.0)) * 0.4;
    float falling = local < 1.0 ? 1.0 : 0.0;
    float nib = exp(-(behind * behind + uv.y * uv.y) / (w * w * 9.0)) * 1.5 * falling;
    float linger = falling + (1.0 - falling) * (1.0 - smoothstep(1.0, 1.35, local));
    vec3 col = palette(uv.x / len * 0.6 + Segment.x * 2.0 + Age * 0.1);
    return vec4(hot(col, nib * 0.5 + core * 0.3), ((core + glow) * tail + nib) * linger);
}

float boltOffset(float x, float len, float seed) {
    float pinned = sin(clamp(x / len, 0.0, 1.0) * PI);
    return (jag(x * 2.5, seed) * 0.9 + jag(x * 7.0, seed + 31.0) * 0.35) * pinned * min(len * 0.18, 0.6);
}

vec4 arc(vec2 uv) {
    float t = Progress;
    float len = Extent.x;
    float u = clamp(uv.x / len, 0.0, 1.0);
    float frame = floor(Age * 10.0);
    float seed = Segment.x * 53.0 + frame * 17.0;
    float offset = boltOffset(uv.x, len, seed);
    float slope = (boltOffset(uv.x + 0.01, len, seed) - offset) / 0.01;
    float across = abs(uv.y - offset) / sqrt(1.0 + slope * slope);
    float reach = ease(t / 0.12);
    float shown = 1.0 - smoothstep(reach - 0.02, reach + 0.02, u);
    float flicker = 0.65 + 0.35 * hash11(frame + Segment.x * 31.0);
    float fade = 1.0 - smoothstep(0.35, 1.0, t);
    float lit = line(across, Extent.y) * shown * flicker * fade * 1.3;
    vec3 col = palette(u * 0.5 + Segment.x + Age * 0.4);
    return vec4(hot(col, 0.45), lit);
}

vec4 eye(vec2 p) {
    float t = Progress;
    float open = max(ease(t / 0.22) * (1.0 - smoothstep(0.78, 0.95, t)), 0.04);
    float fade = 1.0 - smoothstep(0.85, 1.0, t);
    float x = p.x / 0.95;
    float within = 1.0 - smoothstep(0.98, 1.02, abs(x));
    float lid = 0.48 * pow(max(1.0 - x * x, 0.0), 0.9);
    float upper = lid * open;
    float lower = -lid * open * 0.8;
    float w = 0.03 / Extent.x;
    float lids = (line(abs(p.y - upper), w) + line(abs(p.y - lower), w)) * within;
    float brow = line(abs(p.y - lid * 1.25 - 0.05), w * 0.7) * within * 0.35 * smoothstep(0.0, 0.2, t);
    float aa = fwidth(p.y) + 1e-5;
    float inside = smoothstep(lower - aa, lower + aa, p.y) * (1.0 - smoothstep(upper - aa, upper + aa, p.y)) * within;
    float r = length(p);
    float a = atan(p.y, p.x);
    float iris = line(abs(r - 0.32), w);
    float around = (a + PI) / TAU * 36.0;
    float rays = step(0.11, r) * step(r, 0.32) * (0.4 + 0.6 * hash11(floor(around)))
        * pow(abs(cos((fract(around) - 0.5) * PI)), 8.0) * 0.5;
    float pupil = exp(-r * r / 0.006) * 1.2 + line(abs(r - 0.1), w) * 0.8;
    float regard = pow(abs(cos(a * 6.0 + Age * 0.5)), 30.0) * exp(-max(r - 0.6, 0.0) * 4.0) * step(0.62, r) * 0.4 * open;
    float lit = lids + brow + (iris + rays + pupil + 0.06) * inside + regard;
    vec3 col = palette(r * 0.8 + Age * 0.08);
    return vec4(hot(col, pupil * 0.4 + 0.1), lit * fade * discEdge(r));
}

vec4 eclipse(vec2 p) {
    float t = Progress;
    float grow = easeBack(t / 0.2);
    float fade = 1.0 - smoothstep(0.65, 1.0, t);
    vec2 q = p / max(grow, 0.02);
    float r = length(q);
    float a = atan(q.y, q.x);
    float radius = 0.42;
    float aa = fwidth(r) + 1e-5;
    float disc = 1.0 - smoothstep(radius - aa, radius + aa, r);
    hold = disc * 0.85 * fade * Intensity;
    float rim = line(abs(r - radius), 0.012) * 1.3;
    vec2 heading = vec2(cos(a), sin(a));
    float writhe = noise(heading * 2.0 + vec2(Age * 0.3, Age * 0.2)) * 0.6
        + noise(heading * 5.0 - vec2(Age * 0.5, Age * 0.4)) * 0.4;
    float reach = 0.08 + writhe * 0.5;
    float corona = exp(-max(r - radius, 0.0) / max(reach, 0.02) * 2.2) * (1.0 - disc);
    float streamers = pow(abs(cos(a * 9.0 + writhe * 3.0)), 6.0) * corona * 0.6;
    vec2 toDiamond = q - vec2(radius * 0.71);
    float diamond = exp(-dot(toDiamond, toDiamond) / 0.004) * (1.0 - smoothstep(0.0, 0.25, t)) * 3.0;
    float lit = (rim * (1.0 - disc * 0.5) + corona * 0.9 + streamers) * fade + diamond;
    vec3 col = palette(a / TAU + Age * 0.05);
    return vec4(hot(col, 0.15 + diamond * 0.5), lit * discEdge(r));
}

vec4 shard(vec2 p) {
    float t = Progress;
    vec2 q = rot(-Segment.x) * p;
    float along = abs(q.y);
    float taper = 0.3 / 0.55;
    float width = 0.3 * clamp((1.0 - along) / 0.55, 0.0, 1.0);
    float edge = (abs(q.x) - width) / (along > 0.45 ? sqrt(1.0 + taper * taper) : 1.0);
    float w = 0.025 / Extent.x;
    float inside = 1.0 - smoothstep(-w, w, edge);
    float outline = line(abs(edge), w);
    float ridge = line(abs(q.x), w * 0.7) * inside;
    float shoulder = line(abs(along - 0.45), w * 0.7) * inside;
    float face = q.x > 0.0 ? 0.28 : 0.1;
    float glintAt = mix(-1.0, 1.0, fract(Age * 1.3 + Segment.y));
    float glint = exp(-pow((q.y - glintAt) / 0.12, 2.0)) * inside * 0.9;
    float shown = smoothstep(0.0, 0.08, t) * (1.0 - smoothstep(0.6, 1.0, t));
    vec3 col = palette(q.y * 0.3 + Segment.y);
    return vec4(hot(col, glint * 0.6 + 0.1),
        (outline + ridge * 0.6 + shoulder * 0.5 + face * inside + glint) * shown * discEdge(length(p)));
}


float feather(vec2 p, vec2 root, vec2 dir, float a, float b) {
    vec2 rel = p - root - dir * a;
    vec2 local = vec2(dot(rel, dir), dot(rel, vec2(-dir.y, dir.x)));
    return (length(local / vec2(a, b)) - 1.0) * b;
}

vec4 wings(vec2 p) {
    float t = Progress;
    float open = easeBack(t / 0.35);
    float beat = sin(clamp((t - 0.35) / 0.4, 0.0, 1.0) * PI) * 0.18;
    float fade = 1.0 - smoothstep(0.7, 1.0, t);
    vec2 q = vec2(abs(p.x), p.y);
    vec2 root = vec2(0.08, -0.05);
    float w = 0.022 / Extent.x;
    float lit = 0.0;
    for (int i = 0; i < 7; i++) {
        float fi = float(i) / 6.0;
        float along = mix(1.25, -0.15, fi) * open + 1.45 * (1.0 - open);
        vec2 base = root + vec2(cos(along), sin(along)) * 0.3 * open;
        float angle = mix(1.5, mix(1.15, -0.35, fi), open) + beat * (1.0 - fi * 0.5);
        vec2 dir = vec2(cos(angle), sin(angle));
        float len = (0.75 - 0.3 * fi) * 0.5;
        float d = feather(q, base, dir, len, 0.065);
        float inside = 1.0 - smoothstep(-w, w, d);
        float quill = line(abs(dot(q - base, vec2(-dir.y, dir.x))), w * 0.6) * inside;
        lit += line(abs(d), w) * 0.8 + quill * 0.4 + inside * 0.03;
    }
    vec2 fromRoot = q - root;
    float bone = line(abs(length(fromRoot) - 0.3), w * 1.2)
        * step(-0.3, atan(fromRoot.y, fromRoot.x)) * step(atan(fromRoot.y, fromRoot.x), 1.4) * open;
    float heart = exp(-dot(p, p) / 0.006) * 0.5;
    vec3 col = palette(length(p) * 0.8 + Age * 0.05);
    return vec4(hot(col, 0.25 + heart * 0.4), (lit + bone + heart) * fade * discEdge(length(p)));
}

vec4 ripple(vec2 p) {
    float t = Progress;
    float r = length(p);
    float w = 0.02 / Extent.x;
    float lit = 0.0;
    for (int k = 0; k < 4; k++) {
        float start = float(k) * 0.12;
        float local = (t - start) / (1.0 - start);
        if (local <= 0.0) {
            continue;
        }
        float crest = ease(local) * 0.95;
        float strength = (1.0 - local) * (1.0 - float(k) * 0.2);
        lit += line(abs(r - crest), w * (1.0 + 1.5 * (1.0 - local))) * strength;
        lit += exp(-pow((r - crest + 0.06) / 0.05, 2.0)) * 0.15 * strength;
    }
    float splash = exp(-r * r / 0.003) * (1.0 - smoothstep(0.0, 0.2, t)) * 2.0;
    vec3 col = palette(r * 1.2 - Age * 0.2);
    return vec4(hot(col, splash * 0.5 + 0.15), (lit + splash) * discEdge(r));
}

float gear(vec2 p, vec2 centre, float radius, float teeth, float spokes, float angle, float w) {
    vec2 rel = p - centre;
    float r = length(rel);
    float a = atan(rel.y, rel.x) + angle;
    float tooth = clamp(cos(a * teeth) * 3.0, -1.0, 1.0) * 0.5 + 0.5;
    float rim = line(abs(r - (radius + tooth * radius * 0.14)), w);
    float inner = line(abs(r - radius * 0.72), w * 0.8);
    float sector = TAU / spokes;
    float offSpoke = mod(a + sector * 0.5, sector) - sector * 0.5;
    float spoke = line(abs(r * sin(offSpoke)), w * 0.8) * step(radius * 0.18, r) * step(r, radius * 0.72);
    float hub = line(abs(r - radius * 0.18), w * 0.8) + exp(-r * r / (radius * radius * 0.004));
    return rim + inner * 0.7 + spoke * 0.8 + hub * 0.7;
}

vec4 gears(vec2 p) {
    float t = Progress;
    float grow = easeBack(t / 0.2);
    float fade = 1.0 - smoothstep(0.65, 1.0, t);
    vec2 q = p / max(grow, 0.02);
    float w = 0.025 / Extent.x;
    float turn = Age * 1.2;
    float lit = gear(q, vec2(0.0), 0.42, 14.0, 6.0, turn, w);
    vec2 first = vec2(cos(0.6), sin(0.6)) * (0.42 + 0.24 + 0.07);
    lit += gear(q, first, 0.24, 8.0, 4.0, -turn * 14.0 / 8.0 + 0.2, w);
    vec2 second = vec2(cos(3.6), sin(3.6)) * (0.42 + 0.2 + 0.07);
    lit += gear(q, second, 0.2, 7.0, 3.0, -turn * 2.0 + 0.4, w);
    vec3 col = palette(length(q) * 0.7 + Age * 0.05);
    return vec4(hot(col, 0.2), lit * fade * discEdge(length(q)));
}

float periodicNoise(vec2 p, float period) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float x0 = mod(i.x, period);
    float x1 = mod(i.x + 1.0, period);
    float a = hashCell(vec2(x0, i.y));
    float b = hashCell(vec2(x1, i.y));
    float c = hashCell(vec2(x0, i.y + 1.0));
    float d = hashCell(vec2(x1, i.y + 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

vec4 flame(vec2 uv) {
    float t = Progress;
    float height = (uv.y + Extent.y) / (2.0 * Extent.y);
    if (height < 0.0) {
        return vec4(0.0);
    }
    float rise = max(ease(t / 0.15), 0.05);
    float fade = 1.0 - smoothstep(0.6, 1.0, t);
    float period = max(floor(Extent.x * 1.25) * 2.0, 4.0);
    float x = uv.x / Extent.x * period;
    float flow = Age * 2.2;
    float n = periodicNoise(vec2(x, height * 2.0 - flow), period) * 0.6
        + periodicNoise(vec2(x * 2.0, height * 4.0 - flow * 1.5), period * 2.0) * 0.3
        + periodicNoise(vec2(x * 4.0, height * 8.0 - flow * 2.2), period * 4.0) * 0.1;
    float sway = periodicNoise(vec2(x * 0.5, height * 1.5 - flow * 0.7), period * 0.5) * 0.8;
    float tongues = pow(0.5 + 0.5 * cos(TAU * (x * 0.5 + sway)), 2.0);
    float fuel = n * 0.6 + tongues * 0.5;
    float cut = height * 0.8 / rise + 0.12;
    float body = smoothstep(cut, cut + 0.1, fuel);
    float core = smoothstep(cut + 0.12, cut + 0.3, fuel);
    float root = exp(-height * 8.0) * 0.4;
    vec3 col = palette(height * 0.5 + uv.x / Extent.x);
    return vec4(hot(col, core * 0.45 + root * 0.3), (body * 0.45 + core * 0.4 + root) * fade);
}

void main() {
    vec2 disc = localPos / Extent.x;
    vec4 c;
    if (Style == SEAL) {
        c = seal(disc);
    } else if (Style == LATTICE) {
        c = lattice(disc);
    } else if (Style == STROKE) {
        c = stroke(localPos);
    } else if (Style == BEAM) {
        c = beam(localPos);
    } else if (Style == STAR) {
        c = star(disc);
    } else if (Style == RING) {
        c = ring(disc);
    } else if (Style == ORBIT) {
        c = orbit(disc);
    } else if (Style == THREAD) {
        c = thread(localPos);
    } else if (Style == PETALS) {
        c = petals(disc);
    } else if (Style == SHELL) {
        c = shell(disc);
    } else if (Style == VORTEX) {
        c = vortex(disc);
    } else if (Style == BAND) {
        c = band(localPos);
    } else if (Style == STREAK) {
        c = streak(localPos);
    } else if (Style == ARC) {
        c = arc(localPos);
    } else if (Style == EYE) {
        c = eye(disc);
    } else if (Style == ECLIPSE) {
        c = eclipse(disc);
    } else if (Style == SHARD) {
        c = shard(disc);
    } else if (Style == WINGS) {
        c = wings(disc);
    } else if (Style == RIPPLE) {
        c = ripple(disc);
    } else if (Style == GEARS) {
        c = gears(disc);
    } else {
        c = flame(localPos);
    }
    float amount = max(c.a, 0.0) * Intensity;
    fragColor = vec4(min(c.rgb * amount, vec3(1.0)), max(min(amount, 1.0) * 0.45, hold));
}
