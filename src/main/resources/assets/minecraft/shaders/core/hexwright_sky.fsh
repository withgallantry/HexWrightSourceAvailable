#version 150

#moj_import <hexwright_sky.glsl>


uniform sampler2D AtlasSampler;

uniform mat4 ProjMat;
uniform vec2 ScreenSize;
uniform float FaceSize;
uniform float Time;
uniform int Style;
uniform float Seed;
uniform vec3 PrimaryColor;
uniform vec3 SecondaryColor;
uniform vec3 HighlightColor;
uniform vec4 Sun0;
uniform vec4 Sun1;

in vec3 skyDir;

out vec4 fragColor;

const float STAR_CELLS = 56.0;
const float STAR_CHANCE = 0.45;

const float LATTICE_SCALE = 3.0;

const float PRISM_CELLS = 3.0;
const float PRISM_CHANCE = 0.7;

vec2 stars(vec3 dir, vec2 uv, int face, float pixel) {
    vec2 cell = floor((uv * 0.5 + 0.5) * STAR_CELLS);
    uint h = hash(uint(cell.x) | (uint(cell.y) << 8) | (uint(face) << 16) | (uint(Seed) << 19));
    float spread = 1.0 + dot(uv, uv);
    if (unit(h) > STAR_CHANCE / (spread * sqrt(spread))) {
        return vec2(0.0);
    }
    vec2 at = ((cell + 0.15 + 0.7 * vec2(unit(h >> 8), unit(h >> 16))) / STAR_CELLS) * 2.0 - 1.0;
    vec3 star = normalize(faceDirection(face, at));
    float b = unit(h >> 24);
    b *= b;
    b *= b;
    float d = length(star - dir) / (pixel * (0.6 + 0.8 * b));
    float phase = fract(Time * (0.15 + 0.3 * b) + b * 7.0);
    float twinkle = 0.85 + 0.3 * abs(phase - 0.5);
    return vec2((0.04 + 1.5 * b) * twinkle * exp(-d * d), unit(h >> 4));
}

float nebulaSun(vec3 dir, vec4 sun) {
    if (sun.w <= 0.0) {
        return 0.0;
    }
    float angle2 = max(2.0 - 2.0 * dot(dir, sun.xyz), 0.0) / (sun.w * sun.w);
    float disc = 1.0 / (1.0 + angle2 * 25000.0);
    return disc * disc * 6.0 + 0.6 / (1.0 + angle2 * 1500.0) + 0.12 / (1.0 + angle2 * 30.0);
}

vec3 dayLights(vec3 dir, float pixel, float cover) {
    vec3 colour = vec3(0.0);
    float clear = 1.0 - 0.85 * cover;
    if (Sun0.w > 0.0) {
        float radius = 0.0047 * Sun0.w;
        float angle = length(dir - Sun0.xyz);
        float disc = 1.0 - smoothstep(radius - pixel, radius + pixel, angle);
        colour += HighlightColor * (disc * 2.5 + exp(-angle / (radius * 2.5)) * 0.6) * clear;
    }
    if (Sun1.w > 0.0) {
        float radius = 0.0047 * Sun1.w;
        vec3 moon = Sun1.xyz;
        float disc = 1.0 - smoothstep(radius - pixel, radius + pixel, length(dir - moon));
        vec3 sunSide = Sun0.xyz - moon * dot(Sun0.xyz, moon);
        sunSide = dot(sunSide, sunSide) > 1e-6 ? normalize(sunSide) : vec3(0.0, 1.0, 0.0);
        vec3 shadow = normalize(moon - sunSide * radius * 0.75);
        float dark = 1.0 - smoothstep(radius - pixel, radius + pixel, length(dir - shadow));
        colour += mix(SecondaryColor, vec3(1.0), 0.7) * 0.55 * disc * (1.0 - 0.85 * dark) * clear;
    }
    return colour;
}

vec3 lattice(vec3 dir, float pixel) {
    float lift = max(abs(dir.y), 0.001);
    float above = step(0.0, dir.y);
    vec2 p = dir.xz / lift * LATTICE_SCALE + (1.0 - above) * vec2(0.5, 0.2887);
    float footprint = LATTICE_SCALE * pixel / (lift * lift);
    float fade = clamp(1.0 - footprint * 5.0, 0.0, 1.0);

    const vec2 SPACING = vec2(1.0, 1.7320508);
    vec2 a = mod(p, SPACING) - SPACING * 0.5;
    vec2 b = mod(p - SPACING * 0.5, SPACING) - SPACING * 0.5;
    vec2 offset = dot(a, a) < dot(b, b) ? a : b;
    vec2 centre = p - offset;
    vec2 folded = abs(offset);
    float edge = 0.5 - max(dot(folded, vec2(0.5, 0.8660254)), folded.x);

    float line = 1.0 - smoothstep(0.02, 0.02 + footprint * 1.5, edge);
    float glow = exp(-edge / (0.07 + footprint));
    float wave = fract(length(centre) / LATTICE_SCALE * 0.12 - Time * 0.025);
    float pulse = exp(-pow((wave - 0.5) * 14.0, 2.0));
    ivec2 cell = ivec2(round(centre * vec2(2.0, 1.1547005)));
    uint h = hash(uint(cell.x + 2048) | (uint(cell.y + 2048) << 12) | (uint(above) << 24) | (uint(Seed) << 25));
    float flare = pow(max(sin(Time * (0.15 + 0.25 * unit(h)) + unit(h >> 8) * 6.2832), 0.0), 30.0);

    vec3 colour = PrimaryColor * (line * 0.9 + glow * 0.25)
        + HighlightColor * pulse * (line * 1.2 + glow * 0.5)
        + SecondaryColor * flare * smoothstep(0.0, 0.3, edge) * 0.5;
    return colour * fade;
}

const float RING_TILT[6] = float[6](0.08, 0.16, 0.05, 0.2, 0.75, 0.95);
const float RING_RADIUS[6] = float[6](0.3, 0.58, 0.86, 1.14, 1.3, 1.38);
const float RING_TURN[6] = float[6](0.01, -0.007, 0.006, -0.012, 0.004, -0.005);
const float RING_WIDTH[6] = float[6](0.006, 0.009, 0.007, 0.012, 0.008, 0.01);
const float RING_TICKS[6] = float[6](36.0, 72.0, 48.0, 96.0, 72.0, 120.0);
const float RING_BODY[6] = float[6](0.03, -0.022, 0.018, -0.026, 0.012, -0.015);

vec3 orrery(vec3 dir, float pixel) {
    vec3 colour = vec3(0.0);
    for (int i = 0; i < 6; i++) {
        float turn = RING_TURN[i] * Time + float(i) * 1.7 + Seed;
        vec3 axis = vec3(sin(RING_TILT[i]) * cos(turn), cos(RING_TILT[i]), sin(RING_TILT[i]) * sin(turn));
        float width = RING_WIDTH[i];
        float off = abs(dot(dir, axis) - cos(RING_RADIUS[i])) / sin(RING_RADIUS[i]);
        if (off > width + 0.08) {
            continue;
        }
        float rule = exp(-pow((off - width) / (pixel * 1.1), 2.0));
        float inside = 1.0 - smoothstep(width - pixel, width, off);

        vec3 across = normalize(cross(axis, vec3(0.0, 0.0, 1.0)));
        vec3 along = cross(axis, across);
        float theta = atan(dot(dir, along), dot(dir, across));
        float spacing = 6.2832 / RING_TICKS[i];
        float t = theta / spacing + Time * RING_TURN[i] * 2.0;
        float tick = exp(-pow(abs(fract(t + 0.5) - 0.5) * spacing * sin(RING_RADIUS[i]) / pixel, 2.0)) * inside;

        float phase = RING_BODY[i] * Time + float(i) * 2.3 + Seed * 0.37;
        vec3 bodyAt = axis * cos(RING_RADIUS[i]) + (across * cos(phase) + along * sin(phase)) * sin(RING_RADIUS[i]);
        float toBody = length(dir - bodyAt);
        float body = exp(-pow(toBody / (width * 1.6), 2.0)) + 0.25 * exp(-toBody / 0.025);

        colour += PrimaryColor * (rule * 0.9 + inside * 0.06) + SecondaryColor * tick * 0.8 + HighlightColor * body;
    }
    return colour;
}

vec4 prism(vec3 dir, vec2 uv, int face, float pixel) {
    vec2 cell = floor((uv * 0.5 + 0.5) * PRISM_CELLS);
    uint h = hash(uint(cell.x) | (uint(cell.y) << 4) | (uint(face) << 8) | (uint(Seed) << 12));
    if (unit(h) > PRISM_CHANCE) {
        return vec4(0.0);
    }
    vec2 wander = vec2(sin(Time * 0.05 + unit(h >> 8) * 6.2832), cos(Time * 0.04 + unit(h >> 16) * 6.2832)) * 0.08;
    vec2 at = ((cell + 0.5 + wander) / PRISM_CELLS) * 2.0 - 1.0;
    vec3 centre = normalize(faceDirection(face, at));
    vec3 across = normalize(cross(centre, abs(centre.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
    vec3 along = cross(centre, across);
    float facing = dot(dir, centre);
    if (facing <= 0.0) {
        return vec4(0.0);
    }
    vec2 local = vec2(dot(dir, across), dot(dir, along)) / facing;
    float spin = unit(h >> 4) * 6.2832 + Time * (0.015 + 0.03 * unit(h >> 24)) * ((h & 0x100u) == 0u ? 1.0 : -1.0);
    local = mat2(cos(spin), sin(spin), -sin(spin), cos(spin)) * local;

    float spread = 1.0 + dot(at, at);
    float reach = (0.13 + 0.07 * unit(h >> 20)) / (spread * sqrt(spread));
    int kind = int(unit(h >> 12) * 3.99);
    float sides = kind == 0 ? 3.0 : (kind == 1 ? 4.0 : 6.0);
    float distance;
    float size;
    if (kind == 3) {
        size = reach * 0.8;
        distance = length(local) - size;
    } else {
        float sector = 6.2832 / sides;
        size = reach * cos(sector * 0.5);
        float angle = mod(atan(local.y, local.x) + sector * 0.5, sector) - sector * 0.5;
        distance = length(local) * cos(angle) - size;
    }
    float fill = 1.0 - smoothstep(-pixel, pixel, distance);
    float outline = exp(-pow(distance / (pixel * 1.3), 2.0));
    float inner = exp(-pow((distance + size * 0.3) / (pixel * 1.1), 2.0));

    uint pick = (h >> 28) % 3u;
    vec3 tint = pick == 0u ? PrimaryColor : (pick == 1u ? SecondaryColor : HighlightColor);
    return vec4(tint, clamp(fill * 0.22 + outline * 0.85 + inner * 0.5, 0.0, 1.0));
}

void main() {
    vec3 dir = normalize(skyDir);
    vec2 uv;
    int face = faceOf(dir, uv);

    float tileSize = FaceSize + 2.0;
    vec2 texel = vec2(face % 3, face / 3) * tileSize + 1.0 + (uv * 0.5 + 0.5) * FaceSize;
    vec4 backdrop = texture(AtlasSampler, texel / (vec2(3.0, 2.0) * tileSize));
    vec3 colour = backdrop.rgb;
    float pixel = 2.0 / (ProjMat[1][1] * ScreenSize.y);

    if (Style == STYLE_DAY) {
        colour += dayLights(dir, pixel, backdrop.a);
    } else if (Style == STYLE_PRISM) {
        vec4 shape = prism(dir, uv, face, pixel);
        colour = mix(colour, shape.rgb, shape.a);
    } else {
        float starlight = Style == STYLE_NEBULA ? 1.0 : (Style == STYLE_ORRERY ? 0.8 : 0.5);
        vec2 star = stars(dir, uv, face, pixel);
        vec3 starTint = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.88, 0.75), star.y);
        colour += starTint * star.x * starlight * (1.0 - 0.6 * backdrop.a);
        if (Style == STYLE_NEBULA) {
            if (Sun0.w > 0.0) {
                colour += HighlightColor * (nebulaSun(dir, Sun0) + nebulaSun(dir, Sun1));
            }
        } else if (Style == STYLE_LATTICE) {
            colour += lattice(dir, pixel);
        } else {
            colour += orrery(dir, pixel);
        }
    }

    float dither = (fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715)))) - 0.5) / 255.0;
    fragColor = vec4(colour + dither, 1.0);
}
