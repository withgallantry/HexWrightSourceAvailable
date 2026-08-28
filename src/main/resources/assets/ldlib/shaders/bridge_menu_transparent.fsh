#version 150

// Einstein-Rosen Bridge Network Visualisation
// Menu-compositor transparent version.
//
// Changes:
// - removes the opaque dark/scanline background
// - uses straight-alpha-friendly output so translucent glows do not form dark halos
// - alpha follows actual visible brightness rather than a broad invisible coverage mask
// - trims extremely faint outer glow to avoid a dark circular/elliptical footprint
// - geometry remains scaled to fill wide canvases

uniform vec2 iResolution;
uniform float iTime;

in vec2 texCoord;
out vec4 fragColor;

const float PI = 3.14159265359;
const int CHANNEL_COUNT = 16;

float hash1(float n) {
    return fract(sin(n * 127.1) * 43758.5453123);
}

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(0.0, 2.0/3.0, 1.0/3.0)) * 6.0 - 3.0);
    vec3 rgb = clamp(p - 1.0, 0.0, 1.0);
    return c.z * mix(vec3(1.0), rgb, c.y);
}

mat2 rot2(float a) {
    float s = sin(a);
    float c = cos(a);
    return mat2(c, -s, s, c);
}

float sdSegment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 0.00001), 0.0, 1.0);
    return length(pa - ba * h);
}

float gaussLine(float d, float w) {
    return exp(-d * d / max(0.000001, w * w));
}

// ------------------------------------------------------------
// Bridge geometry
// ------------------------------------------------------------

float bridgeRadius(float x) {
    // Slightly wider overall with a broader throat than before.
    float ax = abs(x);
    float c = (cosh(1.22 * ax) - 1.0) / (cosh(1.22) - 1.0);
    return 0.21 + 0.69 * c;
}

vec3 bridgePoint(float x, float ang) {
    float r = bridgeRadius(x);
    return vec3(
        x * 1.60,
        r * cos(ang),
        r * sin(ang)
    );
}

vec2 projectBridge(vec3 p) {
    // Horizontal axis with a mild 3D tilt only.
    p.yz = rot2(0.53) * p.yz;

    float camDist = 4.15;
    float persp = 1.78 / (camDist - p.z);
    vec2 uv = vec2(p.x, p.y) * persp;

    uv.x *= 1.03;
    uv.y *= 1.14;
    return uv;
}

float bridgeWire(vec2 p) {
    float accum = 0.0;

    const int RINGS = 20;
    const int MERIDIANS = 16;
    const int XSEG = 28;

    // Ring loops
    for (int xi = 0; xi < RINGS; ++xi) {
        float x = mix(-1.0, 1.0, float(xi) / float(RINGS - 1));

        for (int ai = 0; ai < MERIDIANS; ++ai) {
            float a0 = 2.0 * PI * float(ai) / float(MERIDIANS);
            float a1 = 2.0 * PI * float(ai + 1) / float(MERIDIANS);

            vec2 p0 = projectBridge(bridgePoint(x, a0));
            vec2 p1 = projectBridge(bridgePoint(x, a1));

            float d = sdSegment(p, p0, p1);
            accum += gaussLine(d, 0.0050); // thicker
        }
    }

    // Longitudinal meridians
    for (int ai = 0; ai < MERIDIANS; ++ai) {
        float ang = 2.0 * PI * float(ai) / float(MERIDIANS);

        for (int xi = 0; xi < XSEG; ++xi) {
            float x0 = mix(-1.0, 1.0, float(xi) / float(XSEG));
            float x1 = mix(-1.0, 1.0, float(xi + 1) / float(XSEG));

            vec2 p0 = projectBridge(bridgePoint(x0, ang));
            vec2 p1 = projectBridge(bridgePoint(x1, ang));

            float d = sdSegment(p, p0, p1);
            accum += gaussLine(d, 0.0044); // thicker
        }
    }

    return accum;
}

// ------------------------------------------------------------
// Channels
// ------------------------------------------------------------

vec2 cubicBezier(vec2 a, vec2 b, vec2 c, vec2 d, float t) {
    float it = 1.0 - t;
    return it*it*it*a
         + 3.0*it*it*t*b
         + 3.0*it*t*t*c
         + t*t*t*d;
}

float channelBaseY(float idx) {
    float u = (idx + 0.5) / 16.0;

    // Pulled inward a bit to leave padding inside the tunnel.
    float y = mix(-0.60, 0.60, u);
    y *= 0.96;
    y += 0.018 * sin(idx * 1.83);
    return y;
}

vec2 channelPath(float t, float idx) {
    float y0 = channelBaseY(idx);
    float throatY = y0 * 0.09;
    float midTilt = 0.040 * sin(idx * 0.71);

    vec2 p0 = vec2(-1.30, y0);
    vec2 p1 = vec2(-1.04, y0);
    vec2 p2 = vec2(-0.80, y0 * 0.82 + midTilt);
    vec2 p3 = vec2(-0.50, y0 * 0.34);

    vec2 p4 = vec2(-0.18, throatY);
    vec2 p5 = vec2( 0.18, throatY);
    vec2 p6 = vec2( 0.50, y0 * 0.34);

    vec2 p7 = vec2( 0.80, y0 * 0.82 - midTilt);
    vec2 p8 = vec2( 1.04, y0);
    vec2 p9 = vec2( 1.30, y0);

    if (t < 0.3333333) {
        float lt = t / 0.3333333;
        return cubicBezier(p0, p1, p2, p3, lt);
    } else if (t < 0.6666666) {
        float lt = (t - 0.3333333) / 0.3333333;
        return cubicBezier(p3, p4, p5, p6, lt);
    } else {
        float lt = (t - 0.6666666) / 0.3333334;
        return cubicBezier(p6, p7, p8, p9, lt);
    }
}

vec3 channelColor(float idx) {
    float h = fract(idx / 16.0 + 0.08);
    return hsv2rgb(vec3(h, 0.72, 1.0));
}

float pulseGate(float idx, float t) {
    float h1 = hash1(idx + 2.17);
    float h2 = hash1(idx + 7.91);

    float s1 = 0.5 + 0.5 * sin(t * (0.83 + h1 * 0.71) + h1 * 17.0);
    float s2 = 0.5 + 0.5 * sin(t * (0.49 + h2 * 0.63) + h2 * 29.0);

    float mixv = 0.55 * s1 + 0.45 * s2;
    return smoothstep(0.62, 0.88, mixv);
}

float pulsePosA(float idx, float t) {
    float speed = mix(0.11, 0.20, hash1(idx + 0.41));
    float pos = fract(t * speed + hash1(idx + 4.7) * 3.0);
    float dir = step(0.0, sin(t * (0.33 + hash1(idx + 1.3) * 0.52) + idx));
    return mix(1.0 - pos, pos, dir);
}

float pulsePosB(float idx, float t) {
    float speed = mix(0.09, 0.18, hash1(idx + 8.53));
    float pos = fract(t * speed + hash1(idx + 6.1) * 2.0);
    float dir = step(0.0, sin(t * (0.28 + hash1(idx + 11.0) * 0.47) + idx * 1.9));
    return mix(pos, 1.0 - pos, dir);
}

// ------------------------------------------------------------
// Main
// ------------------------------------------------------------

void mainImage(out vec4 outColor, in vec2 fragCoord) {
    vec2 uv = fragCoord.xy / iResolution.xy;
    float aspect = iResolution.x / iResolution.y;

    vec2 p = uv * 2.0 - 1.0;

    // Preserve most of the original aspect correction, but relax it on the
    // horizontal axis so the artwork fills wide canvases more naturally.
    // Scaling p.y down makes the rendered bridge appear taller, reducing
    // empty space above and below it.
    p.x *= mix(1.0, aspect, 0.78);
    p.y *= 0.78;

    float t = iTime;
    float rad = length(vec2(p.x / max(aspect, 0.001), p.y));

    // No painted background: RGB and alpha both start completely clear.
    vec3 color = vec3(0.0);

    float wire = bridgeWire(p);
    vec3 wireColorA = vec3(0.40, 0.72, 0.95);
    vec3 wireColorB = vec3(0.82, 0.46, 1.00);
    float wireMix = 0.5 + 0.5 * sin(t * 0.55 + p.x * 3.2);
    vec3 wireColor = mix(wireColorA, wireColorB, wireMix);
    color += wireColor * wire * 0.155;

    float throatGlow = exp(-((p.x * 0.70)*(p.x * 0.70) + (p.y * 1.40)*(p.y * 1.40)) * 4.7);
    color += vec3(0.35, 0.16, 0.55) * throatGlow * 0.26;

    for (int i = 0; i < CHANNEL_COUNT; ++i) {
        float idx = float(i);
        vec3 c = channelColor(idx);

        float line = 0.0;
        vec2 prev = channelPath(0.0, idx);

        const int STEPS = 40;
        for (int s = 1; s <= STEPS; ++s) {
            float tt = float(s) / float(STEPS);
            vec2 cur = channelPath(tt, idx);
            float d = sdSegment(p, prev, cur);
            line += gaussLine(d, 0.0052);
            prev = cur;
        }

        float gateA = pulseGate(idx, t);
        vec2 pulsePtA = channelPath(pulsePosA(idx, t), idx);
        float pulseA = gaussLine(length(p - pulsePtA), 0.020) * gateA;

        float gateB = pulseGate(idx + 23.0, t * 1.11);
        vec2 pulsePtB = channelPath(pulsePosB(idx, t), idx);
        float pulseB = gaussLine(length(p - pulsePtB), 0.016) * gateB;

        color += c * line * 0.16;
        color += c * pulseA * 1.15;
        color += mix(c, vec3(1.0), 0.45) * pulseB * 0.85;

    }

    float leftNode  = exp(-length(p - vec2(-1.18, 0.0)) * 5.0);
    float rightNode = exp(-length(p - vec2( 1.18, 0.0)) * 5.0);
    color += vec3(0.18, 0.35, 0.65) * leftNode * 0.22;
    color += vec3(0.65, 0.22, 0.85) * rightNode * 0.22;

    // Softly fade only the artwork near the frame edges. The background
    // itself remains alpha 0 everywhere there is no visible effect.
    float vignette = 1.0 - smoothstep(0.42, 1.28, rad);
    float edgeFade = 0.88 + 0.12 * vignette;
    color *= edgeFade;

    // Treat `color` as the premultiplied/emissive contribution we want to see.
    // Most menu UIs use standard SRC_ALPHA / ONE_MINUS_SRC_ALPHA blending,
    // which expects straight (un-premultiplied) RGB. Deriving alpha from the
    // brightest channel and dividing RGB by that alpha avoids the common
    // dark-circle / dark-halo artifact around soft glows.
    float peak = max(color.r, max(color.g, color.b));

    // Hard-trim only the extremely faint tail of the glow.
    // This keeps the visible bridge soft without leaving a huge translucent
    // footprint over the menu background.
    if (peak < 0.012) {
        outColor = vec4(0.0);
        return;
    }

    float alpha = clamp(peak, 0.0, 1.0);
    vec3 straightColor = clamp(color / max(alpha, 0.0001), 0.0, 1.0);

    outColor = vec4(straightColor, alpha);
}

void main() {
    mainImage(
        fragColor,
        vec2(texCoord.x * iResolution.x, texCoord.y * iResolution.y)
    );
}
