#version 150

// Crystal Refraction
// Angular amethyst-like shard/facet shader for a sharper magical look.

uniform vec2 iResolution;
uniform float iTime;

in vec2 texCoord;
out vec4 fragColor;

float random(in vec2 st) {
    return fract(sin(dot(st, vec2(12.9898, 78.233))) * 43758.5453123);
}

float noise(in vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);

    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));

    vec2 u = f * f * (3.0 - 2.0 * f);

    return mix(a, b, u.x)
         + (c - a) * u.y * (1.0 - u.x)
         + (d - b) * u.x * u.y;
}

mat2 rot2(float a) {
    float s = sin(a);
    float c = cos(a);
    return mat2(c, -s, s, c);
}

float diamondDist(vec2 p) {
    return abs(p.x) + abs(p.y);
}

void mainImage(out vec4 outColor, in vec2 fragCoord) {
    vec2 uv = fragCoord.xy / iResolution.xy;
    vec2 p = uv - 0.5;
    p.x *= iResolution.x / iResolution.y;

    float t = iTime * 0.22;
    float d = length(p);

    // Slow drift in the crystal field
    vec2 q = rot2(0.25 * sin(t * 0.7)) * (p * 3.2);

    // Multi-angle crystal facets
    vec2 a = q;
    vec2 b = rot2(1.0472) * q;  // ~60 deg
    vec2 c = rot2(-0.7854) * q; // ~-45 deg

    float gridA = abs(fract(a.x) - 0.5);
    float gridB = abs(fract(b.x) - 0.5);
    float gridC = abs(fract(c.y) - 0.5);

    float facetLines = 0.0;
    facetLines += 1.0 - smoothstep(0.06, 0.10, gridA);
    facetLines += 1.0 - smoothstep(0.05, 0.09, gridB);
    facetLines += 1.0 - smoothstep(0.05, 0.09, gridC);

    // Diamond shard shapes within a repeated field
    vec2 cell = fract(q) - 0.5;
    float shard = 1.0 - smoothstep(0.18, 0.42, diamondDist(cell));

    // Subtle internal texture
    float tex = noise(q * 1.8 + vec2(t * 0.3, -t * 0.2));
    float flicker = 0.85 + 0.15 * sin(t * 3.5 + tex * 8.0);

    vec3 bg     = vec3(0.050, 0.015, 0.090);
    vec3 mid    = vec3(0.210, 0.070, 0.330);
    vec3 crystal= vec3(0.430, 0.200, 0.650);
    vec3 edge   = vec3(0.820, 0.630, 0.980);
    vec3 cyan   = vec3(0.550, 0.900, 1.000);

    vec3 color = mix(bg, mid, 0.50 + 0.18 * (1.0 - d));
    color = mix(color, crystal, shard * 0.55);
    color += edge * facetLines * 0.12 * flicker;
    color += cyan * facetLines * 0.035 * (0.5 + 0.5 * sin(t + q.x * 2.0));

    // Larger crystalline glints
    float glint = smoothstep(0.78, 0.96, facetLines * 0.55 + shard * 0.65);
    color += edge * glint * 0.18;

    // Edge darkening / framing
    float vignette = 1.0 - smoothstep(0.35, 1.08, d);
    color *= 0.78 + 0.22 * vignette;

    outColor = vec4(color, 1.0);
}

void main() {
    mainImage(
        fragColor,
        vec2(texCoord.x * iResolution.x, texCoord.y * iResolution.y)
    );
}
