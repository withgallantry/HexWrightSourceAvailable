#version 150

// Magical Crucible Flame - Chunky Pixel Art
// Wide, blocky magical flame for Minecraft-style menu use.
// Transparent background. Designed to feel like deliberate pixel-art flame,
// not a smooth flame that has merely been downscaled.

uniform vec2 iResolution;
uniform float iTime;
// 0.0 = empty/extinguished, 1.0 = fully fueled.
uniform float fuelLevel;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(127.1, 311.7));
    p += dot(p, p + 19.19);
    return fract(p.x * p.y);
}

float blockNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);

    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    // Deliberately chunky interpolation.
    vec2 u = step(vec2(0.5), f);

    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float steppedFlame(vec2 cell, float t) {
    // cell.x is centred around 0.
    // cell.y starts at 0 at the base and goes upward.

    float y = cell.y;
    if (y < 0.0) {
        return 0.0;
    }

    // Flame is wide at the bottom and narrows in hard pixel-art bands.
    float band = floor(y);
    float maxHeight = 18.0;

    float heightFade = 1.0 - smoothstep(13.0, maxHeight, band);
    float baseWidth = mix(17.0, 1.5, pow(clamp(band / maxHeight, 0.0, 1.0), 0.72));

    // Pixel-step waviness per row, rather than smooth curves.
    float rowShift = floor(sin(t * 2.0 + band * 0.75) * 2.0);
    rowShift += floor((blockNoise(vec2(band * 0.7, floor(t * 3.0))) - 0.5) * 3.0);

    float edgeJitter = floor((blockNoise(vec2(abs(cell.x) * 0.33, band * 0.45 + floor(t * 2.0))) - 0.5) * 4.0);

    float x = abs(cell.x - rowShift);
    float inside = 1.0 - step(baseWidth + edgeJitter, x);

    // Cut little blocky bites out of the upper body for flame tongues.
    float bite = blockNoise(vec2(floor(cell.x * 0.35) + floor(t * 2.0), band * 0.6));
    float upper = smoothstep(5.0, 15.0, band);
    float cut = step(0.72, bite) * upper * step(baseWidth * 0.45, x);

    // Keep base very solid.
    float baseSolid = 1.0 - smoothstep(0.0, 4.0, band);
    inside = max(inside * (1.0 - cut), baseSolid * (1.0 - step(18.0, abs(cell.x))));

    return inside * heightFade;
}

float tongue(vec2 cell, float t, float xOff, float height, float width, float phase) {
    vec2 q = cell;
    q.x -= xOff;
    q.y *= 18.0 / height;

    float f = steppedFlame(q / vec2(width, 1.0), t + phase);
    return f;
}

void mainImage(out vec4 outColor, in vec2 fragCoord) {
    vec2 uv = fragCoord.xy / iResolution.xy;

    // Convert to a low-res virtual pixel grid.
    // Increase pixelSize for chunkier pixels, decrease for finer pixels.
    float pixelSize = 2.0;
    vec2 virtualRes = floor(iResolution / pixelSize);
    vec2 px = floor(uv * virtualRes);

    float W = virtualRes.x;
    float H = virtualRes.y;

    // Work in a wide flame coordinate system.
    vec2 p = px;
    p.x = p.x - W * 0.5;
    p.y = p.y - H * 0.08;

    float t = iTime;

    // One gameplay-controlled value drives the whole visual state.
    float intensity = clamp(fuelLevel, 0.0, 1.0);
    float heightScale = mix(0.28, 1.0, intensity);
    float widthScale = mix(0.52, 1.0, intensity);
    float brightnessScale = mix(0.30, 1.0, intensity);
    float opacityScale = smoothstep(0.0, 0.12, intensity);

    // Scale so the effect fits many aspect ratios.
    float sx = max(W / 96.0, 1.0);
    float sy = max(H / 30.0, 1.0);

    vec2 cell = vec2(p.x / sx, p.y / sy);

    // Shrink both the complete flame and the spacing between its tongues as
    // fuel runs out. Dividing the coordinates makes the rendered shape smaller.
    vec2 flameCell = vec2(cell.x / widthScale, cell.y / heightScale);

    // Broad blocky underflame to avoid gaps.
    float base = 1.0 - step(20.0, abs(flameCell.x));
    base *= 1.0 - step(4.0, flameCell.y);
    base *= step(0.0, flameCell.y);

    float lowGlow = 1.0 - step(26.0, abs(flameCell.x));
    lowGlow *= 1.0 - step(7.0, flameCell.y);
    lowGlow *= step(0.0, flameCell.y);

    // Multiple chunky tongues across the crucible.
    float outer = 0.0;
    float mid = 0.0;
    float core = 0.0;

    outer = max(outer, tongue(flameCell, t, -18.0, 14.0, 0.82, 0.2));
    outer = max(outer, tongue(flameCell, t, -11.0, 18.0, 0.92, 1.7));
    outer = max(outer, tongue(flameCell, t,  -3.0, 20.0, 1.00, 3.4));
    outer = max(outer, tongue(flameCell, t,   6.0, 17.0, 0.88, 4.1));
    outer = max(outer, tongue(flameCell, t,  15.0, 15.0, 0.80, 5.6));

    mid = max(mid, tongue(flameCell - vec2(0.0, 1.2), t + 0.8, -14.0, 12.0, 0.55, 1.1));
    mid = max(mid, tongue(flameCell - vec2(0.0, 1.5), t + 0.8,  -5.0, 16.0, 0.62, 2.8));
    mid = max(mid, tongue(flameCell - vec2(0.0, 1.4), t + 0.8,   5.0, 14.0, 0.58, 3.7));
    mid = max(mid, tongue(flameCell - vec2(0.0, 1.1), t + 0.8,  14.0, 11.0, 0.50, 4.9));

    core = max(core, tongue(flameCell - vec2(0.0, 2.2), t + 1.5, -7.0, 11.0, 0.34, 1.9));
    core = max(core, tongue(flameCell - vec2(0.0, 2.5), t + 1.5,  2.0, 13.0, 0.38, 2.6));
    core = max(core, tongue(flameCell - vec2(0.0, 2.1), t + 1.5, 10.0,  9.0, 0.30, 3.8));

    // Blocky ember strip at the base.
    float emberNoise = blockNoise(vec2(floor((flameCell.x + 32.0) / 2.0), floor(t * 5.0)));
    float ember = step(0.0, flameCell.y) * (1.0 - step(2.0, flameCell.y));
    ember *= 1.0 - step(25.0, abs(flameCell.x));
    ember *= 0.55 + emberNoise * 0.45;

    // Pixel-art palette: magical violet shell, cyan flame, white-hot blocks.
    vec3 outerColor = vec3(0.38, 0.12, 0.85);
    vec3 midColor   = vec3(0.18, 0.78, 1.00);
    vec3 coreColor  = vec3(0.90, 0.98, 1.00);
    vec3 baseColor  = vec3(0.10, 0.92, 1.00);
    vec3 auraColor  = vec3(0.55, 0.20, 0.95);

    // Slight stepped flicker by whole pixel bands, not smooth flicker.
    float flickerStep = blockNoise(vec2(floor(flameCell.x / 5.0), floor(t * 6.0)));
    // Low fuel is less stable: deeper, more obvious stepped flicker.
    float flickerStrength = mix(0.42, 0.16, intensity);
    float flicker = (1.0 - flickerStrength) + flickerStrength * flickerStep;

    vec3 color = vec3(0.0);

    // Aura is chunky and broad.
    float aura = max(outer, lowGlow * 0.42);
    color += auraColor * aura * 0.42;
    color += outerColor * max(outer, base * 0.75) * 0.95;
    color += midColor * max(mid, base * 0.50) * 1.10;
    color += coreColor * core * 1.08;
    // Embers also die away as the fuel level falls.
    color += baseColor * ember * mix(0.10, 0.45, intensity);

    color *= flicker * brightnessScale;

    float alpha = 0.0;
    alpha = max(alpha, aura * 0.45);
    alpha = max(alpha, outer * 0.78);
    alpha = max(alpha, mid * 0.90);
    alpha = max(alpha, core * 0.98);
    alpha = max(alpha, base * 0.62);
    alpha = max(alpha, ember * 0.72);
    // At exactly zero fuel the effect is fully transparent.
    alpha = clamp(alpha * opacityScale, 0.0, 1.0);

    outColor = vec4(color, alpha);
}

void main() {
    mainImage(
        fragColor,
        vec2(texCoord.x * iResolution.x, texCoord.y * iResolution.y)
    );
}
