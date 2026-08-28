#version 150

// Janus Threshold pane fragment shader.
//
// The destination view was rendered with this frame's exact camera projection
// from the folded camera, so the pixel that continues this line of sight is
// simply the one at this fragment's own screen position - no UV mapping, just
// gl_FragCoord / ScreenSize (both bottom-left origin, no Y flip). SceneSampler
// is a copy of the frame so far (see SceneSnapshot) and is addressed the same
// way, so sampling it at an *offset* refracts whatever is behind the pane.
//
// localPos is in blocks: (0,0)..WindowSize is the window rectangle, and the
// quad covers exactly that rectangle - the pane ends hard at its edge. What
// marks the edge is not a fade but a deliberate seam: a thin, pulsing
// amethyst glow hugging the border from the inside, so a framed pane reads as
// enchanted glass set into its frame and an unframed one as a crisply cut,
// clearly magical opening rather than an accidental tear in the renderer.
//
// One sweep drives the whole animation. A reveal front runs centre-out over
// Progress 0..1; the pane's *presence* rides that front, and the destination
// view follows VIEW_LAG behind it. The gap between the two is the warp band:
// there the pane shows the world behind it refracted through a lens of
// amethyst energy rather than any colour of its own, so opening reads as
// space buckling and tearing, and closing (which never has a view) as the
// whole opening collapsing back into an unremarkable patch of air. Because a
// refraction of zero is the background itself, the warp has no edge to be
// harsh - it simply stops mattering.
//
// With SceneReady 0 (no scene copy this frame - inside another portal's view
// pass, where sampling would be recursive) the warp degrades to a translucent
// violet swirl on a radial gradient rather than a hard-edged plate.

uniform sampler2D PortalSampler;
uniform sampler2D SceneSampler;

uniform vec2 ScreenSize;
uniform float GameTime;
uniform vec2 WindowSize;
uniform float Progress;
uniform float ViewReady;
uniform float SceneReady;

in vec2 localPos;

out vec4 fragColor;

const vec3 RIM_COLOR = vec3(0.66, 0.38, 1.0);   // amethyst body
const vec3 RIM_LIGHT = vec3(0.86, 0.68, 1.0);   // bright edge core
const vec3 VOID_COLOR_A = vec3(0.10, 0.03, 0.22);
const vec3 VOID_COLOR_B = vec3(0.32, 0.12, 0.55);

// Softness of the reveal front, as a fraction of the pane's half-diagonal.
const float FRONT_SOFT = 0.35;
// How far the warp band runs ahead of the destination view, same units. This
// is the whole visual: too small and the view snaps in behind a hard line,
// too large and the pane spends its animation as a puddle of haze.
const float VIEW_LAG = 0.5;
// Peak refraction offset as a fraction of the pane's on-screen span, so a
// pane across the field of view and one at forty blocks warp by the same
// amount *of themselves* rather than the same number of pixels.
const float WARP_SPAN = 0.035;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

// GameTime is days; scale to something that moves.
float animTime() {
    return GameTime * 1200.0;
}

vec3 voidSwirl(vec2 p) {
    float t = animTime() * 0.25;
    vec2 c = p - WindowSize * 0.5;
    float ang = atan(c.y, c.x);
    float r = length(c);
    float swirl = noise(vec2(ang * 2.0 + t, r * 1.5 - t * 0.7));
    float pulse = 0.5 + 0.5 * sin(t * 0.8 + r * 2.0);
    return mix(VOID_COLOR_A, VOID_COLOR_B, swirl) + RIM_COLOR * pulse * 0.12;
}

// Direction and magnitude (roughly unit-scale) of the lens, in pane-local
// space: a radial pinch that vanishes at both the centre and the rim so the
// pane bulges like heated glass instead of sliding the picture sideways,
// plus travelling ripples and a little turbulence to break up the symmetry.
vec2 warpDirection(vec2 p, float radial) {
    float t = animTime();
    vec2 c = p - WindowSize * 0.5;
    vec2 dir = c / max(length(c), 0.001);
    float pinch = sin(3.14159265 * clamp(radial, 0.0, 1.0));
    float ripple = sin(radial * 9.0 - t * 1.7);
    vec2 turb = vec2(noise(p * 1.6 + vec2(t * 0.28, t * 0.15)),
                     noise(p * 2.9 + vec2(-t * 0.19, t * 0.33))) - 0.5;
    return dir * (pinch * 0.85 + ripple * 0.25) + turb * 1.3;
}

void main() {
    vec2 p = localPos;

    // Screen-space size of the pane, from the rate localPos changes per pixel.
    // Derivatives must be taken in uniform control flow, so this is computed
    // before anything can discard.
    float blocksPerPixel = max(fwidth(p.x) + fwidth(p.y), 1e-5);
    float paneSpanPixels = clamp((WindowSize.x + WindowSize.y) / blocksPerPixel, 16.0, 8192.0);
    float warpAmp = paneSpanPixels * WARP_SPAN;

    vec2 c = p - WindowSize * 0.5;
    float diag = length(WindowSize * 0.5) + 0.001;
    float radial = length(c) / diag;
    float tear = noise(p * 2.4 + vec2(0.0, animTime() * 0.6)) - 0.5;

    // The sweep has to carry the front past the corners (radial 1) *and* drag
    // the trailing view front the whole VIEW_LAG behind it, plus the slack the
    // tear noise can subtract - so at Progress 1 every fragment, corners
    // included, is fully revealed and fully showing the destination.
    float front = Progress * (1.05 + FRONT_SOFT + VIEW_LAG) - radial + tear * 0.10;
    float presence = smoothstep(0.0, FRONT_SOFT, front) * smoothstep(0.0, 0.02, Progress);
    if (presence <= 0.004) {
        discard;
    }
    // A pane with no view to show never stops warping: the destination front
    // simply never arrives, and the whole pane stays a lens.
    float show = ViewReady > 0.5 ? smoothstep(0.0, FRONT_SOFT, front - VIEW_LAG) : 0.0;
    float warp = presence * (1.0 - show);

    // Never refract right at the frame: the sample would drag pixels from
    // outside the opening across the seam.
    float borderDist = min(min(p.x, p.y), min(WindowSize.x - p.x, WindowSize.y - p.y));
    float borderBand = min(0.30, min(WindowSize.x, WindowSize.y) * 0.2);
    float borderFade = smoothstep(0.0, borderBand, borderDist);

    vec2 screenUV = gl_FragCoord.xy / ScreenSize;

    vec3 veil;
    float veilAlpha;
    if (SceneReady > 0.5) {
        vec2 offset = warpDirection(p, radial) * (warp * borderFade * warpAmp) / ScreenSize;
        veil = textureLod(SceneSampler, clamp(screenUV + offset, vec2(0.001), vec2(0.999)), 0.0).rgb;
        veil += RIM_COLOR * warp * 0.10;   // just enough tint to read as charged air
        // The lens has to *replace* the background over most of its area to
        // stay crisp, but opacity must not outrun the displacement or the point
        // where it saturates becomes the hard line all over again. Rising a
        // little faster than warp keeps the fringe a ghosted double image -
        // which is what the outer edge of a heat shimmer looks like anyway -
        // and where warp reaches 0 the sample *is* the background, so there is
        // nothing left for a seam to be drawn between.
        veilAlpha = clamp(warp * 1.5, 0.0, 1.0);
    } else {
        veil = voidSwirl(p);
        // No scene to bend: a translucent radial gradient, densest in the
        // middle and thinning to nothing at the front, rather than a plate.
        veilAlpha = warp * (0.30 + 0.55 * (1.0 - clamp(radial, 0.0, 1.0)));
    }

    vec3 scene = veil;
    if (ViewReady > 0.5) {
        scene = mix(veil, textureLod(PortalSampler, screenUV, 0.0).rgb, show);
    }

    // Amethyst rim riding the tear front while opening, gone once settled.
    float opening = step(0.001, Progress) * (1.0 - step(0.999, Progress));
    float tearRim = exp(-abs(front) * 12.0) * opening;

    // The permanent border: distance to the nearest edge, glowing inward.
    // A soft breathing halo plus a bright core line right at the rim, both
    // held inside whatever the reveal has actually opened.
    float pulse = 0.78 + 0.22 * sin(animTime() * 1.6 + (p.x + p.y) * 1.1);
    float halo = exp(-borderDist * 5.0) * pulse * presence;
    float core = exp(-borderDist * 24.0) * pulse * presence;

    vec3 color = scene
        + RIM_COLOR * (tearRim * 1.4 + halo * 0.45)
        + RIM_LIGHT * core * 0.85;
    // Glow has to carry its own opacity, or blending against a barely-there
    // warp would dim it away.
    float alpha = clamp(max(max(veilAlpha, show), max(tearRim, halo * 0.5 + core)), 0.0, 1.0);
    fragColor = vec4(color, alpha);
}
