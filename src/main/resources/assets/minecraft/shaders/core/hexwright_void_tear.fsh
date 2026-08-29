#version 150

// Janus' Teeth - the void tear.
//
// Sibling to hexwright_portal.fsh and deliberately its opposite. A Threshold
// pane is a window: it shows somewhere else, so it has a view texture, a
// reveal front and a settled opaque state. A rip shows *nothing*. There is no
// destination, so there is no view sampler here at all - and dropping the view
// is what frees the geometry, because a pane that folds a camera must stay
// upright while a rip that folds nothing can hang at any angle it likes.
//
// What is left is a shape and a lip. localPos is in blocks, measured from the
// centre; TearSize is the rip's half-extents, so localPos / TearSize runs
// -1..1 over the quad. Inside that square the rip is a lens - widest at the
// middle, tapering to points at both ends - with its edge eaten away by static
// noise, which is what makes it read as torn rather than cut. The noise is
// seeded per rip and never animates: a silhouette that crawls looks like a
// rendering fault, not a wound. Only the glow and the churn move.
//
// Progress is the gape, 0 sealed to 1 wide open, and it drives the two axes at
// different rates: the long axis snaps out first, so the rip arrives as a
// hairline slash, and the aperture opens behind it. Run backwards it closes
// the same way, to a line and then to nothing.
//
// Three bands of colour, from the middle out: a near-black void that churns
// slowly and carries a few amethyst filaments, a hot amethyst lip hugging the
// jagged edge from the inside, and - just outside the rip - a band where the
// scene behind is refracted, so space is visibly buckled around the wound
// rather than merely interrupted by it. With SceneReady 0 (no scene copy this
// frame) that outer band degrades to a plain violet haze.
//
// That outer band is measured to the silhouette in *both* axes. Measuring it
// across only - which is all the interior needs - left the band at full
// strength everywhere past the tips, where the lens has already tapered to
// nothing: a bright hairline shooting out of each end of the rip. Wrapping the
// tips instead gives the band a rounded cap, the same thickness there as along
// the flanks.

uniform sampler2D SceneSampler;

uniform vec2 ScreenSize;
uniform float GameTime;
uniform vec2 TearSize;
uniform float Progress;
uniform float Seed;
uniform float SceneReady;

in vec2 localPos;

out vec4 fragColor;

const vec3 VOID_DEEP = vec3(0.016, 0.008, 0.035);  // the black of the wound
const vec3 VOID_CHURN = vec3(0.20, 0.07, 0.38);    // violet moving inside it
const vec3 RIM_COLOR = vec3(0.66, 0.38, 1.0);      // amethyst body, as the pane
const vec3 RIM_LIGHT = vec3(0.86, 0.68, 1.0);      // bright edge core

// How deeply the static noise bites into the lens edge, as a fraction of the
// half-aperture. Past about 0.5 the rip stops reading as one opening.
const float JAG_DEPTH = 0.42;
// Spatial frequency of that noise along the rip. High enough for several
// teeth, low enough that each one is legible.
const float JAG_FREQ = 5.5;
// Width of the outer refraction band, in normalised aperture units.
const float WARP_BAND = 0.9;
// Peak refraction offset as a fraction of the rip's on-screen span, so a rip
// at arm's length and one at thirty blocks buckle by the same amount *of
// themselves* rather than the same number of pixels.
const float WARP_SPAN = 0.05;

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

float fbm(vec2 p) {
    return noise(p) * 0.6 + noise(p * 2.3) * 0.3 + noise(p * 4.7) * 0.1;
}

// GameTime is days; scale to something that moves.
float animTime() {
    return GameTime * 1200.0;
}

// Half-height of the rip at position `along` (-1..1) on its long axis, in
// normalised aperture units. The lens profile tapers to a point at each end;
// the noise term is what tears the edge, and is sampled on a lane that depends
// on which side of the rip we are on so the two edges never mirror each other.
float edgeProfile(float along, float side) {
    float lens = pow(max(0.0, 1.0 - along * along), 0.72);
    float torn = fbm(vec2(along * JAG_FREQ + Seed, side * 3.7 + Seed * 0.37));
    return lens * (1.0 - JAG_DEPTH * torn);
}

void main() {
    // Screen-space size of the quad, from the rate localPos changes per pixel.
    // Derivatives must be taken in uniform control flow, so this happens before
    // anything can discard.
    float blocksPerPixel = max(fwidth(localPos.x) + fwidth(localPos.y), 1e-5);
    float spanPixels = clamp((TearSize.x + TearSize.y) * 2.0 / blocksPerPixel, 16.0, 8192.0);
    float warpAmp = spanPixels * WARP_SPAN;

    if (Progress <= 0.001) {
        discard;
    }

    // The two axes open at different rates - long axis first, aperture behind
    // it - so the rip arrives as a slash and then gapes.
    float openLong = smoothstep(0.0, 0.35, Progress);
    float openAcross = pow(Progress, 1.6);
    if (openLong <= 0.001 || openAcross <= 0.001) {
        discard;
    }

    vec2 n = localPos / TearSize;
    float along = n.x / openLong;
    float across = n.y / openAcross;
    // Past the tips there is no lens left, but there is still a band of
    // buckled space to draw, so this is not a discard - edgeProfile simply
    // returns zero out here and every fragment falls to the outer branch.
    if (abs(along) >= 1.0 + WARP_BAND) {
        discard;
    }

    float edge = edgeProfile(along, across >= 0.0 ? 1.0 : -1.0);
    // Signed distance to the silhouette in normalised units: positive inside.
    float d = edge - abs(across);
    // How far past a tip we are, along the rip. Zero everywhere the lens still
    // has width, so it only ever affects the two caps.
    float beyondTip = max(0.0, abs(along) - 1.0);
    // ...and the same in blocks, which is what the glow falloffs want, so a
    // big rip and a small one get proportionate lips rather than identical ones.
    float dBlocks = d * TearSize.y * openAcross;
    float openBlocks = max(TearSize.y * openAcross, 0.001);

    float t = animTime();
    vec2 screenUV = gl_FragCoord.xy / ScreenSize;

    vec3 color;
    float alpha;

    if (d > 0.0) {
        // Inside the wound. Near-black, churning slowly, with a few amethyst
        // filaments drawn across it - enough structure that the hole reads as
        // deep rather than as a flat black decal.
        float churn = fbm(vec2(along * 2.6 - t * 0.10, across * 1.9 + t * 0.07));
        float filament = pow(fbm(vec2(along * 7.0 + t * 0.22, across * 3.0 - t * 0.13)), 3.0);
        vec3 interior = mix(VOID_DEEP, VOID_CHURN, churn * 0.55) + RIM_COLOR * filament * 0.9;

        // The lip: a soft halo standing off the edge plus a bright core line
        // right on it, both measured inward from the silhouette.
        float pulse = 0.80 + 0.20 * sin(t * 2.1 + along * 4.0 + Seed);
        float halo = exp(-dBlocks / (openBlocks * 0.55)) * pulse;
        float core = exp(-dBlocks / (openBlocks * 0.14)) * pulse;

        color = interior + RIM_COLOR * halo * 0.75 + RIM_LIGHT * core * 1.15;
        alpha = 1.0;
    } else {
        // Outside it. Space buckling around the tear, strongest at the lip and
        // gone within a band; nothing beyond that, so the effect has no edge of
        // its own to give itself away.
        //
        // Distance to the silhouette itself, not merely across the rip: past a
        // tip the nearest part of the wound *is* the tip, so the band has to
        // reckon with the along-axis gap as well or it runs off both ends as a
        // hairline. With it, the band closes round the tips as a cap.
        float dOut = -length(vec2(beyondTip, d));
        float outer = exp(dOut / (WARP_BAND * 0.45));
        if (outer < 0.004) {
            discard;
        }
        // Push the sample away from the rip, along the aperture axis: the
        // picture is dragged apart at the wound instead of sliding sideways.
        vec2 dir = normalize(vec2(n.x * 0.35, across >= 0.0 ? 1.0 : -1.0));
        float ripple = 0.75 + 0.25 * sin(along * 8.0 - t * 2.3 + Seed);

        vec3 veil;
        if (SceneReady > 0.5) {
            vec2 offset = dir * (outer * ripple * warpAmp) / ScreenSize;
            veil = textureLod(SceneSampler, clamp(screenUV + offset, vec2(0.001), vec2(0.999)), 0.0).rgb;
        } else {
            // No scene to bend: a violet haze rather than a hard-edged plate.
            veil = mix(VOID_DEEP, VOID_CHURN, fbm(vec2(along * 3.0 + t * 0.1, across * 2.0)));
        }
        float glow = exp(dOut / (WARP_BAND * 0.16));
        color = veil + RIM_COLOR * glow * 0.55;
        // Opacity must not outrun the displacement, or wherever it saturates
        // becomes a hard line - the very thing the band exists to avoid.
        alpha = clamp(max(outer * 0.85, glow * 0.9), 0.0, 1.0);
    }

    fragColor = vec4(color, alpha);
}
