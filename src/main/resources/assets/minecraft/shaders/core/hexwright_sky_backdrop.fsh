#version 150

#moj_import <hexwright_sky.glsl>


uniform sampler2D NoiseSampler;

uniform float FaceSize;
uniform float Time;
uniform int Style;
uniform vec3 BaseColor;
uniform vec3 PrimaryColor;
uniform vec3 SecondaryColor;
uniform vec3 HighlightColor;
uniform float Coverage;
uniform float Seed;
uniform float Streaks;
uniform vec4 Sun0;

out vec4 fragColor;

const mat3 OCTAVE = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64) * 2.03;

vec2 noise(vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    vec2 uv = p.xy + vec2(37.0, 17.0) * p.z + f.xy;
    vec4 s = textureLod(NoiseSampler, (uv + 0.5) / 256.0, 0.0);
    return mix(s.xz, s.yw, f.z);
}

vec2 fbm(vec3 p, int octaves, vec3 drift) {
    vec2 sum = vec2(0.0);
    float amplitude = 0.5;
    float total = 0.0;
    for (int i = 0; i < octaves; i++) {
        sum += amplitude * noise(p);
        total += amplitude;
        p = OCTAVE * p + drift;
        amplitude *= 0.5;
    }
    return sum / total;
}

vec4 nebula(vec3 dir) {
    vec3 p = dir * 2.2 + Seed;
    vec2 warp = fbm(p * 0.55 + vec3(0.0, 0.0, Time * 0.004), 2, vec3(Time * 0.003, 0.0, 0.0));
    float spin = (warp.y - 0.5) * 6.2832 + Time * 0.01;
    vec3 swirl = vec3(cos(spin), sin(spin), cos(spin) * sin(spin)) * (warp.x - 0.25);
    vec2 gas = fbm(p + swirl * 1.6, 5, vec3(Time * 0.006, -Time * 0.004, Time * 0.005));

    float density = smoothstep(1.0 - Coverage, 1.0 - Coverage + 0.35, gas.x * 0.75 + warp.x * 0.45);
    float wisps = smoothstep(0.35, 0.8, gas.x);
    vec3 hue = mix(PrimaryColor, SecondaryColor, smoothstep(0.3, 0.7, gas.y));
    vec3 colour = BaseColor + hue * density * (0.35 + 0.65 * wisps);
    colour += HighlightColor * pow(density * wisps, 3.0) * 0.35;
    return vec4(colour, density);
}

vec4 day(vec3 dir) {
    float h = dir.y;
    vec3 colour = mix(PrimaryColor, BaseColor, pow(max(h, 0.0), 0.45));
    colour = mix(colour, mix(PrimaryColor, BaseColor, 0.3) * 0.85, smoothstep(0.0, -0.4, h));

    float toSun = max(dot(dir, Sun0.xyz), 0.0) * step(0.0001, Sun0.w);
    float sunward = max(dot(normalize(dir.xz + 0.0001), normalize(Sun0.xz + 0.0001)), 0.0) * step(0.0001, Sun0.w);
    colour += HighlightColor * (0.2 * pow(toSun, 8.0) + 0.35 * pow(toSun, 90.0));
    colour += HighlightColor * 0.2 * sunward * sunward * exp(-abs(h) * 5.0);

    float cover = 0.0;
    if (h > 0.0) {
        vec2 ceiling = dir.xz / (h + 0.1) * 1.5;
        ceiling *= vec2(1.0 - 0.5 * Streaks, 1.0 + 0.8 * Streaks);
        vec3 q = vec3(ceiling + vec2(0.012, 0.005) * Time, Seed + Time * 0.0025);
        float detail = smoothstep(0.08, 0.5, h);
        float n = 0.0;
        float amplitude = 0.5;
        float total = 0.0;
        vec3 x = q;
        for (int i = 0; i < 5; i++) {
            float a = amplitude * (i < 2 ? 1.0 : detail);
            n += a * noise(x).x;
            total += a;
            x = OCTAVE * x;
            amplitude *= 0.5;
        }
        n /= total;
        cover = smoothstep(1.0 - Coverage, 1.0 - Coverage + 0.2, n) * smoothstep(0.0, 0.15, h);

        float here = fbm(q, 2, vec3(0.0)).x;
        float sunwardOfHere = fbm(q + vec3(normalize(Sun0.xz + 0.0001) * 0.18, 0.0), 2, vec3(0.0)).x;
        float lit = clamp(0.65 + (here - sunwardOfHere) * 5.0, 0.0, 1.0);
        vec3 cloud = mix(SecondaryColor * mix(PrimaryColor, vec3(1.0), 0.5) * 0.75, SecondaryColor, lit);
        cloud += HighlightColor * 0.4 * pow(toSun, 6.0) * (1.0 - cover);
        colour = mix(colour, cloud, cover);
    }
    return vec4(colour, cover);
}

vec4 backdrop(vec3 dir) {
    vec3 colour = mix(mix(BaseColor, SecondaryColor, 0.5), BaseColor, smoothstep(-0.3, 0.8, dir.y));
    vec3 p = dir * 1.6 + Seed;
    vec2 n = fbm(p + vec3(Time * 0.004, 0.0, -Time * 0.003), 4, vec3(Time * 0.003, Time * 0.002, 0.0));
    float haze = smoothstep(1.0 - Coverage, 1.0 - Coverage + 0.4, n.x);
    colour += SecondaryColor * haze * (0.4 + 0.6 * n.y);
    return vec4(colour, haze);
}

void main() {
    float tileSize = FaceSize + 2.0;
    vec2 tile = floor(gl_FragCoord.xy / tileSize);
    vec2 uv = (gl_FragCoord.xy - tile * tileSize - 1.0) / FaceSize * 2.0 - 1.0;
    vec3 dir = normalize(faceDirection(int(tile.x) + 3 * int(tile.y), uv));
    if (Style == STYLE_NEBULA) {
        fragColor = nebula(dir);
    } else if (Style == STYLE_DAY) {
        fragColor = day(dir);
    } else {
        fragColor = backdrop(dir);
    }
}
