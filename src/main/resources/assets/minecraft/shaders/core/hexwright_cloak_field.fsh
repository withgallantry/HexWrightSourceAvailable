#version 150

// Cloak field - a dome that veils what stands under it: from afar the dome
// looks clear and what it holds is faded almost away. Up close it turns into a
// shield.
//
// THE CLOAK. There is no picture of what is really behind the dome: the thing
// being hidden is already in the frame, in exactly the pixels the dome covers.
// So the dome doesn't try to replace it. It bends the real scene under it with
// a slow refractive warp and, using the depth buffer to tell the dome's
// contents from the world seen through it, lets the world through nearly as it
// is while draining the contents and fading them towards the tone of the scene
// just beyond the dome's edges - heat haze with a ghost of a building in it.
// That tone comes from a line across the dome's silhouette (the dome sliced by
// the plane through the eye, the pixel's ray and the camera's right vector);
// the two ends of that line are found analytically as tangents to the sphere,
// and the pixel blends a broad average of what lies just past each end.
// Working across rather than radially keeps the horizon and the ground line
// running level through the dome. The average is wide on purpose: copying
// texture in from the edges is what smeared.
//
// The dome is half an ellipsoid - Radius across, Radius / Stretch tall - so a
// tall field needn't be as wide as it is high. All the tangent maths is done in
// the space where it is a sphere of Radius (world up squashed by Stretch about
// the centre). That map is affine, and affine maps keep lines, planes and
// tangency, so the edges found there map straight back to the real dome's.
//
// THE SHIELD. The edge tone only works while there is scene beside the dome;
// once the dome approaches the width of the screen there is none, and it would
// fade into whatever is at the screen border - often the hidden structure
// itself. So as the dome fills the view (Proximity, worked out on the CPU from
// its angular size) the cloak gives way to what it can honestly be at that
// range: a tinted, gently bent shell over what is really there, with a slow
// sweep of light climbing it and a brighter rim. The patterns written on the shield are not drawn here but as
// geometry over the dome (CloakFieldWard): a shield is what fills the screen,
// so everything it costs per pixel is paid on every pixel, and it reads the
// scene once.
//
// RIPPLES. Up to four per dome, each an impact point on the surface and its age
// through a ripple's life. A ripple is a packet of a few waves running out
// across the surface from the point, and what shows of it is how it bends the
// view behind: by the slope of the waves, one way on a rising face and the
// other on a falling one, as light bends through a ripple on water - so the
// scene behind swims and magnifies rather than sliding aside. Where it bends
// hardest the colours part slightly. They show on the cloak too, more faintly
// - an arrow through a hidden dome gives it away.
//
// SWELLS. Now and then a long, soft wave rolls unprompted across the whole
// shield from a random point, one at a time, so a shield at rest still moves.
// Made the same way as a ripple, at a far longer wavelength and bending
// gently; the cloak carries none, since they would give it away.
//
// STARTUP. A field that has just gone up is drawn in from the ground: a wave
// of energy climbs the shell from the rim to the crown, the dome standing
// behind it and nothing yet above it, with fainter ripples trailing it up.
// Rise is how far through that the field is, 1 (or more) once it is done.
//
// From inside, the world out there is real and unhidden, so the shell only
// bends it, with the sweep and ripples laid lightly over the top. From
// underneath, where the eye sits inside the slice and there are no edges to
// take a tone from, the cloak is only drained, not faded.

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;

uniform mat4 ProjMat;
uniform vec2 ScreenSize;
uniform float GameTime;
uniform vec3 SphereCenter;
uniform float Radius;
uniform float Stretch;
uniform vec3 WorldUp;
uniform float Inside;
uniform float Seed;
uniform float DepthReady;
uniform float Proximity;
uniform float Rise;
// 1 under vanilla's Fast graphics: the shield leaves the view behind it
// unbent, since up close the dome covers the whole screen.
uniform float Fast;
// xyz: impact point in view space. w: age as a fraction of the ripple's life,
// negative when the slot is empty.
uniform vec4 Ripple0;
uniform vec4 Ripple1;
uniform vec4 Ripple2;
uniform vec4 Ripple3;
// xyz: where the current swell started, in view space. w: age as a fraction of
// its life, negative between swells.
uniform vec4 Swell;

in vec3 viewPos;
in vec2 surfaceUV;

out vec4 fragColor;

const vec3 TINT = vec3(0.70, 0.62, 1.0);

// How far the cloak fades the dome's contents towards the surroundings' tone,
// and the world seen through it. Short of 1: the field is meant to be
// spottable by someone looking for it.
const float CONTENTS_FADE = 0.88;
const float THROUGH_FADE = 0.08;
// Share of the contents' colour drained before they are faded.
const float DRAIN = 0.4;
// Height above the base, in blocks, below which the scene inside the dome is
// taken as the ground it stands on and shown as the world.
const float GROUND_ALLOWANCE = 0.5;
// How far, in pixels, the scene behind is bent: strongly by the cloak, where
// the warp is most of what breaks up the shape inside, and gently by the
// shield and from inside.
const float CLOAK_WARP_PX = 6.0;
const float WARP_PX = 3.5;
// Gap between the silhouette and the nearest tone sample, so antialiased edge
// pixels that already contain some of the dome's contents are skipped.
const float RIM_MARGIN_PX = 4.0;
// The band beyond each edge the tone is averaged over: out from the rim, and
// along it either side of the pixel's own line. Few taps, dithered per pixel:
// the grain that leaves is lost under the warp, where more taps cost a read of
// the scene and one of its depth each.
const float TONE_REACH_PX = 40.0;
const float TONE_SPREAD_PX = 24.0;
const int TONE_TAPS = 4;
// Where to look when every tap landed on something in front of the dome.
const float FALLBACK_STEP_PX = 24.0;

// The shield's sweep: a band of light that climbs the dome every SWEEP_SECONDS.
const float SWEEP_SECONDS = 7.0;
const float SWEEP_WIDTH = 0.06;
const float SWEEP_GLOW = 0.08;
const vec3 SLATE_CORE = vec3(0.95, 0.84, 1.0);
// A ripple, in blocks: how far it has run by the end of its life - quickly at
// first, slowing as it goes - the length of its waves, and the spread of the
// packet at the start and end. How far its steepest slope bends the view, and
// how much of that the colours part by.
const float RIPPLE_REACH = 8.0;
const float RIPPLE_WAVELENGTH = 1.1;
const float RIPPLE_SPREAD_START = 0.8;
const float RIPPLE_SPREAD_END = 2.2;
const float RIPPLE_GLOW = 0.3;
const float RIPPLE_PUSH_PX = 14.0;
const float CHROMA_SHARE = 0.3;
// A swell: its wavelength as a share of the dome's radius, never under
// SWELL_MIN_WAVELENGTH blocks, with a packet of about one and a half waves.
// It runs twice the dome's larger half-size, far enough to cross it from any
// start.
const float SWELL_WAVELENGTH = 0.3;
const float SWELL_MIN_WAVELENGTH = 3.0;
const float SWELL_SPREAD = 0.8;
const float SWELL_GLOW = 0.05;
const float SWELL_PUSH_PX = 7.0;

// The startup wave, in blocks: the crisp line of its front, the glow it
// trails down the shell and the short one it throws ahead, how far the front
// rolls up and down around the dome, and how far apart the ripples following
// it are. Painted on rather than added, so it reads as purple over a bright
// sky instead of burning to white - and kept thin, because from the foot of a
// dome the whole view is the few blocks nearest the ground and a broad band
// would wash it all out at once.
const float RISE_LINE = 0.12;
const float RISE_TRAIL = 0.8;
const float RISE_LEAD = 0.25;
const float RISE_ROLL = 0.5;
const float RISE_ECHO = 1.6;
const float RISE_ECHO_WIDTH = 0.3;
const float RISE_PUSH_PX = 10.0;

// Share of the shield's colour replaced by the field's purple, and the scale
// that brings TINT to unit brightness so tinting doesn't darken.
const float SHIELD_TINT = 0.3;
const float TINT_LUMA_SCALE = 1.46;
// The line round the base, where the dome meets the ground: core thickness and
// glow falloff in blocks. The core never thins below a pixel and a half, so it
// still reads from far off. The far side's line, seen through the dome, is
// fainter.
const vec3 BASE_COLOR = vec3(0.62, 0.32, 1.0);
const float BASE_CORE = 0.6;
const float BASE_LINE_BLOCKS = 0.12;
const float BASE_GLOW_BLOCKS = 0.5;
const float BASE_GLOW = 0.25;
const float BASE_FAR_SHARE = 0.5;
// How far in front of the far base the scene may be, in blocks, before it
// counts as standing in front of it rather than being the ground it rests on.
const float BASE_OCCLUSION_SLACK = 0.5;

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

float luma(vec3 c) {
    return dot(c, vec3(0.3, 0.59, 0.11));
}

vec3 scene(vec2 uv) {
    return textureLod(SceneSampler, clamp(uv, vec2(0.001), vec2(0.999)), 0.0).rgb;
}

// Whether the scene at uv is nearer than depth - something between the eye and
// the dome, which would be dragged across the shell if it were sampled.
bool occludes(vec2 uv, float depth) {
    return DepthReady > 0.5
        && textureLod(DepthSampler, clamp(uv, vec2(0.001), vec2(0.999)), 0.0).r < depth;
}

vec3 toSphere(vec3 v) {
    return v + WorldUp * (dot(v, WorldUp) * (Stretch - 1.0));
}

vec3 fromSphere(vec3 v) {
    return v + WorldUp * (dot(v, WorldUp) * (1.0 / Stretch - 1.0));
}

vec2 toScreen(vec3 dir) {
    // At or behind the eye plane the projection flips through w <= 0; nudging
    // the direction just in front sends it far off-screen on the correct side
    // instead, and the clamp in scene() takes it from there.
    dir.z = min(dir.z, -1e-3);
    vec4 clip = ProjMat * vec4(dir, 1.0);
    return clip.xy / clip.w * 0.5 + 0.5;
}

vec2 wobble(vec2 uv, float t) {
    return vec2(fbm(uv * 7.0 + vec2(t * 0.05, Seed)),
                fbm(uv * 7.0 + vec2(Seed * 1.7, -t * 0.04))) - 0.5;
}

// The glow of the sweep passing up the shield, faded out towards the crown.
float sweep(float t) {
    float crown = 1.0 - smoothstep(0.6, 0.8, surfaceUV.y);
    float sweepAt = fract(t / SWEEP_SECONDS + Seed) * (1.0 + 2.0 * SWEEP_WIDTH) - SWEEP_WIDTH;
    float x = (surfaceUV.y - sweepAt) / SWEEP_WIDTH;
    return exp(-x * x) * crown;
}

// A packet of waves whose middle has run front blocks out from origin, at this
// fragment: adds pushPx times the waves' slope to push, along the way the
// surface runs away from origin as it lies on screen, and returns how high the
// crests stand here (0..1) for the light they carry. The slope is scaled so
// its steepest is about 1, whatever the wavelength.
float wavePacket(vec3 origin, float front, float wavelength, float spread, float pushPx, inout vec2 push) {
    vec3 out3 = viewPos - origin;
    float distance = length(out3);
    float x = distance - front;
    float envelope = exp(-x * x / (spread * spread));
    if (envelope < 0.01) {
        return 0.0;
    }
    float k = 6.2831853 / wavelength;
    // Pushed further than about a sixth of a wave's length on screen, the view
    // folds over itself and a far-off dome's ripple turns to noise.
    float wavePx = wavelength * ProjMat[1][1] * ScreenSize.y / (2.0 * max(-viewPos.z, 0.1));
    pushPx = min(pushPx, wavePx * 0.16);
    float slope = (-sin(k * x) - cos(k * x) * 2.0 * x / (spread * spread * k)) * envelope;
    vec2 onScreen = (toScreen(viewPos + out3 / max(distance, 1e-3) * 0.1) - toScreen(viewPos)) * ScreenSize;
    push += onScreen / max(length(onScreen), 1e-4) * slope * pushPx;
    return max(cos(k * x), 0.0) * envelope;
}

// One impact's ripple at this point: fast off the mark and slowing, its packet
// spreading and dying away as it goes.
float ripple(vec4 r, inout vec2 push) {
    if (r.w < 0.0 || r.w >= 1.0) {
        return 0.0;
    }
    float fade = (1.0 - r.w) * (1.0 - r.w);
    float travelled = 1.0 - fade;
    float spread = mix(RIPPLE_SPREAD_START, RIPPLE_SPREAD_END, r.w);
    return wavePacket(r.xyz, RIPPLE_REACH * travelled, RIPPLE_WAVELENGTH, spread,
        RIPPLE_PUSH_PX * fade, push) * fade;
}

// The swell rolling across the shield, if there is one, scaled by amount -
// how much of the shield is showing. Eased in and out over its life, so it
// rises out of the shell and settles back rather than appearing.
float swell(float amount, inout vec2 push) {
    if (Swell.w < 0.0 || Swell.w >= 1.0 || amount <= 0.0) {
        return 0.0;
    }
    float height = Radius / Stretch;
    float wavelength = max(SWELL_MIN_WAVELENGTH, Radius * SWELL_WAVELENGTH);
    float strength = sin(3.1415927 * Swell.w) * amount;
    return wavePacket(Swell.xyz, 2.0 * max(Radius, height) * Swell.w, wavelength,
        wavelength * SWELL_SPREAD, SWELL_PUSH_PX * strength, push) * strength;
}

// The scene at uv, its colours parted by split pixels - red one way, blue the
// other - where a ripple bends it hardest. Only there, so the extra reads are
// only paid on the few pixels a ripple is crossing.
vec3 parted(vec2 uv, vec2 split, vec2 px) {
    if (dot(split, split) < 0.25) {
        return scene(uv);
    }
    return vec3(scene(uv + split * px).r, scene(uv).g, scene(uv - split * px).b);
}

// Where to read the real scene for this pixel, bent by the warp. Where the bent
// sample lands on something in front of the dome it is dropped, or that
// thing's edges would be dragged across the shell.
vec2 refracted(vec2 uv0, vec2 px, float t, float depth, float warpPx) {
    vec2 uv = uv0 + wobble(uv0, t) * 2.0 * warpPx * px;
    return occludes(uv, depth) ? uv0 : uv;
}

// The view-space point the depth buffer holds at uv.
vec3 sceneViewPos(vec2 uv) {
    float ndc = textureLod(DepthSampler, uv, 0.0).r * 2.0 - 1.0;
    float z = -ProjMat[3][2] / (ndc + ProjMat[2][2]);
    vec2 xy = (uv * 2.0 - 1.0) * -z / vec2(ProjMat[0][0], ProjMat[1][1]);
    return vec3(xy, z);
}

// How much of what the scene shows at this pixel is the dome's contents rather
// than the world seen through it: inside the ellipsoid, and not the ground it
// rests on. The ground inside counts as the world - it is not what is being
// hidden, and fading it would make a flat patch of the dome's footprint from
// above. Ground is told by facing up as well as lying low, so the foot of a
// wall isn't mistaken for it.
float contentsAt(vec3 at, vec3 facing) {
    if (DepthReady < 0.5) {
        return 1.0;
    }
    vec3 rel = at - SphereCenter;
    float inside = smoothstep(-0.5, 0.5, Radius - length(toSphere(rel)));
    float ground = smoothstep(0.8, 0.95, abs(dot(facing, WorldUp)))
        * (1.0 - smoothstep(GROUND_ALLOWANCE, GROUND_ALLOWANCE + 0.5, dot(rel, WorldUp)));
    return inside * (1.0 - ground);
}

// The shield: the scene behind, gently bent, milkier and tinted by the field's
// colour at the same brightness, so the purple shows over dark ground as well
// as light sky without lifting either.
vec3 shield(vec2 uv, vec2 split, vec2 px, float t, float depth) {
    vec2 through = Fast > 0.5 ? uv : refracted(uv, px, t, depth, WARP_PX);
    vec3 seen = parted(through, split, px);
    vec3 milky = mix(seen, vec3(luma(seen)), 0.25) * 0.9 + TINT * 0.1;
    return mix(milky, luma(milky) * TINT * TINT_LUMA_SCALE, SHIELD_TINT);
}

// The tone of the scene past one end of the pixel's line: a broad average over
// a band just beyond the silhouette, dithered per pixel so the taps blend into
// one colour rather than showing as copies of whatever they landed on. Taps on
// something in front of the dome are left out.
vec3 edgeTone(vec2 edge, vec2 outward, vec2 row, vec2 px, float depth) {
    vec3 sum = vec3(0.0);
    float count = 0.0;
    float dither = hash(gl_FragCoord.xy + outward * 17.0);
    for (int k = 0; k < TONE_TAPS; k++) {
        float reach = (float(k) + dither) / float(TONE_TAPS);
        float along = fract(dither * 7.13 + float(k) * 0.618034) * 2.0 - 1.0;
        vec2 uv = edge + (outward * (RIM_MARGIN_PX + TONE_REACH_PX * reach) + row * along * TONE_SPREAD_PX) * px;
        if (!occludes(uv, depth)) {
            sum += scene(uv);
            count += 1.0;
        }
    }
    return count > 0.0 ? sum / count : scene(edge + outward * (RIM_MARGIN_PX + FALLBACK_STEP_PX) * px);
}

// The tone the cloak fades towards, with w negative when the eye is inside
// this pixel's slice and there are no edges to take it from.
vec4 surroundings(vec2 uv0, vec2 px, vec3 d, float depth) {
    // The slice: the plane through the eye containing this ray and the
    // camera's right vector, cut through the sphere. Everything from here is
    // relative to the eye, in sphere space.
    vec3 toCenterFull = toSphere(SphereCenter);
    vec3 n = normalize(cross(toSphere(vec3(1.0, 0.0, 0.0)), toSphere(d)));
    float h = dot(toCenterFull, n);
    vec3 sliceCenter = toCenterFull - n * h;
    float sliceRadius = sqrt(max(Radius * Radius - h * h, 0.0));
    float sliceDistance = length(sliceCenter);
    if (sliceDistance <= sliceRadius) {
        return vec4(-1.0);
    }

    // The two rays from the eye that graze the slice's circle - the ends of
    // this pixel's line across the silhouette.
    vec3 toCenter = sliceCenter / sliceDistance;
    float halfAngle = asin(sliceRadius / sliceDistance);
    vec3 sideways = normalize(cross(n, toCenter));
    vec2 edgeA = toScreen(fromSphere(toCenter * cos(halfAngle) - sideways * sin(halfAngle)));
    vec2 edgeB = toScreen(fromSphere(toCenter * cos(halfAngle) + sideways * sin(halfAngle)));

    vec2 spanPx = (edgeB - edgeA) * ScreenSize;
    float spanLength = max(length(spanPx), 1.0);
    vec2 across = spanPx / spanLength;
    vec2 row = vec2(-across.y, across.x);
    float fromA = clamp(dot((uv0 - edgeA) * ScreenSize, across), 0.0, spanLength);

    vec3 toneA = edgeTone(edgeA, -across, row, px, depth);
    vec3 toneB = edgeTone(edgeB, across, row, px, depth);
    return vec4(mix(toneA, toneB, smoothstep(0.0, 1.0, fromA / spanLength)), 1.0);
}

// The world behind the dome shows through almost untouched, so the dome itself
// reads as clear; only its contents are drained and faded away. The real scene
// is read where ripples have pushed it, the tone from the unpushed edges.
vec3 cloak(vec2 uv0, vec2 pushed, vec2 px, float t, vec3 d, float depth, float contents) {
    vec3 real = scene(refracted(pushed, px, t, depth, CLOAK_WARP_PX));
    vec3 drained = mix(real, vec3(luma(real)), DRAIN * contents);
    vec4 tone = surroundings(uv0, px, d, depth);
    if (tone.w < 0.0) {
        return drained;
    }
    return mix(drained, tone.rgb, mix(THROUGH_FADE, CONTENTS_FADE, contents));
}

// Blocks per pixel at this view-space distance in front of the eye.
float footprint(float ahead) {
    return 2.0 * ahead / (ProjMat[1][1] * ScreenSize.y);
}

// The line where the dome meets the plane it stands on, at a point heightAbove
// blocks up the shell: a crisp core and a soft glow over it.
float baseLine(float heightAbove, float aaBlocks) {
    float h = abs(heightAbove);
    float core = 1.0 - smoothstep(0.0, max(BASE_LINE_BLOCKS, aaBlocks * 1.5), h);
    return core * BASE_CORE + exp(-h / BASE_GLOW_BLOCKS) * BASE_GLOW;
}

// The base line on the far side of the dome, seen through the near shell: where
// this pixel's ray leaves the ellipsoid, if that is at the base and nothing
// inside the dome - the scene's own point at this pixel, sceneAt - stands in
// front of it.
float farBaseLine(vec3 d, vec3 sceneAt) {
    vec3 ray = toSphere(d);
    vec3 c = toSphere(SphereCenter);
    float a = dot(ray, ray);
    float b = dot(ray, c);
    float disc = b * b - a * (dot(c, c) - Radius * Radius);
    if (disc < 0.0) {
        return 0.0;
    }
    float exit = (b + sqrt(disc)) / a;
    vec3 exitPoint = d * exit;
    float ahead = -exitPoint.z;
    if (DepthReady > 0.5 && -sceneAt.z < ahead - BASE_OCCLUSION_SLACK) {
        return 0.0;
    }
    return baseLine(dot(exitPoint - SphereCenter, WorldUp), footprint(ahead));
}

// How far above the startup wave's front this point is, in blocks: negative
// behind it, where the dome already stands, positive ahead of it. The front
// starts just under the base and finishes far enough past the crown for its
// trailing ripples to clear it too.
float riseOffset(float heightAbove, float t) {
    float height = Radius / Stretch;
    float front = mix(-1.0, height + 2.5 * RISE_ECHO, smoothstep(0.0, 1.0, Rise));
    float around = surfaceUV.x * 6.2831853;
    front += RISE_ROLL * (0.6 * sin(around * 7.0 + t * 5.0) + 0.4 * sin(around * 13.0 - t * 7.3 + Seed));
    return heightAbove - front;
}

// The wave's glow at dh blocks from its front: the halo round the front, and
// two fainter ripples following it up.
float riseGlow(float dh) {
    float halo = exp(-max(dh, 0.0) / RISE_LEAD - max(-dh, 0.0) / RISE_TRAIL);
    float echo1 = (dh + RISE_ECHO) / RISE_ECHO_WIDTH;
    float echo2 = (dh + 2.0 * RISE_ECHO) / RISE_ECHO_WIDTH;
    return halo * 0.5 + 0.3 * exp(-echo1 * echo1) + 0.15 * exp(-echo2 * echo2);
}

// Lays the startup over the finished dome: the plain scene where the wave has
// not reached yet, then the glow and the front's bright line painted over.
vec3 rising(vec3 dome, vec2 uv, float reveal, float glow, float line) {
    vec3 color = mix(scene(uv), dome, reveal);
    color = mix(color, BASE_COLOR, clamp(glow, 0.0, 1.0));
    return mix(color, SLATE_CORE, line * 0.9);
}

void main() {
    // Derivatives first, while control flow is still uniform.
    float heightAbove = dot(viewPos - SphereCenter, WorldUp);
    float aaBlocks = fwidth(heightAbove);
    float nearBase = baseLine(heightAbove, aaBlocks);

    vec2 px = 1.0 / ScreenSize;
    vec2 uv0 = gl_FragCoord.xy * px;
    // GameTime is in days; this is seconds.
    float t = GameTime * 1200.0;
    float depth = gl_FragCoord.z;

    // What the scene holds behind this pixel, and which way it faces - the
    // facing from derivatives, so also taken here. One read serves both the
    // cloak's test for the dome's contents and the far base line's occlusion.
    bool outside = Inside < 0.5;
    vec3 sceneAt = vec3(0.0, 0.0, -1e6);
    vec3 sceneFacing = WorldUp;
    if (outside && DepthReady > 0.5) {
        sceneAt = sceneViewPos(uv0);
        sceneFacing = normalize(cross(dFdx(sceneAt), dFdy(sceneAt)) + vec3(0.0, 0.0, 1e-6));
    }

    float reveal = 1.0;
    float glow = 0.0;
    float line = 0.0;
    vec2 risen = uv0;
    if (Rise < 1.0) {
        float dh = riseOffset(heightAbove, t);
        // Ahead of the wave there is no dome yet, and the scene is already in the frame.
        if (dh > 8.0 * RISE_LEAD) {
            discard;
        }
        float lineWidth = max(RISE_LINE, aaBlocks * 1.5);
        reveal = 1.0 - smoothstep(-lineWidth, lineWidth, dh);
        glow = riseGlow(dh);
        line = 1.0 - smoothstep(0.0, lineWidth, abs(dh));
        risen = uv0 + wobble(uv0 * 3.0, t * 6.0) * 4.0 * glow * RISE_PUSH_PX * px;
        risen = occludes(risen, depth) ? uv0 : risen;
    }

    vec3 d = normalize(viewPos);
    // The ellipsoid's gradient: squashing twice is the same as dividing by the
    // squared semi-axes, up to a scale the normalize removes.
    vec3 normal = normalize(toSphere(toSphere(viewPos - SphereCenter)));
    float facing = dot(normal, -d);

    vec2 push = vec2(0.0);
    float rings = ripple(Ripple0, push) + ripple(Ripple1, push)
        + ripple(Ripple2, push) + ripple(Ripple3, push);
    float swelling = swell(outside ? Proximity : 1.0, push);
    vec2 pushed = uv0 + push * px;
    vec2 split = push * CHROMA_SHARE;
    float rim = pow(1.0 - abs(facing), 3.0);

    if (!outside) {
        // From inside the shell covers the whole screen at every distance, so
        // Fast graphics leaves the view unbent; the tint and ripples still say
        // where it is.
        vec2 through = Fast > 0.5 ? pushed : refracted(pushed, px, t, depth, WARP_PX);
        vec3 color = parted(through, split, px)
            + TINT * (0.025 + rim * 0.06 + rings * RIPPLE_GLOW * 0.6 + swelling * SWELL_GLOW
                + sweep(t) * SWEEP_GLOW * 0.5)
            + BASE_COLOR * nearBase;
        if (Rise < 1.0) {
            color = rising(color, risen, reveal, glow, line);
        }
        fragColor = vec4(color, 1.0);
        return;
    }
    // Both shells are drawn; from outside only the near one is the dome.
    if (facing < 0.0) {
        discard;
    }

    vec3 far = Proximity < 1.0 ? cloak(uv0, pushed, px, t, d, depth, contentsAt(sceneAt, sceneFacing)) : vec3(0.0);
    vec3 near = Proximity > 0.0 ? shield(pushed, split, px, t, depth) : vec3(0.0);
    vec3 color = mix(far, near, Proximity);

    // The cloak's own rim stays faint so it isn't an outline from afar; the
    // shield's sweep and brighter rim arrive with it.
    float shimmer = 0.6 + 0.4 * sin(t * 1.3 + dot(uv0, vec2(40.0)) + Seed);
    color += TINT * pow(1.0 - facing, 5.0) * shimmer * 0.05;
    color += Proximity * TINT * rim * 0.22;
    color += TINT * (rings * RIPPLE_GLOW * mix(0.5, 1.0, Proximity) + swelling * SWELL_GLOW);
    color += BASE_COLOR * (nearBase + farBaseLine(d, sceneAt) * BASE_FAR_SHARE);
    // Only worked out where it shows: the cloak from afar carries none.
    if (Proximity > 0.0) {
        color += TINT * sweep(t) * SWEEP_GLOW * Proximity;
    }
    if (Rise < 1.0) {
        color = rising(color, risen, reveal, glow, line);
    }

    fragColor = vec4(color, 1.0);
}
