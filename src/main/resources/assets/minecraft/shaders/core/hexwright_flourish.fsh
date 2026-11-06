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
uniform vec4 Ring0;
uniform vec4 Ring1;
uniform vec4 Ring2;
uniform vec4 Ring3;
uniform vec4 Ring4;
uniform vec4 Ring5;

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
const int FIGURE = 21;
const int WEB = 22;
const int FROST = 23;
const int MOTH = 24;
const int CONSTELLATION = 25;
const int MAZE = 26;
const int CHAINS = 27;
const int BLADE = 28;
const int TESSERACT = 29;
const int SERPENT = 30;
const int RIFT = 31;
const int CLOCK = 32;
const int SINGULARITY = 33;
const int EFFIGY = 34;
const int AURORA = 35;
const int PRISM = 36;
const int ROSE_WINDOW = 37;
const int WILDFLOWER = 38;
const int DANDELION = 39;
const int MYCELIUM = 40;
const int BRIAR = 41;
const int WORLD_TREE = 42;

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


float petalDistance(float r, float a, float size, float k, float m) {
    float c = cos(m * a);
    float ac = max(abs(c), 1e-4);
    float f = size * pow(ac, k);
    float df = size * k * pow(ac, k - 1.0) * -m * sin(m * a) * sign(c);
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
    float outer = petalDistance(r, a, 0.95 * open1, 0.55, 3.0);
    float inner = petalDistance(r, a + PI / 6.0, 0.6 * open2, 0.55, 3.0);
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


const int SHAPE_CIRCLE = 0;
const int SHAPE_POLYGON = 1;
const int SHAPE_STAR = 2;
const int SHAPE_ROSE = 3;
const int SHAPE_RAYS = 4;
const int SHAPE_BEADS = 5;
const int SHAPE_LOOPS = 6;
const int SHAPE_TEETH = 7;
const int SHAPE_WAVE = 8;
const int SHAPE_GLYPHS = 9;
const int SHAPE_SPIRAL = 10;

const int ENTER_STAMP = 0;
const int ENTER_UNFOLD = 1;
const int ENTER_TRACE = 2;
const int ENTER_CASCADE = 3;
const int EXIT_FADE = 0;
const int EXIT_SHATTER = 1;
const int EXIT_COLLAPSE = 2;
const int EXIT_BURST = 3;

const int MAX_RINGS = 6;
const int MAX_REPEATS = 12;

vec4 ringAt(int i) {
    if (i == 0) return Ring0;
    if (i == 1) return Ring1;
    if (i == 2) return Ring2;
    if (i == 3) return Ring3;
    if (i == 4) return Ring4;
    return Ring5;
}

vec2 foldSector(vec2 q, float n) {
    float sector = TAU / n;
    float a = mod(atan(q.y, q.x) + sector * 0.5, sector) - sector * 0.5;
    return length(q) * vec2(cos(a), sin(a));
}

float starLines(vec2 q, float n, float k, float R, float w) {
    float reach = R * cos(PI * k / n);
    float lit = 0.0;
    for (int j = 0; j < MAX_REPEATS; j++) {
        if (float(j) >= n) {
            break;
        }
        float th = (2.0 * float(j) + k) * PI / n;
        lit = max(lit, line(abs(dot(q, vec2(cos(th), sin(th))) - reach), w));
    }
    return lit * (1.0 - smoothstep(R, R + w * 3.0, length(q)));
}

float figureCircle(vec2 q, float n, int form, float R, float w) {
    float r = length(q);
    float rim = line(abs(r - R), w);
    if (form == 1) {
        return rim + line(abs(r - R + w * 6.0), w * 0.7) * 0.8;
    }
    if (form == 2) {
        vec2 f = foldSector(q, n);
        float halfArc = PI / n * 0.6;
        vec2 end = R * vec2(cos(halfArc), sin(halfArc) * sign(f.y));
        return line(abs(atan(f.y, f.x)) < halfArc ? abs(r - R) : length(f - end), w);
    }
    if (form == 3) {
        return rim + 0.1 * (1.0 - smoothstep(R - w, R + w, r));
    }
    return rim;
}

float figurePolygon(vec2 q, float n, int form, float R, float w) {
    float lit = starLines(q, n, 1.0, R, w);
    vec2 f = foldSector(q, n);
    if (form == 1) {
        lit += line(abs(length(f - vec2(R, 0.0)) - max(R * 0.07, w * 3.0)), w * 0.8);
    } else if (form == 2) {
        lit += line(abs(length(q) - R * cos(PI / n)), w * 0.8) * 0.8;
    } else if (form == 3) {
        lit += line(segment(f, vec2(0.0), vec2(R, 0.0)), w * 0.7) * 0.7;
    }
    return lit;
}

float figureRose(vec2 q, float n, int form, float R, float w) {
    float k = form == 0 ? 0.35 : form == 1 ? 0.6 : form == 2 ? 1.2 : 2.6;
    float d = petalDistance(length(q), atan(q.y, q.x), R, k, n * 0.5);
    return line(abs(d), w) + (1.0 - smoothstep(-w, w, d)) * 0.08;
}

float figureRays(vec2 q, float n, int form, float R, float w) {
    vec2 f = foldSector(q, n);
    float from = R * (form == 0 ? 0.12 : form == 1 ? 0.5 : form == 2 ? 0.82 : 0.25);
    float d = segment(f, vec2(from, 0.0), vec2(R, 0.0));
    if (form == 3) {
        float along = clamp((f.x - from) / max(R - from, 1e-4), 0.0, 1.0);
        return line(d, mix(w * 3.0, w * 0.4, along));
    }
    return line(d, w);
}

float figureBeads(vec2 q, float n, int form, float R, float w) {
    vec2 f = foldSector(q, n);
    float size = max(R * (form == 0 ? 0.035 : form == 1 ? 0.06 : form == 2 ? 0.1 : 0.08), w * 2.0);
    float d = length(f - vec2(R, 0.0));
    if (form == 3) {
        return line(abs(d - size), w);
    }
    float aa = fwidth(d) + 1e-5;
    return (1.0 - smoothstep(size - aa, size + aa, d)) * 0.9 + exp(-d * d / (size * size * 4.0)) * 0.5;
}

float figureLoops(vec2 q, float n, int form, float R, float w) {
    float at = form == 0 ? 0.5 : form == 1 ? 0.7 : form == 2 ? 0.85 : 0.35;
    float size = (1.0 - at) * R;
    float lit = 0.0;
    for (int j = 0; j < MAX_REPEATS; j++) {
        if (float(j) >= n) {
            break;
        }
        float th = float(j) * TAU / n;
        lit = max(lit, line(abs(length(q - at * R * vec2(cos(th), sin(th))) - size), w));
    }
    return lit;
}

float figureTeeth(vec2 q, float n, int form, float R, float w) {
    float r = length(q);
    float teeth = n * 2.0;
    float pitch = TAU * R / teeth;
    float depth = R * 0.12;
    vec2 c = vec2((fract(atan(q.y, q.x) / TAU * teeth + 0.5) - 0.5) * pitch, r - R);
    vec2 lo = vec2(-0.5 * pitch, -depth);
    vec2 hi = vec2(0.5 * pitch, -depth);
    float d;
    if (form == 0) {
        vec2 a = vec2(-0.25 * pitch, 0.0);
        vec2 b = vec2(0.25 * pitch, 0.0);
        d = min(min(segment(c, lo, vec2(a.x, -depth)), segment(c, vec2(a.x, -depth), a)),
            min(min(segment(c, a, b), segment(c, b, vec2(b.x, -depth))), segment(c, vec2(b.x, -depth), hi)));
    } else if (form == 1) {
        d = min(min(segment(c, lo, vec2(hi.x, 0.0)), segment(c, vec2(hi.x, 0.0), hi)), segment(c, vec2(lo.x, 0.0), lo));
    } else if (form == 2) {
        d = min(segment(c, lo, vec2(0.0)), segment(c, vec2(0.0), hi));
    } else {
        float x = c.x / pitch * TAU;
        float slope = -depth * 0.5 * sin(x) * TAU / pitch;
        d = abs(c.y + depth * 0.5 - depth * 0.5 * cos(x)) / sqrt(1.0 + slope * slope);
    }
    return line(d, w);
}

float waveLine(float r, float a, float n, float R, float amplitude, float w) {
    float rim = R - amplitude + amplitude * cos(n * a);
    float slope = -amplitude * n * sin(n * a);
    float d = abs(r - rim) / sqrt(1.0 + min(slope * slope / max(r * r, 1e-4), 9.0));
    return line(d, w) * smoothstep(R * 0.3, R * 0.5, r);
}

float figureWave(vec2 q, float n, int form, float R, float w) {
    float r = length(q);
    float a = atan(q.y, q.x);
    float amplitude = R * (form == 0 || form == 2 ? 0.06 : 0.13);
    float lit = waveLine(r, a, n, R, amplitude, w);
    if (form >= 2) {
        lit = max(lit, waveLine(r, a + PI / n, n, R, amplitude, w));
    }
    return lit;
}

float figureGlyphs(vec2 q, float n, int form, float R, float w) {
    float r = length(q);
    float cells = n * (form == 1 ? 5.0 : 3.0);
    float ca = atan(q.y, q.x) / TAU * cells;
    float cell = mod(floor(ca), cells);
    float pitch = TAU * R / cells;
    vec2 c = vec2((fract(ca) - 0.5) * pitch, r - R);
    if (form == 0) {
        float kind = hash11(cell);
        float reach = R * (kind > 0.66 ? 0.05 : 0.025);
        return line(length(vec2(c.x, max(abs(c.y) - reach, 0.0))), w) * step(0.2, kind);
    }
    if (form == 1) {
        float major = step(mod(cell, 5.0), 0.5);
        return line(segment(c, vec2(0.0, -R * mix(0.03, 0.08, major)), vec2(0.0, 0.0)), w * mix(0.8, 1.3, major));
    }
    float s = min(pitch * 0.28, R * 0.06);
    float lit = 0.0;
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        vec2 from = floor(vec2(hash11(cell * 7.0 + fk * 1.3), hash11(cell * 13.0 + fk * 2.9 + 5.0)) * 3.0) - 1.0;
        float pick = floor(hash11(cell * 3.1 + fk * 4.7 + 9.0) * 4.0);
        vec2 hop = pick < 1.0 ? vec2(1.0, 0.0) : pick < 2.0 ? vec2(0.0, 1.0) : pick < 3.0 ? vec2(1.0, 1.0) : vec2(1.0, -1.0);
        vec2 to = clamp(from + hop, -1.0, 1.0);
        lit += line(segment(c, from * s, to * s), w) * 0.9;
    }
    if (form == 3) {
        lit += line(abs(c.y - s * 1.8), w * 0.8) + line(abs(c.y + s * 1.8), w * 0.8);
    }
    return lit;
}

float figureSpiral(vec2 q, float n, int form, float R, float w) {
    float r = max(length(q), 1e-4);
    float twist = form == 0 ? 0.6 : form == 1 ? 1.2 : form == 2 ? 2.0 : 3.2;
    float s = (atan(q.y, q.x) + twist * log(r / R)) * n / TAU;
    float gap = abs(fract(s + 0.5) - 0.5);
    float across = gap * TAU * r / (n * sqrt(1.0 + twist * twist));
    float within = (1.0 - smoothstep(R, R + w * 2.0, r)) * smoothstep(0.0, R * 0.15, r);
    return line(across, w) * within;
}

float figureGrid(vec2 q, float n, int form, float R, float w) {
    float pitch = 2.0 * R / n;
    vec2 g = q / pitch;
    float lit;
    if (form == 0) {
        vec2 rel = abs(hexTile(g).xy);
        lit = line((0.5 - max(dot(rel, vec2(0.5, 0.8660254)), rel.x)) * pitch, w);
    } else if (form == 1) {
        float d = 1e9;
        for (int k = 0; k < 3; k++) {
            float th = float(k) * PI / 3.0;
            d = min(d, abs(fract(dot(g, vec2(cos(th), sin(th))) + 0.5) - 0.5));
        }
        lit = line(d * pitch, w);
    } else if (form == 2) {
        vec2 off = abs(fract(g + 0.5) - 0.5);
        lit = line(min(off.x, off.y) * pitch, w);
    } else {
        float d = length(hexTile(g).xy) * pitch;
        float size = max(pitch * 0.12, w * 2.0);
        float aa = fwidth(d) + 1e-5;
        lit = 1.0 - smoothstep(size - aa, size + aa, d);
    }
    return lit * (1.0 - smoothstep(R - w, R + w, length(q)));
}

float ringShape(vec2 q, int shape, float n, int form, float R, float w) {
    if (shape == SHAPE_CIRCLE) {
        return figureCircle(q, n, form, R, w);
    } else if (shape == SHAPE_POLYGON) {
        return figurePolygon(q, n, form, R, w);
    } else if (shape == SHAPE_STAR) {
        return starLines(q, n, 2.0 + float(form), R, w);
    } else if (shape == SHAPE_ROSE) {
        return figureRose(q, n, form, R, w);
    } else if (shape == SHAPE_RAYS) {
        return figureRays(q, n, form, R, w);
    } else if (shape == SHAPE_BEADS) {
        return figureBeads(q, n, form, R, w);
    } else if (shape == SHAPE_LOOPS) {
        return figureLoops(q, n, form, R, w);
    } else if (shape == SHAPE_TEETH) {
        return figureTeeth(q, n, form, R, w);
    } else if (shape == SHAPE_WAVE) {
        return figureWave(q, n, form, R, w);
    } else if (shape == SHAPE_GLYPHS) {
        return figureGlyphs(q, n, form, R, w);
    } else if (shape == SHAPE_SPIRAL) {
        return figureSpiral(q, n, form, R, w);
    }
    return figureGrid(q, n, form, R, w);
}

vec4 figure(vec2 p) {
    float t = Progress;
    int rings = int(Segment.x + 0.5);
    int entrance = int(Segment.y + 0.5);
    int leave = int(Segment.z + 0.5);
    float enter = clamp(t / 0.25, 0.0, 1.0);
    float leaving = clamp((t - 0.62) / 0.38, 0.0, 1.0);
    float pr = length(p);
    float around = fract(0.25 - atan(p.y, p.x) / TAU);
    float total = 0.0;
    vec3 colour = vec3(0.0);
    for (int i = 0; i < MAX_RINGS; i++) {
        if (i >= rings) {
            break;
        }
        vec4 ring = ringAt(i);
        int code = int(ring.x + 0.5);
        int shape = code & 15;
        float n = float((code >> 4) & 15);
        int form = (code >> 8) & 3;
        float turn = float((code >> 10) & 3);
        float hue = float((code >> 12) & 3);
        float R = ring.y;
        float w = ring.z / Extent.x;
        float order = rings > 1 ? float(i) / float(rings - 1) : 0.0;
        float along = i % 2 == 0 ? around : 1.0 - around;

        float grow = 1.0;
        float shown = 1.0;
        if (entrance == ENTER_STAMP) {
            grow = max(ease(enter), 0.02);
            shown = smoothstep(0.0, 0.03, t);
        } else if (entrance == ENTER_UNFOLD) {
            grow = max(easeBack(enter), 0.02);
            shown = smoothstep(0.0, 0.03, t);
        } else if (entrance == ENTER_TRACE) {
            float drawn = ease(clamp((t - order * 0.08) / 0.3, 0.0, 1.0)) * 1.02;
            shown = 1.0 - smoothstep(drawn - 0.02, drawn, along);
        } else if (entrance == ENTER_CASCADE) {
            float local = clamp((t - order * 0.2) / 0.14, 0.0, 1.0);
            grow = max(easeBack(local), 0.02);
            shown = smoothstep(0.0, 0.05, local);
        } else {
            R = mix(1.0, R, ease(enter));
            shown = smoothstep(0.0, 0.12, t);
        }

        if (leave == EXIT_FADE) {
            shown *= 1.0 - smoothstep(0.0, 1.0, leaving);
        } else if (leave == EXIT_SHATTER) {
            float piece = hash11(floor(around * 24.0) + float(i) * 37.0);
            shown *= 1.0 - smoothstep(piece - 0.08, piece + 0.08, leaving * 1.15);
        } else if (leave == EXIT_COLLAPSE) {
            grow *= max(1.0 - leaving * leaving, 0.02);
            shown *= 1.0 - smoothstep(0.85, 1.0, leaving);
        } else if (leave == EXIT_BURST) {
            R = mix(R, 1.0, ease(leaving));
            w *= 1.0 - 0.6 * leaving;
            shown *= pow(1.0 - leaving, 1.5);
        } else {
            shown *= smoothstep(leaving * 1.02 - 0.02, leaving * 1.02, along);
        }

        vec2 q = rot(PI * 0.5 + turn * 0.25 * TAU / n + ring.w * Age) * (p / grow);
        float lit = ringShape(q, shape, n, form, R, w) * shown;
        colour += palette(hue * 0.25 + length(q) * 0.3 + Age * 0.05) * lit;
        total += lit;
    }

    float accent = 0.0;
    if (entrance == ENTER_STAMP) {
        accent += line(abs(pr - ease(t / 0.2)), Ring0.z / Extent.x * 2.0) * (1.0 - smoothstep(0.1, 0.25, t)) * 1.5;
    }
    if (leave == EXIT_COLLAPSE) {
        accent += exp(-pr * pr / 0.012) * smoothstep(0.8, 0.92, leaving) * (1.0 - smoothstep(0.92, 1.0, leaving)) * 3.0;
    }
    colour += palette(Age * 0.1) * accent;
    total += accent;

    vec3 col = colour / max(total, 1e-4);
    return vec4(hot(col, min(total, 2.0) * 0.12), total * discEdge(pr));
}

vec4 web(vec2 p) {
    float t = Progress;
    float r = length(p);
    float a = atan(p.y, p.x);
    const float SPOKES = 12.0;
    float sector = TAU / SPOKES;
    float halfSector = sector * 0.5;
    float k = mod(floor((a + PI) / sector), SPOKES);
    float off = a + PI - (k + 0.5) * sector;
    float w = 0.012 / Extent.x;
    float fade = 1.0 - smoothstep(0.7, 1.0, t);

    float reach = ease(t / 0.18);
    float spokes = line(r * sin(halfSector - abs(off)), w * 1.2) * (1.0 - smoothstep(reach - 0.03, reach, r));

    float hub = 0.16;
    float pitch = 0.075;
    float rho = r * cos(off) / cos(halfSector);
    float sag = (1.0 - pow(off / halfSector, 2.0)) * 0.01;
    float s = (rho + sag - hub) / pitch - k / SPOKES;
    float rung = floor(s + 0.5);
    float acrossRung = abs(s - rung) * pitch * cos(halfSector);
    float outer = (0.9 - hub) / pitch;
    float front = (outer + 0.3) * (1.0 - ease((t - 0.14) / 0.42));
    float alongSpiral = rung + (k + off / sector + 0.5) / SPOKES;
    float laid = step(alongSpiral, outer) * smoothstep(front - 0.3, front, alongSpiral);
    float spiral = line(acrossRung, w) * step(0.5, s) * laid;
    float frame = line(abs(rho - 0.94) * cos(halfSector), w * 1.3) * smoothstep(0.1, 0.2, t);

    float cell = rung * SPOKES + k;
    float dew = 0.0;
    if (hashCell(vec2(cell, 7.0)) > 0.6 && rung >= 1.0) {
        float dewAngle = -PI + (k + 0.2 + 0.6 * hashCell(vec2(cell, 3.0))) * sector;
        float dewRho = hub + (rung + k / SPOKES) * pitch;
        float dewR = dewRho * cos(halfSector) / cos(dewAngle + PI - (k + 0.5) * sector);
        vec2 drop = dewR * vec2(cos(dewAngle), sin(dewAngle));
        float glint = 0.5 + 0.5 * sin(Age * 3.0 - r * 9.0 + hashCell(vec2(cell, 5.0)) * 6.0);
        dew = exp(-dot(p - drop, p - drop) / (w * w * 5.0)) * (0.6 + 1.2 * glint) * laid;
    }
    float hubLines = (line(abs(r - hub * 0.5), w) + line(abs(r - hub * 0.8), w)) * smoothstep(0.0, 0.1, t);

    float lit = spokes + spiral * 0.9 + frame * 0.8 + hubLines * 0.7 + dew;
    vec3 col = palette(r * 0.6 + Age * 0.05);
    return vec4(hot(col, dew * 0.5 + 0.15), lit * fade * discEdge(r));
}

vec4 frost(vec2 p) {
    float t = Progress;
    float r = length(p);
    float a = mod(atan(p.y, p.x), PI / 3.0);
    a = min(a, PI / 3.0 - a);
    vec2 q = r * vec2(cos(a), sin(a));
    float melt = smoothstep(0.6, 1.0, t);
    float w = 0.016 / Extent.x * (1.0 - 0.5 * melt);
    float tip = 0.95 * ease(t / 0.45);
    float lit = line(segment(q, vec2(0.0), vec2(tip, 0.0)), w * 1.3);
    vec2 slant = vec2(0.5, 0.8660254);
    for (int i = 0; i < 7; i++) {
        float x = 0.14 + float(i) * 0.115;
        float grown = tip - x;
        if (grown <= 0.0) {
            break;
        }
        float most = (0.1 + 0.18 * sin(PI * x / 0.95)) * (0.7 + 0.6 * hash11(float(i) * 7.3));
        float len = min(min(most, grown * 0.8), x * 0.9);
        vec2 root = vec2(x, 0.0);
        lit += line(segment(q, root, root + slant * len), w);
        if (len > 0.07) {
            vec2 twig = root + slant * len * 0.55;
            lit += line(segment(q, twig, twig + vec2(len * 0.35, 0.0)), w * 0.8) * 0.8;
        }
    }
    float plate = line(segment(q, vec2(0.1, 0.0), vec2(0.075, 0.0433)), w) * smoothstep(0.0, 0.06, t);
    float within = 1.0 - smoothstep(tip * 0.9, tip * 1.02, r);
    float haze = noise(p * Extent.x * 3.0) * 0.06 * within;
    vec2 cellAt = floor(p * 14.0);
    vec2 sparkAt = (cellAt + 0.2 + 0.6 * vec2(hashCell(cellAt), hashCell(cellAt + 13.0))) / 14.0;
    float glitter = exp(-dot(p - sparkAt, p - sparkAt) / 0.00004) * step(0.6, hashCell(cellAt + 3.0))
        * (0.5 + 0.5 * sin(Age * 6.0 + hashCell(cellAt + 8.0) * TAU)) * 1.5 * within;
    vec3 col = palette(r * 0.8 + Age * 0.05);
    return vec4(hot(col, 0.3 + glitter * 0.3), (lit + plate + haze + glitter) * (1.0 - melt) * discEdge(r));
}


float ellipse(vec2 p, vec2 centre, vec2 axes, float angle) {
    vec2 local = rot(angle) * (p - centre);
    return (length(local / axes) - 1.0) * min(axes.x, axes.y);
}

vec4 moth(vec2 p) {
    float t = Progress;
    vec2 q = rot(Segment.x) * p;
    float beat = 0.5 + 0.5 * cos(Age * 16.0 + Segment.y * TAU);
    float spread = mix(0.35, 1.0, beat);
    vec2 s = vec2(abs(q.x) / spread, q.y);
    float w = max(0.02, 0.008 / Extent.x);
    float fore = ellipse(s, vec2(0.36, 0.16), vec2(0.4, 0.22), 0.5);
    float hind = ellipse(s, vec2(0.24, -0.2), vec2(0.25, 0.17), -0.55);
    float wings = min(fore, hind);
    float body = ellipse(q, vec2(0.0, 0.0), vec2(0.06, 0.3), 0.0);
    float inWing = 1.0 - smoothstep(-w, w, wings);
    float lit = line(abs(fore) * spread, w) + line(abs(hind) * spread, w) * 0.9 + line(abs(body), w) * 0.8;
    float fill = inWing * 0.06 * spread + (1.0 - smoothstep(-w, w, body)) * 0.25;
    vec2 spot = s - vec2(0.44, 0.2);
    float eyespot = (line(abs(length(spot) - 0.07) * spread, w * 0.8) + exp(-dot(spot, spot) / 0.0012)) * inWing;
    vec2 side = vec2(abs(q.x), q.y);
    float antennae = line(segment(side, vec2(0.02, 0.28), vec2(0.1, 0.48)), w * 0.7)
        + line(segment(side, vec2(0.1, 0.48), vec2(0.19, 0.55)), w * 0.7);
    float shown = smoothstep(0.0, 0.08, t) * (1.0 - smoothstep(0.75, 1.0, t));
    vec3 col = palette(length(s) * 0.7 + Segment.y + Age * 0.05);
    return vec4(hot(col, eyespot * 0.4 + 0.15), (lit + fill + eyespot + antennae * 0.7) * shown * discEdge(length(p)));
}

const int STARS = 7;

float starPoint(vec2 d, float size) {
    float core = exp(-dot(d, d) / (size * size));
    float flare = exp(-abs(d.x) / (size * 3.0)) * exp(-d.y * d.y / (size * size * 0.15))
        + exp(-abs(d.y) / (size * 3.0)) * exp(-d.x * d.x / (size * size * 0.15));
    return core * 1.6 + flare * 0.5;
}

vec4 constellation(vec2 p) {
    float t = Progress;
    float r = length(p);
    float w = 0.014 / Extent.x;
    float fade = 1.0 - smoothstep(0.75, 1.0, t);
    float lit = 0.0;
    float flash = 0.0;
    vec2 at = (vec2(hash11(1.0), hash11(2.0)) - 0.5) * 0.7;
    float heading = hash11(3.0) * TAU;
    vec2 spurFrom = at;
    for (int i = 0; i < STARS; i++) {
        float fi = float(i);
        float lit0 = 0.05 + fi * 0.06;
        vec2 next = at;
        if (i > 0) {
            heading += (hash11(fi * 5.1 + 4.0) - 0.5) * 2.0;
            float stride = 0.26 + 0.14 * hash11(fi * 3.7 + 9.0);
            next = at + vec2(cos(heading), sin(heading)) * stride;
            if (length(next) > 0.72) {
                heading = atan(-at.y, -at.x) + (hash11(fi * 2.3 + 1.0) - 0.5) * 1.2;
                next = at + vec2(cos(heading), sin(heading)) * stride;
            }
            float drawn = clamp((t - lit0 + 0.06) / 0.06, 0.0, 1.0);
            lit += line(segment(p, at, mix(at, next, drawn)), w) * step(0.0, t - lit0 + 0.06) * 0.7;
        }
        float since = t - lit0;
        float twinkle = 0.75 + 0.25 * sin(Age * 5.0 + fi * 2.1);
        float size = 0.022 + 0.01 * hash11(fi + 20.0);
        lit += starPoint(p - next, size) * step(0.0, since) * twinkle;
        flash += exp(-dot(p - next, p - next) / 0.01) * exp(-max(since, 0.0) * 18.0) * step(0.0, since) * 1.5;
        if (i == 2) {
            spurFrom = next;
        }
        at = next;
    }
    float spurAt = 0.05 + float(STARS) * 0.06;
    float spurHeading = heading + PI * 0.5 + hash11(30.0);
    vec2 spur = spurFrom + vec2(cos(spurHeading), sin(spurHeading)) * 0.24;
    spur *= min(1.0, 0.72 / max(length(spur), 1e-4));
    float spurDrawn = clamp((t - spurAt + 0.06) / 0.06, 0.0, 1.0);
    lit += line(segment(p, spurFrom, mix(spurFrom, spur, spurDrawn)), w) * step(0.0, t - spurAt + 0.06) * 0.7;
    lit += starPoint(p - spur, 0.02) * step(spurAt, t);
    flash += exp(-dot(p - spur, p - spur) / 0.01) * exp(-max(t - spurAt, 0.0) * 18.0) * step(spurAt, t) * 1.5;

    float a = atan(p.y, p.x);
    float traced = 1.0 - smoothstep(ease(t / 0.4) - 0.02, ease(t / 0.4), fract(0.25 - a / TAU));
    vec2 tick = foldSector(p, 36.0);
    vec2 quarter = foldSector(p, 4.0);
    float chart = line(abs(r - 0.92), w) + line(segment(tick, vec2(0.86, 0.0), vec2(0.92, 0.0)), w * 0.8) * 0.7
        + line(segment(quarter, vec2(0.8, 0.0), vec2(0.92, 0.0)), w);
    vec2 cellAt = floor(p * 9.0);
    vec2 dust = (cellAt + 0.2 + 0.6 * vec2(hashCell(cellAt), hashCell(cellAt + 17.0))) / 9.0;
    float faint = exp(-dot(p - dust, p - dust) / 0.00006) * step(0.55, hashCell(cellAt + 5.0))
        * (0.5 + 0.5 * sin(Age * 4.0 + hashCell(cellAt + 9.0) * TAU)) * 0.8 * step(r, 0.88);
    float total = lit + flash + (chart * traced * 0.35 + faint) * smoothstep(0.0, 0.15, t);
    vec3 col = palette(r * 0.5 + Age * 0.05);
    return vec4(hot(col, flash * 0.5 + 0.35), total * fade * discEdge(r));
}

const int MAZE_WALLS = 6;
const float MAZE_INNER = 0.18;
const float MAZE_STEP = 0.15;

float mazeWall(int i) {
    return MAZE_INNER + float(i) * MAZE_STEP;
}

float mazeGate(int i) {
    return hash11(float(i) * 13.7 + 2.0) * TAU;
}

vec2 polar(float radius, float angle) {
    return radius * vec2(cos(angle), sin(angle));
}

float turnBetween(float start, float end) {
    return mod(end - start + PI, TAU) - PI;
}

float arcDistance(vec2 p, float rc, float start, float dir, float sweep) {
    float phi = mod((atan(p.y, p.x) - start) * dir, TAU);
    if (phi <= sweep) {
        return abs(length(p) - rc);
    }
    return min(length(p - polar(rc, start)), length(p - polar(rc, start + dir * sweep)));
}

vec4 maze(vec2 p) {
    float t = Progress;
    float r = length(p);
    float a = atan(p.y, p.x);
    float w = 0.025 / Extent.x;
    float fade = 1.0 - smoothstep(0.78, 1.0, t);
    float raised = ease(t / 0.12);

    float walls = 0.0;
    float gateHalf = MAZE_STEP * 0.4;
    for (int i = 0; i < MAZE_WALLS; i++) {
        float R = mazeWall(i);
        float g = mazeGate(i);
        float gateArc = gateHalf / R;
        walls = max(walls, line(arcDistance(p, R, g + gateArc, 1.0, TAU - gateArc * 2.0), w));
    }

    float total = 1.02 - (mazeWall(MAZE_WALLS - 1) - MAZE_STEP * 0.5) + MAZE_INNER - MAZE_STEP * 0.5;
    for (int c = MAZE_WALLS - 2; c >= 0; c--) {
        float rc = mazeWall(c) + MAZE_STEP * 0.5;
        total += abs(turnBetween(mazeGate(c + 1), mazeGate(c))) * rc + (c > 0 ? MAZE_STEP : 0.0);
    }
    float s = total * clamp((t - 0.1) / 0.58, 0.0, 1.0);

    float path = 0.0;
    float deadEnds = 0.0;
    vec2 head = vec2(0.0, 1.02);
    float from = 1.02;
    float done = 0.0;
    for (int c = MAZE_WALLS - 2; c >= -1; c--) {
        float g = mazeGate(c + 1);
        float rc = c >= 0 ? mazeWall(c) + MAZE_STEP * 0.5 : 0.0;
        float inward = from - rc;
        float shown = clamp(s - done, 0.0, inward);
        if (shown > 0.0) {
            vec2 tip = polar(from - shown, g);
            path = max(path, line(segment(p, polar(from, g), tip), w * 1.3));
            head = tip;
        }
        done += inward;
        if (c < 0) {
            break;
        }
        float turn = turnBetween(g, mazeGate(c));
        float dir = turn < 0.0 ? -1.0 : 1.0;
        float sweep = abs(turn);
        float travelled = clamp(s - done, 0.0, sweep * rc);
        if (travelled > 0.0) {
            path = max(path, line(arcDistance(p, rc, g, dir, travelled / rc), w * 1.3));
            head = polar(rc, g + dir * travelled / rc);
        }
        done += sweep * rc;
        for (int k = 1; k <= 2; k++) {
            float at = g + dir * (sweep + (TAU - sweep) * float(k) / 3.0);
            deadEnds = max(deadEnds, line(segment(p, polar(mazeWall(c), at), polar(mazeWall(c + 1), at)), w));
        }
        from = rc;
    }
    float running = step(0.1, t) * (1.0 - step(total - 1e-3, s));
    float comet = exp(-dot(p - head, p - head) / (w * w * 30.0)) * 2.0 * running;
    float arrived = smoothstep(0.66, 0.7, t);
    float heart = (line(abs(r - 0.06), w) + exp(-r * r / 0.004) * 1.5) * arrived
        + exp(-r * r / 0.03) * arrived * (1.0 - smoothstep(0.7, 0.85, t)) * 2.0;
    float structure = (walls + deadEnds) * 0.6 * raised;
    vec3 col = palette(r * 0.5 + Age * 0.05);
    return vec4(hot(col, comet * 0.4 + heart * 0.2 + 0.1),
        (structure + path * 1.2 + comet + heart) * fade * discEdge(r));
}

vec4 chains(vec2 uv) {
    float t = Progress;
    float circumference = Extent.x;
    float tight = ease((t - 0.25) / 0.35);
    float amplitude = Extent.y * mix(0.55, 0.2, tight);
    float snapAt = 0.66;
    float broken = clamp((t - snapAt) / (1.0 - snapAt), 0.0, 1.0);
    float snap = exp(-pow((t - snapAt) / 0.03, 2.0));
    float links = max(floor(circumference / 0.2 * 0.5) * 2.0, 8.0);
    float pitch = circumference / links;
    float wound = ease(t / 0.28);
    float w = 0.016;
    float lit = 0.0;
    float hue = 0.0;
    for (int c = 0; c < 2; c++) {
        float phase = float(c) * PI;
        float nearest = floor(uv.x / pitch);
        for (int k = -1; k <= 1; k++) {
            float i = nearest + float(k);
            float id = mod(i, links);
            float x = (i + 0.5) * pitch;
            float wave = TAU * 2.0 * x / circumference + phase;
            vec2 rel = uv - vec2(x, amplitude * sin(wave));
            float h = hashCell(vec2(id, float(c)));
            rel.y += broken * broken * (0.8 + 0.8 * h);
            rel.x += broken * (h - 0.5) * 0.4;
            rel = rot(atan(amplitude * TAU * 2.0 / circumference * cos(wave)) + broken * (h - 0.5) * 5.0) * rel;
            float halfLength = pitch * 0.62;
            float d;
            float width = w;
            if (mod(id, 2.0) < 0.5) {
                float halfWidth = pitch * 0.3;
                d = abs(length(vec2(max(abs(rel.x) - (halfLength - halfWidth), 0.0), rel.y)) - halfWidth);
            } else {
                d = segment(rel, vec2(-halfLength * 0.9, 0.0), vec2(halfLength * 0.9, 0.0));
                width = w * 1.7;
            }
            float order = fract(id / links + float(c) * 0.5);
            float link = line(d, width) * step(order, wound) * (1.0 - broken) * (1.0 + snap);
            if (link > lit) {
                lit = link;
                hue = id / links + float(c) * 0.25;
            }
        }
    }
    vec3 col = palette(hue + Age * 0.05);
    return vec4(hot(col, 0.2 + snap * 0.4), lit);
}

vec4 blade(vec2 uv) {
    float len = Extent.x;
    float size = Extent.y;
    float local = (Progress - Segment.x) / max(Segment.y, 1e-4);
    if (local <= 0.0) {
        return vec4(0.0);
    }
    float tip = pow(min(local, 1.0), 1.6) * (len + size * 0.12);
    float u = (tip - uv.x) / size;
    float v = uv.y / size;
    float w = 0.014 / size;
    float halfWidth = 0.05 * min(max(u, 0.0) / 0.14, 1.0);
    float edge = (abs(v) - halfWidth) / (u < 0.14 ? 1.06 : 1.0);
    float bladeShape = u < 0.0 ? length(vec2(u, v)) : u > 0.68 ? 1.0 : abs(edge);
    float inside = step(0.0, u) * step(u, 0.68) * (1.0 - smoothstep(-w, w, edge));
    vec2 guardBox = abs(vec2(u - 0.7, v)) - vec2(0.02, 0.15);
    float guard = abs(length(max(guardBox, 0.0)) + min(max(guardBox.x, guardBox.y), 0.0));
    float grip = segment(vec2(u, v), vec2(0.72, 0.0), vec2(0.9, 0.0));
    float pommel = abs(length(vec2(u - 0.93, v)) - 0.03);
    float fuller = line(abs(v), w * 0.6) * step(0.12, u) * step(u, 0.6);
    float lit = line(bladeShape, w) + line(guard, w) + line(grip, w * 1.6) * 0.8 + line(pommel, w) + fuller * 0.5
        + inside * 0.12;

    float flying = 1.0 - smoothstep(0.92, 1.0, local);
    float wake = step(0.9, u) * exp(-(u - 0.9) / 1.5) * exp(-v * v / 0.0012) * flying * 0.8;
    float landed = max((local - 1.0) * Segment.y, 0.0);
    float glintAt = 0.68 * (1.0 - clamp(landed / 0.12, 0.0, 1.0));
    float glint = step(0.0, local - 1.0) * exp(-pow((u - glintAt) / 0.05, 2.0)) * inside * 3.0
        * (1.0 - smoothstep(0.1, 0.16, landed));
    vec2 toPoint = vec2(uv.x - len, uv.y) / size;
    float impact = step(1.0, local) * exp(-dot(toPoint, toPoint) / 0.01) * exp(-landed * 25.0) * 3.0;

    float shown = smoothstep(0.0, 0.15, local) * (1.0 - smoothstep(0.72, 1.0, Progress));
    vec3 col = palette(u * 0.5 + Segment.x * 2.0 + Age * 0.05);
    return vec4(hot(col, glint * 0.5 + impact * 0.5 + 0.3), ((lit + glint) * shown + wake + impact));
}

vec4 tesseract(vec2 p) {
    float t = Progress;
    float grow = easeBack(t / 0.22);
    float collapse = smoothstep(0.75, 0.97, t);
    float size = 1.25 * grow * (1.0 - 0.95 * collapse);
    float xw = Age * 0.9 + Seed;
    float yz = Age * 0.55;
    float xz = Age * 0.3 + 0.6;
    vec2 corner[16];
    float nearness[16];
    for (int i = 0; i < 16; i++) {
        vec4 v = vec4(float(i & 1), float((i >> 1) & 1), float((i >> 2) & 1), float((i >> 3) & 1)) * 2.0 - 1.0;
        v.xw = rot(xw) * v.xw;
        v.yz = rot(yz) * v.yz;
        vec3 c = v.xyz / (3.0 - v.w);
        c.xz = rot(xz) * c.xz;
        corner[i] = c.xy / (2.2 - c.z) * size;
        nearness[i] = c.z;
    }
    float w = 0.02 / Extent.x;
    float lit = 0.0;
    float hue = 0.0;
    for (int i = 0; i < 16; i++) {
        for (int b = 0; b < 4; b++) {
            int j = i ^ (1 << b);
            if (j < i) {
                continue;
            }
            float depth = 0.55 + 0.45 * smoothstep(-0.6, 0.6, (nearness[i] + nearness[j]) * 0.5);
            float edge = line(segment(p, corner[i], corner[j]), w) * depth;
            if (edge > lit) {
                lit = edge;
                hue = float(b) * 0.25;
            }
        }
    }
    float corners = 0.0;
    for (int i = 0; i < 16; i++) {
        vec2 d = p - corner[i];
        corners += exp(-dot(d, d) / (w * w * 8.0)) * 0.6;
    }
    float r = length(p);
    float flash = exp(-r * r / 0.02) * smoothstep(0.85, 0.95, t) * (1.0 - smoothstep(0.95, 1.0, t)) * 3.0;
    float fade = 1.0 - smoothstep(0.92, 1.0, t);
    vec3 col = palette(hue + Age * 0.05);
    return vec4(hot(col, 0.2 + flash * 0.5), (lit + corners) * fade * discEdge(r) + flash);
}

vec4 serpent(vec2 p) {
    float t = Progress;
    float r = length(p);
    float R = 0.72;
    float biteAt = 0.5;
    float run = smoothstep(0.0, biteAt, t);
    float body = mix(0.4, TAU - 0.2, run);
    float turned = max(t - biteAt, 0.0) * 3.0;
    float tail = PI * 0.5 + Seed - turned;
    float phi = mod(atan(p.y, p.x) - tail, TAU);
    float w = 0.022 / Extent.x;

    float thick = 0.07 * (0.2 + 0.8 * smoothstep(0.0, 1.8, phi));
    float alongBody = step(phi, body);
    float across = abs(r - R);
    float outline = line(abs(across - thick), w) * alongBody;
    float inBody = (1.0 - smoothstep(thick - w, thick + w, across)) * alongBody;
    float scaleAt = phi * R / 0.055 + across / max(thick, 1e-3) * 0.6;
    float scales = line(abs(fract(scaleAt) - 0.5) * 0.055, w * 0.6) * inBody * 0.45;
    float spine = line(across, w * 0.6) * inBody * 0.3;
    float glint = exp(-pow((phi - fract(turned * 0.25) * TAU) / 0.35, 2.0)) * inBody * step(biteAt, t) * 1.2;

    float headAngle = tail + body;
    vec2 forward = vec2(-sin(headAngle), cos(headAngle));
    vec2 rel = p - polar(R, headAngle);
    vec2 h = vec2(dot(rel, forward), dot(rel, polar(1.0, headAngle)));
    float snout = clamp((h.x - 0.04) / 0.13, 0.0, 1.0);
    float head = (length(vec2((h.x - 0.04) / 0.13, h.y / (0.1 * (1.0 - 0.35 * snout)))) - 1.0) * 0.1;
    float gape = smoothstep(biteAt - 0.22, biteAt - 0.08, t) * (1.0 - smoothstep(biteAt - 0.02, biteAt, t)) * 0.8;
    float jaw = abs(h.y) - (h.x - 0.04) * gape;
    float mouth = step(0.04, h.x) * (1.0 - smoothstep(-w, w, jaw)) * step(0.001, gape);
    float inHead = 1.0 - smoothstep(-w, w, head);
    float headLit = line(abs(head), w) * (1.0 - mouth) + inHead * (1.0 - mouth) * 0.12
        + line(abs(jaw), w) * step(0.04, h.x) * inHead * step(0.001, gape);
    vec2 eye = vec2(h.x - 0.08, abs(h.y) - 0.05);
    float eyes = exp(-dot(eye, eye) / 0.0004) * 1.5;
    float bite = exp(-dot(rel, rel) / 0.006) * exp(-pow((t - biteAt) / 0.03, 2.0)) * 3.0;

    float bodyLit = (outline + scales + spine + inBody * 0.1 + glint) * (1.0 - inHead);
    float fade = 1.0 - smoothstep(0.78, 1.0, t);
    float shown = smoothstep(0.0, 0.05, t);
    vec3 col = palette(phi / TAU + Age * 0.05);
    return vec4(hot(col, glint * 0.4 + eyes * 0.3 + bite * 0.5 + 0.1),
        ((bodyLit + headLit + eyes) * fade * shown + bite) * discEdge(r));
}

vec4 rift(vec2 p) {
    float t = Progress;
    vec2 q = rot((hash11(4.0) - 0.5) * 0.6) * p;
    float open = max(easeBack(t / 0.25), 0.0) * (1.0 - smoothstep(0.6, 0.84, t));
    float y = q.y / 0.9;
    float lens = pow(max(1.0 - y * y, 0.0), 0.8);
    float frame = floor(Age * 8.0);
    float right = open * (0.26 * lens + jag(q.y * 9.0, 3.0 + frame) * 0.05 * lens);
    float left = open * (0.26 * lens + jag(q.y * 9.0, 11.0 + frame) * 0.05 * lens);
    float w = 0.02 / Extent.x;
    float within = step(abs(y), 1.0);
    float aa = fwidth(q.x) + 1e-5;
    float inside = smoothstep(-left - aa, -left + aa, q.x) * (1.0 - smoothstep(right - aa, right + aa, q.x)) * within;
    hold = inside * 0.9 * Intensity;

    float rims = (line(abs(q.x - right), w) + line(abs(q.x + left), w)) * within * step(0.01, open);
    float cracks = 0.0;
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float side = i < 3 ? 1.0 : -1.0;
        float yAt = (hash11(fi * 3.3 + 40.0) - 0.5) * 1.3;
        float lensAt = pow(max(1.0 - pow(yAt / 0.9, 2.0), 0.0), 0.8);
        vec2 from = vec2(side * open * 0.26 * lensAt, yAt);
        vec2 bend = from + vec2(side * 0.1, (hash11(fi + frame) - 0.5) * 0.12) * open;
        vec2 end = bend + vec2(side * 0.09, (hash11(fi * 1.7 + frame) - 0.5) * 0.14) * open;
        cracks += line(min(segment(q, from, bend), segment(q, bend, end)), w * 0.7);
    }
    cracks *= step(0.05, open) * (0.6 + 0.4 * hash11(frame));

    vec2 drift = q + vec2(Age * 0.05, Age * 0.12);
    vec2 cellAt = floor(drift * 14.0);
    vec2 starAt = (cellAt + 0.2 + 0.6 * vec2(hashCell(cellAt), hashCell(cellAt + 31.0))) / 14.0;
    float stars = exp(-dot(drift - starAt, drift - starAt) / 0.00005) * step(0.6, hashCell(cellAt + 7.0)) * 1.2;
    float nebula = noise(q * 4.0 + vec2(Age * 0.2, -Age * 0.1)) * 0.25;
    float seal = exp(-pow((t - 0.84) / 0.04, 2.0));
    float scar = line(abs(q.x), w * 1.5) * within * lens * seal * 1.5 + exp(-dot(q, q) / 0.01) * seal * 1.5;
    float fade = 1.0 - smoothstep(0.86, 1.0, t);
    vec3 col = palette(y * 0.4 + Age * 0.05);
    float lit = (rims * 1.3 + cracks + (stars + nebula) * inside) * fade + scar;
    return vec4(hot(col, rims * 0.3 + stars * inside * 0.5 + seal * 0.5), lit * discEdge(length(p)));
}

vec4 clock(vec2 p) {
    float t = clamp(Progress, 0.0, 1.0);
    float done = clamp(Segment.x, 0.0, 1.0);
    vec2 q = p / max(easeBack(Age / 0.3), 0.02);
    float r = length(q);
    float a = fract(atan(q.x, q.y) / TAU + 1.0);
    float w = 0.018 / Extent.x;
    float urgency = smoothstep(0.55, 1.0, t);
    float beat = 0.5 + 0.5 * cos(Age * mix(5.0, 20.0, urgency));

    float lit = line(abs(r - 0.92), w * 1.3) * (0.8 + urgency * beat);
    lit += line(abs(r - 0.8), w * 0.7) * 0.35;

    float k = floor(a * 12.0 + 0.5);
    float at = k / 12.0;
    vec2 dir = vec2(sin(at * TAU), cos(at * TAU));
    float along = dot(q, dir);
    float across = abs(q.x * dir.y - q.y * dir.x);
    float inner = mod(k, 3.0) < 0.5 ? 0.56 : 0.66;
    float mark = line(length(vec2(across, max(max(inner - along, along - 0.78), 0.0))), w);
    float ahead = step(t, fract(at - 1e-4) + 1e-4) + step(11.5, k);
    lit += mark * mix(0.2, 0.85, clamp(ahead, 0.0, 1.0));

    float left = step(t, a) * (1.0 - smoothstep(0.78, 0.8, r));
    float edge = exp(-pow((a - t) * TAU * max(r, 0.15) / 0.06, 2.0)) * step(t, a);
    lit += left * (0.05 + 0.05 * urgency * beat) + edge * 0.35 * (1.0 - smoothstep(0.78, 0.8, r));

    vec2 hand = vec2(sin(t * TAU), cos(t * TAU));
    float stroke = line(segment(q, -hand * 0.12, hand * 0.74), w * 1.3) * 1.1;
    vec2 toTip = q - hand * 0.74;
    float tip = exp(-dot(toTip, toTip) / 0.003) * 0.8;
    float hub = exp(-r * r / 0.002) * 0.8 + line(abs(r - 0.07), w * 0.6) * 0.5;
    lit += stroke + tip + hub;

    float flare = done * (1.0 - done) * 4.0;
    lit *= (1.0 - done) + flare * 1.5;
    vec3 col = palette(a * 0.5 + Age * 0.05);
    return vec4(hot(col, 0.15 + (stroke + tip) * 0.3 + flare * 0.5), lit * discEdge(r));
}

vec4 singularity(vec2 p) {
    float t = Progress;
    float form = ease((t - 0.1) / 0.22);
    float evaporate = smoothstep(0.76, 0.93, t);
    float R = 0.24 * form * (1.0 - evaporate);
    float r = length(p);
    float w = 0.016 / Extent.x;
    float aa = fwidth(r) + 1e-5;
    float horizon = (1.0 - smoothstep(R - aa, R + aa, r)) * step(0.002, R);
    hold = horizon * 0.95 * Intensity;

    vec2 d = vec2(p.x, p.y / 0.24);
    float rd = length(d);
    vec2 dir = d / max(rd, 1e-4);
    float inner = max(R * 1.6, 0.04);
    float outer = 0.95 * form * (1.0 - evaporate * 0.6);
    float band = smoothstep(inner, inner + 0.05, rd) * (1.0 - smoothstep(outer * 0.55, outer, rd));
    vec2 spun = rot(Age * 1.6 / pow(max(rd, 0.2), 1.5)) * dir;
    float streaks = noise(spun * 2.5 + rd * 8.0) * 0.6 + noise(spun * 6.0 + rd * 19.0) * 0.4;
    float toward = 0.6 - 0.4 * dir.x;
    float heat = exp(-(rd - inner) * 5.0);
    float disc = band * (0.25 + streaks * 0.9) * toward * (0.6 + heat * 1.2);
    disc *= 1.0 - horizon * step(0.0, p.y);

    float a = atan(p.y, p.x);
    vec2 heading = vec2(cos(a), sin(a));
    float arch = exp(-pow((r - R * 1.28) / (R * 0.16 + 1e-3), 2.0)) * step(0.002, R) * 1.3;
    float archStreaks = noise(rot(Age * 2.5) * heading * 3.0 + 5.0);
    float above = smoothstep(-0.4, 0.6, p.y / max(r, 1e-3));
    float lensed = arch * (0.3 + 0.7 * above) * (0.5 + archStreaks) * (0.6 - 0.4 * heading.x);
    float photon = line(abs(r - R * 1.06), w * 0.6) * step(0.002, R) * 0.8;

    float infall = 1.0 - smoothstep(0.12, 0.38, t);
    float wind = fract((a + log(max(r, 1e-3)) * 2.5 + Age * 5.0) * 3.0 / TAU);
    float arms = exp(-pow((wind - 0.5) / 0.12, 2.0)) * smoothstep(0.95, 0.5, r) * smoothstep(0.02, 0.15, r)
        * infall * 0.8;
    float swallow = exp(-r * r / 0.004) * infall * smoothstep(0.0, 0.1, t);

    float pop = exp(-pow((t - 0.94) / 0.025, 2.0));
    float flash = exp(-r * r / 0.006) * pop * 3.0;
    float shock = line(abs(r - ease((t - 0.93) / 0.07) * 0.9), w * 2.0) * step(0.93, t) * (1.0 - smoothstep(0.93, 1.0, t));

    float lit = disc + lensed + photon + arms + swallow + flash + shock;
    vec3 col = palette(rd * 0.5 - Age * 0.08);
    return vec4(hot(col, heat * band * 0.5 + photon * 0.4 + flash * 0.5 + 0.08), lit * discEdge(r));
}

float effigyFace(vec2 q) {
    vec2 h = (q - vec2(0.0, -0.02)) / vec2(0.42, 0.56);
    float head = abs(length(h) - 1.0) * 0.42;
    vec2 e = rot(-0.25) * vec2(abs(q.x) - 0.17, q.y - 0.02);
    float eye = abs(length(e / vec2(0.1, 0.06)) - 1.0) * 0.06;
    float brow = segment(vec2(abs(q.x), q.y), vec2(0.06, 0.15), vec2(0.3, 0.25));
    float mouth = abs(length((q - vec2(0.0, -0.3)) / vec2(0.1, 0.12)) - 1.0) * 0.1;
    float cheek = segment(vec2(abs(q.x), q.y), vec2(0.2, -0.12), vec2(0.24, -0.32));
    return min(min(min(head, eye), min(brow, mouth)), cheek);
}

vec4 effigy(vec2 p) {
    float t = Progress;
    float w = 0.022 / Extent.x;
    float kindle = ease(t / 0.18);
    float flow = Age * 3.0;
    vec2 q = p + vec2(noise(vec2(p.y * 6.0 - flow, 1.7)) - 0.5, noise(vec2(p.x * 6.0, p.y * 4.0 - flow)) - 0.5) * 0.03;
    float face = line(effigyFace(q), w);

    float tongues = 0.0;
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        float n = noise(vec2(q.x * (6.0 + fk * 4.0) + fk * 7.3, q.y * (3.0 + fk * 2.0) - flow * (1.4 + fk * 0.5)));
        n = n * n;
        vec2 s = q - vec2((n - 0.25) * 0.04, n * (0.45 - fk * 0.1) * kindle);
        float d = effigyFace(s);
        tongues += exp(-d / (0.018 + n * 0.03)) * smoothstep(0.05, 0.4, n) * (0.7 - fk * 0.15);
    }

    vec2 e = rot(-0.25) * vec2(abs(q.x) - 0.17, q.y - 0.02);
    float eyes = exp(-dot(e, e) / 0.003) * 1.4;
    float inside = 1.0 - smoothstep(0.95, 1.0, length((p - vec2(0.0, -0.02)) / vec2(0.42, 0.56)));
    float fill = inside * (0.06 + 0.06 * noise(p * 8.0 - vec2(0.0, flow)));

    float burn = mix(-0.15, 1.1, smoothstep(0.42, 0.92, t));
    float f = clamp((p.y + 0.6) / 1.3, 0.0, 1.0) * 0.75 + noise(p * 5.0 + 3.0) * 0.25;
    float left = smoothstep(burn, burn + 0.04, f);
    float ember = exp(-pow((f - burn) / 0.025, 2.0)) * step(0.0, burn) * step(burn, 1.0)
        * (1.0 - smoothstep(0.95, 1.08, length((p - vec2(0.0, -0.02)) / vec2(0.42, 0.56))));

    float shown = smoothstep(0.0, 0.05, t) * (1.0 - smoothstep(0.88, 1.0, t));
    float lit = ((face * 0.9 * kindle + tongues + fill + eyes * kindle) * left + ember * 1.6) * shown;
    vec3 col = palette(clamp(p.y * 0.5 + 0.5, 0.0, 1.0) * 0.5 - Age * 0.1);
    return vec4(hot(col, tongues * 0.25 + eyes * 0.5 + ember * 0.6 + 0.05), lit * discEdge(length(p)));
}

vec4 aurora(vec2 uv) {
    float t = Progress;
    float period = max(floor(Extent.x * 0.6) * 2.0, 4.0);
    float x = uv.x / Extent.x * period;
    float y = uv.y / Extent.y;
    float drift = Age * 0.35;
    float hem = -0.7 + (periodicNoise(vec2(x * 0.5 + drift, Age * 0.25), period * 0.5) - 0.5) * 0.9
        + (periodicNoise(vec2(x * 1.5 - drift * 1.7, 3.0 + Age * 0.4), period * 1.5) - 0.5) * 0.35;
    float above = y - hem;
    float tall = 0.9 * ease(t / 0.3);
    float curtain = smoothstep(-0.05, 0.03, above) * exp(-max(above, 0.0) / max(tall, 0.05));
    float rays = periodicNoise(vec2(x * 6.0 + drift * 3.0, Age * 0.7), period * 6.0);
    rays = 0.3 + 0.7 * rays * rays;
    float hemGlow = exp(-pow(above / 0.07, 2.0)) * 0.6;
    float shimmer = 0.75 + 0.25 * sin(Age * 5.0 + x / period * TAU * 7.0);
    float shown = smoothstep(0.0, 0.15, t) * (1.0 - smoothstep(0.65, 1.0, t)) * (1.0 - smoothstep(1.1, 1.55, y));
    float lit = (curtain * rays * 0.85 + hemGlow) * shimmer * shown;
    vec3 col = palette(max(above, 0.0) * 0.3 + x / period);
    return vec4(hot(col, hemGlow * 0.3), lit);
}

vec4 prism(vec2 p) {
    float t = Progress;
    float w = 0.018 / Extent.x;
    float fade = 1.0 - smoothstep(0.7, 1.0, t);
    vec2 q = p / max(easeBack(t / 0.14), 0.02);
    vec2 A = vec2(0.0, 0.36);
    vec2 B = vec2(-0.31, -0.18);
    vec2 C = vec2(0.31, -0.18);
    float edges = min(min(segment(q, A, B), segment(q, B, C)), segment(q, C, A));
    float inside = step(0.0, q.y + 0.18) * step(abs(q.x) * 1.742 + q.y, 0.36);
    float glass = line(edges, w * 1.2) + inside * 0.07;
    float glint = exp(-pow((dot(q, vec2(0.7071)) - mix(-0.5, 0.5, fract(Age * 0.6))) / 0.04, 2.0)) * inside * 0.3;

    vec2 entry = vec2(-0.172, 0.06);
    vec2 exit = vec2(0.172, 0.06);
    vec2 source = entry + vec2(-1.01, 0.43);
    vec2 head = mix(source, entry, ease((t - 0.1) / 0.16));
    float beam = line(segment(p, source, head), w * 1.5) * step(0.1, t);
    vec2 crossing = mix(entry, exit, clamp((t - 0.26) / 0.06, 0.0, 1.0));
    beam += line(segment(p, entry, crossing), w * 1.2) * step(0.26, t) * 0.8;
    float sparks = (exp(-dot(p - entry, p - entry) / 0.002) * step(0.25, t)
        + exp(-dot(p - exit, p - exit) / 0.002) * step(0.32, t)) * 0.8;

    vec2 fanned = p - exit;
    float dist = length(fanned);
    float across = (atan(fanned.y, fanned.x) + 0.8) / 0.6;
    float reach = ease((t - 0.3) / 0.22) * 1.05;
    float within = step(0.0, across) * step(across, 1.0) * (1.0 - smoothstep(reach - 0.08, reach, dist));
    float k = clamp(floor(across * 4.0), 0.0, 3.0);
    float u = fract(across * 4.0);
    float fan = within * smoothstep(0.0, 0.12, u) * (1.0 - smoothstep(0.88, 1.0, u))
        * (0.5 + 0.5 * smoothstep(1.1, 0.1, dist)) * smoothstep(0.0, 0.05, dist) * 0.7;

    vec3 glassCol = palette(Age * 0.05);
    vec3 rgb = hot(glassCol, 0.3) * (glass + glint) + vec3(1.0) * (beam + sparks) + palette(k * 0.25) * fan;
    float lit = glass + glint + beam + sparks + fan;
    return vec4(rgb / max(lit, 1e-4), lit * fade * discEdge(length(p)));
}

vec4 roseWindow(vec2 p) {
    float t = Progress;
    vec2 q = p / max(easeBack(t / 0.15), 0.02);
    float r = length(q);
    float a = atan(q.x, q.y);
    float w = 0.012 / Extent.x;

    float ring = -1.0;
    float index = 0.0;
    float border = -1.0;
    if (r < 0.17) {
        ring = 0.0;
        border = 0.17 - r;
    } else if (r > 0.2 && r < 0.43) {
        float sector = TAU / 8.0;
        float u = mod(a, sector) - sector * 0.5;
        ring = 1.0;
        index = floor(mod(a, TAU) / sector);
        border = min(min(r - 0.2, 0.43 - r), r * sin(sector * 0.5 - abs(u)) - 0.012);
    } else if (r > 0.46 && r < 0.9) {
        float sector = TAU / 12.0;
        float u = mod(a, sector) - sector * 0.5;
        float lateral = r * sin(abs(u));
        float halfWidth = 0.075 * sqrt(clamp((0.86 - r) / 0.14, 0.0, 1.0));
        float lancet = min(halfWidth - lateral, r - 0.46);
        float between = mod(a + sector * 0.5, sector) - sector * 0.5;
        vec2 roundelAt = vec2(r * sin(between), r * cos(between) - 0.8);
        float roundel = 0.055 - length(roundelAt);
        if (lancet > roundel) {
            ring = 2.0;
            index = floor(mod(a, TAU) / sector);
            border = lancet;
        } else {
            ring = 3.0;
            index = floor(mod(a + sector * 0.5, TAU) / sector);
            border = roundel;
        }
    }
    float aa = fwidth(r) + 1e-5;
    float pane = smoothstep(0.0, aa * 1.5, border) * step(0.0, ring);
    float id = ring * 16.0 + index;
    float litAt = 0.1 + ring * 0.08 + hash11(id) * 0.05;
    float darkAt = 0.62 + (3.0 - ring) * 0.05;
    float glow = smoothstep(litAt, litAt + 0.05, t) * (1.0 - smoothstep(darkAt, darkAt + 0.06, t));
    float mottle = 0.7 + 0.3 * noise(q * 14.0 + id * 3.1);
    float sweep = mix(-1.4, 1.4, smoothstep(0.3, 0.6, t));
    float glint = exp(-pow((dot(q, vec2(0.7071)) - sweep) / 0.1, 2.0));
    float glass = pane * glow * (mottle * (0.35 + 0.25 * smoothstep(0.0, 0.05, border)) + glint * 0.6);

    float frame = 1.0 - smoothstep(0.96 - aa, 0.96 + aa, r);
    float stone = smoothstep(0.0, 0.08, t) * (1.0 - smoothstep(0.82, 1.0, t));
    hold = frame * (1.0 - pane * glow) * 0.8 * stone * Intensity;
    float edges = (line(abs(border), w) * step(0.0, ring) * 0.3 + line(abs(r - 0.96), w * 1.6) * 0.8
        + line(abs(r - 0.93), w) * 0.4) * stone;

    vec3 glassCol = palette(index * 0.25 + ring * 0.125);
    vec3 rgb = glassCol * glass + hot(palette(Age * 0.05), 0.5) * edges;
    float lit = glass + edges;
    return vec4(rgb / max(lit, 1e-4), lit * discEdge(r));
}


float leafEdge(vec2 q, vec2 base, vec2 dir, float len) {
    vec2 rel = q - base;
    float u = dot(rel, dir);
    float v = dot(rel, vec2(-dir.y, dir.x));
    float halfWidth = len * 0.26 * sin(PI * clamp(u / max(len, 1e-4), 0.0, 1.0));
    return max(abs(v) - halfWidth, max(-u, u - len));
}

float petalEdge(vec2 q, vec2 centre, vec2 dir, float a, float b) {
    vec2 rel = q - centre - dir * a;
    vec2 local = vec2(dot(rel, dir), dot(rel, vec2(-dir.y, dir.x)));
    return (length(local / vec2(a, b)) - 1.0) * b;
}

float lineAt(float d, float w, float aa) {
    float core = 1.0 - smoothstep(w - aa, w + aa, d);
    float halo = exp(-(d * d) / (w * w * 18.0)) * 0.45;
    return core + halo;
}

vec3 withered(vec3 c, float k) {
    float lum = dot(c, vec3(0.3, 0.5, 0.2));
    return mix(c, vec3(0.62, 0.46, 0.28) * (0.45 + lum * 0.6), k);
}

float wildflowerStem(float y, float droop, float sway) {
    float up = y / 0.66;
    return 0.022 * sin(y * 7.0 + Seed) * up + sway * up + droop * 0.2 * up * up * up;
}

vec4 wildflower(vec2 uv) {
    float t = Progress;
    vec2 q = vec2(uv.y, uv.x) / Extent.x;
    float w = 0.014 / Extent.x;
    float wither = smoothstep(0.66, 0.88, t);
    float sway = sin(Age * 1.3 + Seed) * 0.012;
    float top = 0.66 * ease((t - 0.08) / 0.32);
    float wind = hash11(9.0) > 0.5 ? 1.0 : -1.0;

    float yc = clamp(q.y, 0.0, top);
    float stem = line(length(vec2(q.x - wildflowerStem(yc, wither, sway), q.y - yc)), w * 1.3) * step(0.002, top);
    vec2 head = vec2(wildflowerStem(top, wither, sway), top - wither * 0.1);

    float leaves = 0.0;
    for (int k = 0; k < 2; k++) {
        float at = k == 0 ? 0.2 : 0.34;
        float side = k == 0 ? 1.0 : -1.0;
        float len = 0.17 * ease((top - at) / 0.14);
        vec2 dir = normalize(vec2(side, mix(0.75, -0.6, wither)));
        vec2 base = vec2(wildflowerStem(at, wither, sway), at);
        float d = leafEdge(q, base, dir, len);
        float inside = 1.0 - smoothstep(-w, w, d);
        float rib = line(abs(dot(q - base, vec2(-dir.y, dir.x))), w * 0.6) * inside;
        leaves += (line(abs(d), w) + inside * 0.14 + rib * 0.4) * step(0.001, len);
    }

    float bud = ease((t - 0.34) / 0.12) * (1.0 - smoothstep(0.5, 0.58, t));
    float budLit = line(abs(petalEdge(q, head - vec2(0.0, 0.03), vec2(0.0, 1.0), 0.04 * bud + 1e-3, 0.026 * bud + 1e-3)), w)
        * step(0.01, bud);
    float open = ease((t - 0.48) / 0.12);
    float petals = 0.0;
    float spin = Seed * 0.7 + 0.3;
    for (int k = 0; k < 6; k++) {
        float fk = float(k);
        float angle = fk * TAU / 6.0 + spin;
        vec2 dir = vec2(cos(angle), sin(angle));
        vec2 centre = head;
        float fallAt = 0.72 + hash11(fk * 1.7) * 0.16;
        float fallen = max(t - fallAt, 0.0);
        if (fallen > 0.0) {
            centre += vec2(wind * fallen * 0.9 + sin(fallen * 30.0 + fk) * 0.03, -fallen * 2.2);
            dir = rot(fallen * 9.0 * (fk - 2.5)) * dir;
        }
        float d = petalEdge(q, centre, dir, 0.075 * open + 1e-3, 0.034 * open + 1e-3);
        float shown = step(0.01, open) * (1.0 - smoothstep(0.0, 0.18, fallen));
        petals += (line(abs(d), w) + (1.0 - smoothstep(-w, w, d)) * 0.3) * shown;
    }
    vec2 toHeart = q - head;
    float heart = exp(-dot(toHeart, toHeart) / 0.0008) * open * 1.2;
    vec2 toSeed = q - vec2(0.0, 0.01);
    float seed = exp(-dot(toSeed, toSeed) / 0.0006) * (1.0 - smoothstep(0.1, 0.2, t)) * (0.8 + 0.4 * sin(Age * 12.0)) * 1.5;

    float shown = smoothstep(0.0, 0.04, t) * (1.0 - smoothstep(0.88, 1.0, t)) * (1.0 - wither * 0.4);
    vec3 green = withered(palette(0.25), wither);
    vec3 bloom = withered(palette(0.7 + Age * 0.04), wither * 0.8);
    vec3 rgb = green * (stem + leaves + budLit) + bloom * petals + hot(bloom, 0.6) * heart + vec3(1.0) * seed;
    float lit = stem + leaves + budLit + petals + heart + seed;
    return vec4(rgb / max(lit, 1e-4), lit * shown);
}

vec4 dandelion(vec2 uv) {
    float t = Progress;
    vec2 q = vec2(uv.y, uv.x) / Extent.x;
    float w = 0.01 / Extent.x;
    float top = 0.6 * ease(t / 0.2);
    float sway = sin(Age * 1.1 + Seed) * 0.015;
    float yc = clamp(q.y, 0.0, top);
    float stemAt = 0.018 * sin(yc * 5.0 + Seed) + sway * yc / 0.6;
    float stem = line(length(vec2(q.x - stemAt, q.y - yc)), w * 1.4) * step(0.002, top);
    vec2 head = vec2(0.018 * sin(top * 5.0 + Seed) + sway * top / 0.6, top);
    float puff = ease((t - 0.14) / 0.18);
    float wind = hash11(9.0) > 0.5 ? 1.0 : -1.0;

    float stalks = 0.0;
    float down = 0.0;
    for (int k = 0; k < 36; k++) {
        float fk = float(k);
        float z = 1.0 - 2.0 * (fk + 0.5) / 36.0;
        float ring = sqrt(1.0 - z * z);
        float phi = fk * 2.39996 + Seed;
        vec2 spoke = vec2(ring * cos(phi), z) * 0.17 * puff;
        float near = 0.6 + 0.4 * sin(phi);
        float leaveAt = 0.38 + hash11(fk * 1.7) * 0.42;
        float flown = max(t - leaveAt, 0.0);
        float gone = 1.0 - smoothstep(0.15, 0.4, flown);
        vec2 tip = head + spoke;
        if (flown > 0.0) {
            tip += vec2(wind * flown * 1.7, flown * 0.9 + sin(flown * 14.0 + fk) * 0.03);
            vec2 body = tip - normalize(spoke + vec2(0.0, -0.05)) * 0.05;
            stalks += line(segment(q, tip, body), w * 0.7) * 0.6 * gone;
        } else {
            stalks += line(segment(q, head, tip), w * 0.6) * 0.35 * near * step(0.01, puff);
        }
        vec2 d = q - tip;
        float tuft = exp(-dot(d, d) / 0.0005) * 0.6 + line(abs(length(d) - 0.016), w * 0.6) * 0.35;
        down += tuft * near * step(0.01, puff) * gone;
    }
    vec2 toHead = q - head;
    float receptacle = exp(-dot(toHead, toHead) / 0.0006) * puff;

    float shown = smoothstep(0.0, 0.05, t) * (1.0 - smoothstep(0.85, 1.0, t));
    vec3 stemCol = palette(0.25);
    vec3 downCol = palette(0.6 + Age * 0.04);
    vec3 rgb = stemCol * (stem + receptacle) + downCol * stalks + hot(downCol, 0.5) * down;
    float lit = stem + receptacle + stalks + down;
    return vec4(rgb / max(lit, 1e-4), lit * shown);
}

const float HYPHAE = 7.0;

float hyphaAngle(float root, float s1, float s2, float r) {
    float sector = TAU / HYPHAE;
    float first = smoothstep(0.28, 0.42, r);
    float second = smoothstep(0.56, 0.68, r);
    float a = (root + 0.5) * sector + (noise(vec2(root * 3.7, r * 4.0)) - 0.5) * 0.6;
    a += (s1 * sector * 0.24 + (noise(vec2(root * 5.1 + s1, r * 6.0)) - 0.5) * 0.25) * first;
    a += (s2 * sector * 0.12 + (noise(vec2(root * 7.3 + s1 * 2.0 + s2, r * 8.0)) - 0.5) * 0.18) * second;
    return a;
}

vec4 mycelium(vec2 p) {
    float t = Progress;
    float r = length(p);
    float a = atan(p.y, p.x);
    float reach = ease(t / 0.55) * 0.92;
    float w = 0.012 / Extent.x;
    float sector = TAU / HYPHAE;
    float nearest = floor(mod(a, TAU) / sector);
    float threads = 0.0;
    float tips = 0.0;
    float caps = 0.0;
    float sprout = smoothstep(0.5, 0.62, t);
    float aa = fwidth(r) * 1.2 + 1e-5;
    for (int di = -1; di <= 1; di++) {
        float root = mod(nearest + float(di), HYPHAE);
        for (int f = 0; f < 4; f++) {
            float s1 = f < 2 ? -1.0 : 1.0;
            float s2 = mod(float(f), 2.0) < 0.5 ? -1.0 : 1.0;
            float off = mod(a - hyphaAngle(root, s1, s2, r) + PI, TAU) - PI;
            float width = w * mix(1.6, 0.7, r);
            threads = max(threads, lineAt(r * abs(off), width, aa) * step(r, reach) * smoothstep(0.02, 0.06, r));
            vec2 toTip = p - polar(reach, hyphaAngle(root, s1, s2, reach));
            tips += exp(-dot(toTip, toTip) / 0.0006) * (1.0 - smoothstep(0.45, 0.6, t)) * step(0.05, reach);
            vec2 toCap = p - polar(0.92, hyphaAngle(root, s1, s2, 0.92));
            float capR = 0.035 * (0.7 + 0.6 * hashCell(vec2(root * 4.0 + float(f), 3.0))) * sprout;
            caps += (lineAt(abs(length(toCap) - capR), w, aa) + exp(-dot(toCap, toCap) / (capR * capR * 0.3 + 1e-5)) * 0.4)
                * step(0.001, capR);
        }
    }
    float pulse = 0.6 + 0.9 * exp(-pow((r - fract(Age * 0.55) * 1.3) / 0.07, 2.0));
    float knot = exp(-r * r / 0.003) * 0.9;
    float fade = 1.0 - smoothstep(0.78, 1.0, t);
    float lit = (threads * pulse + tips + caps + knot) * fade;
    vec3 col = palette(r * 0.6 - Age * 0.08);
    return vec4(hot(col, tips * 0.4 + knot * 0.3 + 0.05), lit * discEdge(r));
}

float briarVineX(float k, float vine, float along, float spacing, float slope) {
    return k * spacing + (along + 0.05 * sin(along * 5.0 + vine * 1.7)) / slope;
}

vec4 briar(vec2 uv) {
    float t = Progress;
    float H = Extent.y;
    float C = Extent.x;
    float count = max(floor(C / 0.9), 3.0);
    float spacing = C / count;
    float slope = 1.4;
    float w = 0.016;
    float wither = smoothstep(0.66, 0.86, t);
    float climbed = min(2.0 * H * ease(t / 0.45), 2.0 * H);

    float along = uv.y + H;
    float k = floor((uv.x - along / slope) / spacing + 0.5);
    float vine = mod(k, count);
    float off = uv.x - briarVineX(k, vine, along, spacing, slope);
    float within = step(0.0, along) * step(along, climbed);
    float aa = fwidth(uv.y) * 1.2 + 1e-5;
    float stems = lineAt(abs(off) * slope / sqrt(1.0 + slope * slope), w, aa) * within;

    vec2 up = normalize(vec2(1.0 / slope, 1.0));
    vec2 across = vec2(up.y, -up.x);
    float thorns = 0.0;
    float leaves = 0.0;
    float n = floor(along / 0.2);
    for (int j = -1; j <= 1; j++) {
        float m = n + float(j);
        float at = (m + 0.5) * 0.2;
        if (at < 0.0 || at > climbed) {
            continue;
        }
        vec2 onVine = vec2(briarVineX(k, vine, at, spacing, slope), at - H);
        float side = mod(m, 2.0) < 0.5 ? 1.0 : -1.0;
        if (mod(m, 3.0) < 0.5) {
            float grown = ease((climbed - at) / 0.4);
            vec2 dir = normalize(across * side + up * 0.6);
            float fallen = max(t - (0.72 + hashCell(vec2(vine, m)) * 0.14), 0.0);
            vec2 base = onVine + vec2(sin(fallen * 20.0 + m) * 0.05, -fallen * 5.0);
            dir = rot(fallen * 8.0 * side) * dir;
            float leaf = leafEdge(uv, base, dir, 0.22 * grown);
            leaves += (lineAt(abs(leaf), w * 0.8, aa) + (1.0 - smoothstep(-w, w, leaf)) * 0.15)
                * step(0.01, grown) * (1.0 - smoothstep(0.0, 0.1, fallen));
        } else {
            thorns += lineAt(segment(uv, onVine, onVine + across * side * 0.08 + up * 0.04), w * 0.6, aa) * 0.8;
        }
    }

    vec2 rel = uv - vec2(briarVineX(k, vine, 2.0 * H, spacing, slope), H);
    float open = ease((t - 0.45) / 0.15) * (1.0 - wither * 0.5);
    float rose = 0.0;
    for (int j = 0; j < 5; j++) {
        float angle = float(j) * TAU / 5.0 + vine;
        float petal = petalEdge(rel, vec2(0.0), vec2(cos(angle), sin(angle)), 0.1 * open + 1e-3, 0.06 * open + 1e-3);
        rose += lineAt(abs(petal), w * 0.8, aa) + (1.0 - smoothstep(-w, w, petal)) * 0.2;
    }
    rose = (rose + exp(-dot(rel, rel) / 0.0008) * 0.8) * step(0.01, open);

    float shown = smoothstep(0.0, 0.05, t) * (1.0 - smoothstep(0.86, 1.0, t)) * (1.0 - wither * 0.35);
    vec3 green = withered(palette(0.25 + vine / count * 0.25), wither);
    vec3 bloom = withered(palette(0.7), wither);
    vec3 rgb = green * (stems + thorns + leaves) + hot(bloom, 0.15) * rose;
    float lit = stems + thorns + leaves + rose;
    return vec4(rgb / max(lit, 1e-4), lit * shown);
}

const int TREE_BRANCHES = 63;

vec4 worldTree(vec2 uv) {
    float t = Progress;
    vec2 q = vec2(uv.y, uv.x) / Extent.x;
    float w = 0.012 / Extent.x;
    vec2 tips[TREE_BRANCHES];
    float heading[TREE_BRANCHES];
    float depth[TREE_BRANCHES];
    float wood = 0.0;
    float woodDepth = 0.0;
    float pulseAt = (t - 0.5) / 0.2 * 6.0;
    float pulsing = step(0.5, t) * (1.0 - step(0.72, t));
    for (int i = 0; i < TREE_BRANCHES; i++) {
        float fi = float(i);
        vec2 from = vec2(0.0);
        if (i == 0) {
            heading[i] = PI * 0.5;
            depth[i] = 0.0;
        } else {
            int parent = (i - 1) / 2;
            float side = (i & 1) == 1 ? 1.0 : -1.0;
            from = tips[parent];
            depth[i] = depth[parent] + 1.0;
            heading[i] = heading[parent] + side * (0.38 + 0.22 * hash11(fi)) + (hash11(fi + 70.0) - 0.5) * 0.2
                + sin(Age * 1.4 + depth[i]) * 0.025 * depth[i];
        }
        float d = depth[i];
        float len = 0.3 * pow(0.72, d) * (0.85 + 0.3 * hash11(fi + 50.0));
        float grown = clamp((t - (0.03 + d * 0.055)) / 0.075, 0.0, 1.0);
        tips[i] = from + vec2(cos(heading[i]), sin(heading[i])) * len * grown;
        if (grown > 0.0) {
            float b = line(segment(q, from, tips[i]), w * mix(2.6, 0.8, d / 5.0))
                * (1.0 + 1.2 * exp(-pow(d - pulseAt, 2.0)) * pulsing);
            if (b > wood) {
                wood = b;
                woodDepth = d;
            }
        }
    }
    float wind = hash11(9.0) > 0.5 ? 1.0 : -1.0;
    float bloom = smoothstep(0.42, 0.52, t);
    float leaves = 0.0;
    for (int i = 31; i < TREE_BRANCHES; i++) {
        float fi = float(i);
        float fallen = max(t - (0.7 + hash11(fi * 1.3) * 0.15), 0.0);
        vec2 at = tips[i] + vec2(wind * fallen * 0.8 + sin(fallen * 24.0 + fi) * 0.03, -fallen * 1.6);
        vec2 d = q - at;
        leaves += exp(-dot(d, d) / 0.0007) * bloom * (1.0 - smoothstep(0.0, 0.2, fallen)) * 0.9;
    }
    vec2 toRoot = q - vec2(0.0, 0.01);
    float seed = exp(-dot(toRoot, toRoot) / 0.002) * (1.0 - smoothstep(0.05, 0.2, t)) * 1.5;

    float shown = smoothstep(0.0, 0.03, t) * (1.0 - smoothstep(0.86, 1.0, t));
    vec3 barkCol = palette(woodDepth / 6.0 * 0.5 + Age * 0.04);
    vec3 leafCol = palette(0.65 + Age * 0.05);
    vec3 rgb = hot(barkCol, 0.1) * wood + hot(leafCol, 0.3) * leaves + vec3(1.0) * seed;
    float lit = wood + leaves + seed;
    return vec4(rgb / max(lit, 1e-4), lit * shown);
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
    } else if (Style == FLAME) {
        c = flame(localPos);
    } else if (Style == FIGURE) {
        c = figure(disc);
    } else if (Style == WEB) {
        c = web(disc);
    } else if (Style == FROST) {
        c = frost(disc);
    } else if (Style == MOTH) {
        c = moth(disc);
    } else if (Style == CONSTELLATION) {
        c = constellation(disc);
    } else if (Style == MAZE) {
        c = maze(disc);
    } else if (Style == CHAINS) {
        c = chains(localPos);
    } else if (Style == BLADE) {
        c = blade(localPos);
    } else if (Style == TESSERACT) {
        c = tesseract(disc);
    } else if (Style == SERPENT) {
        c = serpent(disc);
    } else if (Style == CLOCK) {
        c = clock(disc);
    } else if (Style == SINGULARITY) {
        c = singularity(disc);
    } else if (Style == EFFIGY) {
        c = effigy(disc);
    } else if (Style == AURORA) {
        c = aurora(localPos);
    } else if (Style == PRISM) {
        c = prism(disc);
    } else if (Style == ROSE_WINDOW) {
        c = roseWindow(disc);
    } else if (Style == WILDFLOWER) {
        c = wildflower(localPos);
    } else if (Style == DANDELION) {
        c = dandelion(localPos);
    } else if (Style == MYCELIUM) {
        c = mycelium(disc);
    } else if (Style == BRIAR) {
        c = briar(localPos);
    } else if (Style == WORLD_TREE) {
        c = worldTree(localPos);
    } else {
        c = rift(disc);
    }
    float amount = max(c.a, 0.0) * Intensity;
    fragColor = vec4(min(c.rgb * amount, vec3(1.0)), max(min(amount, 1.0) * 0.45, hold));
}
