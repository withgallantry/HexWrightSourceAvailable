#version 150

#moj_import <fog.glsl>

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float DiscardThreshold;
uniform float GameTime;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

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

    vec2 u = step(vec2(0.5), f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float steppedFlame(vec2 cell, float t) {
    float y = cell.y;
    if (y < 0.0) {
        return 0.0;
    }

    float band = floor(y);
    float maxHeight = 18.0;
    float heightFade = 1.0 - smoothstep(13.0, maxHeight, band);
    float baseWidth = mix(
        17.0,
        1.5,
        pow(clamp(band / maxHeight, 0.0, 1.0), 0.72)
    );

    float rowShift = floor(sin(t * 2.0 + band * 0.75) * 2.0);
    rowShift += floor(
        (blockNoise(vec2(band * 0.7, floor(t * 3.0))) - 0.5) * 3.0
    );

    float edgeJitter = floor(
        (blockNoise(vec2(
            abs(cell.x) * 0.33,
            band * 0.45 + floor(t * 2.0)
        )) - 0.5) * 4.0
    );

    float x = abs(cell.x - rowShift);
    float inside = 1.0 - step(baseWidth + edgeJitter, x);

    float bite = blockNoise(vec2(
        floor(cell.x * 0.35) + floor(t * 2.0),
        band * 0.6
    ));
    float upper = smoothstep(5.0, 15.0, band);
    float cut = step(0.72, bite) * upper * step(baseWidth * 0.45, x);

    float baseSolid = 1.0 - smoothstep(0.0, 4.0, band);
    inside = max(
        inside * (1.0 - cut),
        baseSolid * (1.0 - step(18.0, abs(cell.x)))
    );

    return inside * heightFade;
}

float tongue(
    vec2 cell,
    float t,
    float xOffset,
    float height,
    float width,
    float phase
) {
    vec2 q = cell;
    q.x -= xOffset;
    q.y *= 18.0 / height;
    return steppedFlame(q / vec2(width, 1.0), t + phase);
}

void main() {
    // Fixed low-resolution canvas keeps the flame deliberately pixelated.
    vec2 virtualResolution = vec2(96.0, 30.0);
    vec2 pixel = floor(texCoord0 * virtualResolution);

    vec2 p = pixel;
    p.x -= virtualResolution.x * 0.5;
    p.y -= virtualResolution.y * 0.08;

    // Minecraft's GameTime is a repeating 0..1 value. Scaling it produces
    // a useful animation speed while preserving seamless repetition.
    float t = GameTime * 1200.0;

    vec2 cell = p;

    float base = 1.0 - step(20.0, abs(cell.x));
    base *= 1.0 - step(4.0, cell.y);
    base *= step(0.0, cell.y);

    float lowGlow = 1.0 - step(26.0, abs(cell.x));
    lowGlow *= 1.0 - step(7.0, cell.y);
    lowGlow *= step(0.0, cell.y);

    float outer = 0.0;
    float mid = 0.0;
    float core = 0.0;

    outer = max(outer, tongue(cell, t, -18.0, 14.0, 0.82, 0.2));
    outer = max(outer, tongue(cell, t, -11.0, 18.0, 0.92, 1.7));
    outer = max(outer, tongue(cell, t,  -3.0, 20.0, 1.00, 3.4));
    outer = max(outer, tongue(cell, t,   6.0, 17.0, 0.88, 4.1));
    outer = max(outer, tongue(cell, t,  15.0, 15.0, 0.80, 5.6));

    mid = max(mid, tongue(cell - vec2(0.0, 1.2), t + 0.8, -14.0, 12.0, 0.55, 1.1));
    mid = max(mid, tongue(cell - vec2(0.0, 1.5), t + 0.8,  -5.0, 16.0, 0.62, 2.8));
    mid = max(mid, tongue(cell - vec2(0.0, 1.4), t + 0.8,   5.0, 14.0, 0.58, 3.7));
    mid = max(mid, tongue(cell - vec2(0.0, 1.1), t + 0.8,  14.0, 11.0, 0.50, 4.9));

    core = max(core, tongue(cell - vec2(0.0, 2.2), t + 1.5, -7.0, 11.0, 0.34, 1.9));
    core = max(core, tongue(cell - vec2(0.0, 2.5), t + 1.5,  2.0, 13.0, 0.38, 2.6));
    core = max(core, tongue(cell - vec2(0.0, 2.1), t + 1.5, 10.0,  9.0, 0.30, 3.8));

    float emberNoise = blockNoise(vec2(
        floor((cell.x + 32.0) / 2.0),
        floor(t * 5.0)
    ));
    float ember = step(0.0, cell.y) * (1.0 - step(2.0, cell.y));
    ember *= 1.0 - step(25.0, abs(cell.x));
    ember *= 0.55 + emberNoise * 0.45;

    vec3 outerColor = vec3(0.38, 0.12, 0.85);
    vec3 midColor = vec3(0.18, 0.78, 1.00);
    vec3 coreColor = vec3(0.90, 0.98, 1.00);
    vec3 baseColor = vec3(0.10, 0.92, 1.00);
    vec3 auraColor = vec3(0.55, 0.20, 0.95);

    float flickerStep = blockNoise(vec2(
        floor(cell.x / 5.0),
        floor(t * 6.0)
    ));
    float flicker = 0.84 + 0.16 * flickerStep;

    vec3 color = vec3(0.0);
    float aura = max(outer, lowGlow * 0.42);

    color += auraColor * aura * 0.42;
    color += outerColor * max(outer, base * 0.75) * 0.95;
    color += midColor * max(mid, base * 0.50) * 1.10;
    color += coreColor * core * 1.08;
    color += baseColor * ember * 0.45;
    color *= flicker;

    float alpha = 0.0;
    alpha = max(alpha, aura * 0.45);
    alpha = max(alpha, outer * 0.78);
    alpha = max(alpha, mid * 0.90);
    alpha = max(alpha, core * 0.98);
    alpha = max(alpha, base * 0.62);
    alpha = max(alpha, ember * 0.72);
    alpha = clamp(alpha, 0.0, 1.0);

    // Preserve particle tint and opacity controls.
    vec4 particleTint = vertexColor * ColorModulator;
    vec4 finalColor = vec4(color * particleTint.rgb, alpha * particleTint.a);

    if (finalColor.a < DiscardThreshold) {
        discard;
    }

    fragColor = linear_fog(
        finalColor,
        vertexDistance,
        FogStart,
        FogEnd,
        FogColor
    );
}
