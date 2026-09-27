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
// range: frosted glass over what is really there, a ward of Hex Casting
// patterns being written, cast and fading across its surface, and a brighter
// rim. Everything it samples is the pixel's own neighbourhood, so it works at
// any distance.
//
// RIPPLES. Up to four per dome, each an impact point on the surface and its age
// through a ripple's life. A ring runs out across the surface from the point,
// brightening the shell and pushing the view behind it aside as it passes.
// They show on the cloak too, more faintly - an arrow through a hidden dome
// gives it away.
//
// STARTUP. A field that has just gone up is drawn in from the ground: a wave
// of energy climbs the shell from the rim to the crown, the dome standing
// behind it and nothing yet above it, with fainter ripples trailing it up.
// Rise is how far through that the field is, 1 (or more) once it is done.
//
// From inside, the world out there is real and unhidden, so the shell only
// bends it, with the ward and ripples laid lightly over the top. From
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
// 1 under vanilla's Fast graphics: the shield frosts with fewer taps and writes
// fewer glyphs at once, since up close the dome covers the whole screen and
// those are what its cost is made of.
uniform float Fast;
// xyz: impact point in view space. w: age as a fraction of the ripple's life,
// negative when the slot is empty.
uniform vec4 Ripple0;
uniform vec4 Ripple1;
uniform vec4 Ripple2;
uniform vec4 Ripple3;

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
// along it either side of the pixel's own line.
const float TONE_REACH_PX = 40.0;
const float TONE_SPREAD_PX = 24.0;
const int TONE_TAPS = 8;
// Where to look when every tap landed on something in front of the dome.
const float FALLBACK_STEP_PX = 24.0;

// Radius of the frost blur. Enough that the shapes behind survive and the
// detail doesn't.
const float FROST_PX = 9.0;
const int FROST_TAPS = 8;
const int FROST_TAPS_FAST = 4;

// The shield's ward. Laid out on Hex Casting's own drawing grid - dots in a
// triangular lattice WARD_SPACING blocks apart - and snapped to TEXELS per
// block, a block texture's resolution, so its strokes step like pixel art
// rather than antialiasing like vector lines.
const float WARD_SPACING = 1.0;
const float TEXELS = 16.0;
// The glyphs written across it: one to each GLYPH_CELL-block patch of the
// surface, each taking about GLYPH_SECONDS from its first stroke to fading
// out, with up to GLYPH_MAX_STROKES strokes. Only a share of the patches are
// written in at any moment.
const float GLYPH_CELL = 4.0;
const float GLYPH_SECONDS = 6.0;
const int GLYPH_MAX_STROKES = 9;
const float GLYPH_BUSY = 0.8;
// Fast graphics writes a quieter ward of shorter patterns rather than a
// cropped one: idle patches and short walks cost little, where checking fewer
// neighbouring patches would cut strokes off at the patch lines.
const int GLYPH_MAX_STROKES_FAST = 6;
const float GLYPH_BUSY_FAST = 0.6;
// Share of a glyph's life spent being drawn, and the flash when it is done -
// the moment a pattern is cast.
const float GLYPH_DRAW = 0.45;
const float GLYPH_FLASH = 0.12;
// Stroke widths in texels: the bright core, the coloured body and the glow
// round it. Strokes wobble either side of the grid line by up to STROKE_WOBBLE
// texels, the way a slate's patterns do.
const float STROKE_CORE = 0.5;
const float STROKE_BODY = 1.5;
const float STROKE_HALO = 4.5;
const float STROKE_WOBBLE = 0.6;
// Hex Casting's colours: the purple slates draw their patterns in, its bright
// inner core, the blue of a pattern's starting dot, and the grid dots. The
// grid only shows round what is being written, as it does under a staff.
const vec3 SLATE_PURPLE = vec3(0.68, 0.42, 0.96);
const vec3 SLATE_DEEP = vec3(0.42, 0.2, 0.8);
const vec3 SLATE_CORE = vec3(0.95, 0.84, 1.0);
const vec3 START_DOT = vec3(0.36, 0.48, 0.84);
const vec3 GRID_DOT = vec3(0.86, 0.78, 0.96);
const float GRID_WAKE = 2.0;
const float WARD_GLOW = 1.0;
// How far a ripple's ring has run across the surface by the end of its life,
// in blocks, and how thick it is.
const float RIPPLE_REACH = 7.0;
const float RIPPLE_WIDTH = 0.7;
const float RIPPLE_GLOW = 0.45;
const float RIPPLE_PUSH_PX = 10.0;

// The startup wave, in blocks: the crisp line of its front, the glow it
// trails down the shell and the short one it throws ahead, how far the front
// rolls up and down around the dome, and how far apart the ripples following
// it are. Painted on like the ward rather than added, so it reads as purple
// over a bright sky instead of burning to white - and kept thin, because from
// the foot of a dome the whole view is the few blocks nearest the ground and a
// broad band would wash it all out at once.
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

// The dome's surface unrolled flat, in blocks: round the base in x, up the
// curve in y. The distance round is rounded to whole grid spacings, so the
// lattice closes on itself at the seam; x is periodic in the returned z.
// Cells narrow towards the crown, where round-the-dome distances shrink, so
// the ward fades out before it gets there.
vec3 wardSpace(vec2 uv) {
    float height = Radius / Stretch;
    float quarterArc = 3.1415927 * (3.0 * (Radius + height)
        - sqrt((3.0 * Radius + height) * (Radius + 3.0 * height))) / 4.0;
    float around = max(6.0, floor(6.2831853 * Radius / WARD_SPACING + 0.5)) * WARD_SPACING;
    return vec3(uv.x * around, uv.y * quarterArc, around);
}

vec2 wrapped(vec2 d, float period) {
    d.x -= period * floor(d.x / period + 0.5);
    return d;
}

// Distance from q to the nearest dot of the triangular grid: the union of two
// rectangular grids, one offset by half a cell in both directions.
float gridDotDistance(vec2 q) {
    vec2 cell = vec2(WARD_SPACING, WARD_SPACING * 1.7320508);
    vec2 a = q - floor(q / cell + 0.5) * cell;
    vec2 shifted = q - cell * 0.5;
    vec2 b = shifted - floor(shifted / cell + 0.5) * cell;
    return sqrt(min(dot(a, a), dot(b, b)));
}

// One glyph's contribution at q, accumulated into core/body/halo (x, y, z) and
// the grid dots' wake and the blue starting dot (w, and returned). A glyph is
// a Hex Casting pattern: a walk along the grid from a dot near its patch's
// centre, turning by one of the five angles a pattern may take (never straight
// back), drawn stroke by stroke, flashing as it completes and fading. Its path
// is rebuilt here from hashes of its patch and cycle, so nothing about it lives
// on the CPU.
float glyph(vec2 q, vec2 cell, vec2 cellSize, float period, float t, inout vec4 lit) {
    vec2 slot = vec2(cell.x * 7.31 + Seed, cell.y * 3.17);
    float lifetime = GLYPH_SECONDS * mix(0.8, 1.3, hash(slot));
    float cycle = t / lifetime + hash(slot + 0.5);
    float epoch = floor(cycle);
    float life = cycle - epoch;
    vec2 key = slot + vec2(0.0, epoch * 1.91);
    bool fast = Fast > 0.5;
    if (hash(key + 0.13) > (fast ? GLYPH_BUSY_FAST : GLYPH_BUSY)) {
        return 0.0;
    }

    float texel = 1.0 / TEXELS;
    int maxStrokes = fast ? GLYPH_MAX_STROKES_FAST : GLYPH_MAX_STROKES;
    float strokes = 4.0 + floor(hash(key + 0.31) * float(maxStrokes - 3));
    float drawn = clamp(life / GLYPH_DRAW, 0.0, 1.0) * strokes;
    float cast = life - GLYPH_DRAW;
    float flash = cast > 0.0 ? 1.0 + 1.5 * (1.0 - smoothstep(0.0, GLYPH_FLASH, cast)) : 0.8;
    float fade = 1.0 - smoothstep(GLYPH_DRAW + GLYPH_FLASH, 1.0, life);

    // Start on the grid dot nearest a jittered point in the patch.
    vec2 around = (cell + 0.3 + 0.4 * vec2(hash(key + 0.53), hash(key + 0.61))) * cellSize;
    float row = floor(around.y / (WARD_SPACING * 0.8660254) + 0.5);
    float shift = mod(row, 2.0) * 0.5;
    vec2 at = vec2((floor(around.x / WARD_SPACING - shift + 0.5) + shift) * WARD_SPACING,
                   row * WARD_SPACING * 0.8660254);

    float reach = length(wrapped(q - at, period));
    // Nothing of this glyph can reach q: its strokes so far lie within their
    // own length of the start.
    float written = ceil(min(drawn, strokes)) * WARD_SPACING;
    if (reach > written + GRID_WAKE) {
        return 0.0;
    }
    lit.w = max(lit.w, (1.0 - step(2.0 * texel, reach)) * fade);

    float heading = floor(hash(key + 0.71) * 6.0);
    float nearest = 1e3;
    for (int k = 0; k < GLYPH_MAX_STROKES; k++) {
        if (float(k) >= min(strokes, ceil(drawn))) {
            break;
        }
        float turnRoll = hash(key + vec2(float(k) * 1.37, 0.91));
        heading += turnRoll < 0.3 ? 0.0 : turnRoll < 0.55 ? 1.0 : turnRoll < 0.8 ? 5.0 : turnRoll < 0.9 ? 2.0 : 4.0;
        float angle = heading * 1.0471976;
        vec2 stroke = vec2(cos(angle), sin(angle)) * WARD_SPACING;
        float share = clamp(drawn - float(k), 0.0, 1.0);

        vec2 rel = wrapped(q - at, period);
        float along = clamp(dot(rel, stroke) / dot(stroke, stroke), 0.0, share);
        vec2 side = rel - stroke * along;
        float off = length(side) / texel;
        // The wobble only moves the stroke by a texel or so; further out than
        // its glow it changes nothing, so it isn't worked out there.
        if (off < STROKE_HALO + STROKE_WOBBLE) {
            vec2 normal = vec2(-stroke.y, stroke.x) / WARD_SPACING;
            float wobble = sin(along * 6.0 + t * 1.3 + float(k) * 2.1) * sin(along * 3.1415927)
                * STROKE_WOBBLE * texel;
            off = length(side - normal * wobble) / texel;
        }
        nearest = min(nearest, off);
        if (share < 1.0) {
            // The pen: a bright point where the stroke is being drawn.
            lit.x = max(lit.x, (1.0 - step(2.0, length(rel - stroke * share) / texel)) * 1.5);
        }
        at += stroke;
    }
    float strength = flash * fade;
    lit.x = max(lit.x, (1.0 - step(STROKE_CORE, nearest)) * strength);
    lit.y = max(lit.y, (1.0 - step(STROKE_BODY, nearest)) * strength);
    lit.z = max(lit.z, (1.0 - smoothstep(STROKE_BODY, STROKE_HALO, nearest)) * strength);
    return (1.0 - smoothstep(0.0, GRID_WAKE, nearest * texel)) * fade;
}

// Paints the ward over color: the strokes are laid on like ink rather than
// added like light, or on a bright sky their purple would add up to white.
// What a cast's flash takes past full strength is added on top.
vec3 inscribe(vec3 color, vec4 w, float dots, float amount) {
    color = mix(color, SLATE_DEEP, clamp(w.z, 0.0, 1.0) * 0.35 * amount);
    color = mix(color, SLATE_PURPLE, clamp(w.y, 0.0, 1.0) * 0.9 * amount);
    color = mix(color, SLATE_CORE, clamp(w.x, 0.0, 1.0) * 0.85 * amount);
    color = mix(color, START_DOT, clamp(w.w, 0.0, 1.0) * amount);
    color = mix(color, GRID_DOT, clamp(dots, 0.0, 1.0) * 0.7 * amount);
    return color + SLATE_PURPLE * max(w.y - 1.0, 0.0) * 0.5 * amount * WARD_GLOW;
}

// The ward at q: core, body and halo of the strokes, then the starting dots,
// with the grid dots lit by the glyphs' wake or a ripple passing out through
// dots. Each patch's glyph can wander into its neighbours, so the eight around
// this one are looked at too.
vec4 ward(vec2 q, float period, float cellRows, float t, float rings, out float dots) {
    float columns = max(1.0, floor(period / GLYPH_CELL));
    vec2 cellSize = vec2(period / columns, GLYPH_CELL);
    vec2 home = floor(q / cellSize);
    vec4 lit = vec4(0.0);
    float wake = 0.0;
    for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
            vec2 cell = vec2(mod(home.x + float(dx), columns), home.y + float(dy));
            if (cell.y < 0.0 || cell.y >= cellRows) {
                continue;
            }
            wake = max(wake, glyph(q, cell, cellSize, period, t, lit));
        }
    }
    dots = (1.0 - step(1.5, gridDotDistance(q) * TEXELS)) * max(wake, rings);
    return lit;
}

// One ripple's ring at this point, and which way on screen it pushes.
float ripple(vec4 r, vec2 uv0, inout vec2 push) {
    if (r.w < 0.0 || r.w >= 1.0) {
        return 0.0;
    }
    float x = (length(viewPos - r.xyz) - r.w * RIPPLE_REACH) / RIPPLE_WIDTH;
    float fade = 1.0 - r.w;
    float ring = exp(-x * x) * fade * fade;
    vec2 away = (uv0 - toScreen(r.xyz)) * ScreenSize;
    push += away / max(length(away), 1.0) * ring;
    return ring;
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

// How much of what the scene shows at uv is the dome's contents rather than
// the world seen through it: inside the ellipsoid, and not the ground it rests
// on. The ground inside counts as the world - it is not what is being hidden,
// and fading it would make a flat patch of the dome's footprint from above.
// Ground is told by facing up as well as lying low, so the foot of a wall
// isn't mistaken for it.
float contentsAt(vec2 uv, vec2 px) {
    if (DepthReady < 0.5) {
        return 1.0;
    }
    vec3 at = sceneViewPos(uv);
    vec3 rel = at - SphereCenter;
    float inside = smoothstep(-0.5, 0.5, Radius - length(toSphere(rel)));
    vec3 facing = normalize(cross(sceneViewPos(uv + vec2(px.x, 0.0)) - at, sceneViewPos(uv + vec2(0.0, px.y)) - at));
    float ground = smoothstep(0.8, 0.95, abs(dot(facing, WorldUp)))
        * (1.0 - smoothstep(GROUND_ALLOWANCE, GROUND_ALLOWANCE + 0.5, dot(rel, WorldUp)));
    return inside * (1.0 - ground);
}

// Frosted glass: a small disc of the scene around this pixel, rotated per pixel
// so the taps read as grain rather than as copies. Taps on something in front
// of the shield are left out, or it would halo whatever stands before it.
// Fast graphics drops the warp as well: the blur alone breaks the shapes up.
vec3 frosted(vec2 uv0, vec2 px, float t, float depth) {
    bool fast = Fast > 0.5;
    vec2 centre = fast ? uv0 : uv0 + wobble(uv0, t) * 2.0 * WARP_PX * px;
    int taps = fast ? FROST_TAPS_FAST : FROST_TAPS;
    float spin = hash(gl_FragCoord.xy) * 6.2831853;
    vec3 sum = vec3(0.0);
    float count = 0.0;
    for (int k = 0; k < FROST_TAPS; k++) {
        if (k >= taps) {
            break;
        }
        float angle = spin + float(k) * 2.3999632;
        float reach = FROST_PX * sqrt((float(k) + 0.5) / float(taps));
        vec2 uv = centre + vec2(cos(angle), sin(angle)) * reach * px;
        if (!occludes(uv, depth)) {
            sum += scene(uv);
            count += 1.0;
        }
    }
    vec3 frost = count > 0.0 ? sum / count : scene(uv0);
    // Milkier than what's behind it, and a touch of the field's colour.
    vec3 milky = mix(frost, vec3(luma(frost)), 0.25) * 0.9 + TINT * 0.1;
    // Tinted by colour at the same brightness, so the purple shows over dark
    // ground as well as light sky without lifting either.
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
// reads as clear; only its contents are drained and faded away.
vec3 cloak(vec2 uv0, vec2 px, float t, vec3 d, float depth) {
    vec2 uv = refracted(uv0, px, t, depth, CLOAK_WARP_PX);
    vec3 real = scene(uv);
    float contents = contentsAt(uv, px);
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
// inside the dome stands in front of it.
float farBaseLine(vec3 d) {
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
    if (DepthReady > 0.5 && -sceneViewPos(gl_FragCoord.xy / ScreenSize).z < ahead - BASE_OCCLUSION_SLACK) {
        return 0.0;
    }
    return baseLine(dot(exitPoint - SphereCenter, WorldUp), footprint(ahead));
}

// The ward at this fragment, faded out towards the crown. The most expensive
// thing the shader does, so it is only called for fragments that are kept, and
// skipped where the crown has faded it to nothing.
vec4 wardHere(float t, float rings, out float dots) {
    dots = 0.0;
    float crown = 1.0 - smoothstep(0.6, 0.8, surfaceUV.y);
    if (crown <= 0.0) {
        return vec4(0.0);
    }
    vec3 space = wardSpace(surfaceUV);
    vec2 q = (floor(space.xy * TEXELS) + 0.5) / TEXELS;
    // Glyphs start no higher than the ward reaches before the crown fades it.
    float cellRows = ceil(wardSpace(vec2(0.0, 0.6)).y / GLYPH_CELL);
    vec4 w = ward(q, space.z, cellRows, t, rings, dots);
    dots *= crown;
    return w * crown;
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
    float rings = ripple(Ripple0, uv0, push) + ripple(Ripple1, uv0, push)
        + ripple(Ripple2, uv0, push) + ripple(Ripple3, uv0, push);
    vec2 pushed = uv0 + push * RIPPLE_PUSH_PX * px;
    float rim = pow(1.0 - abs(facing), 3.0);

    if (Inside > 0.5) {
        float dots;
        vec4 w = wardHere(t, rings, dots);
        // From inside the shell covers the whole screen at every distance, so
        // Fast graphics leaves the view unbent; the tint, ward and ripples still
        // say where it is.
        vec2 through = Fast > 0.5 ? pushed : refracted(pushed, px, t, depth, WARP_PX);
        vec3 color = scene(through)
            + TINT * (0.025 + rim * 0.06 + rings * RIPPLE_GLOW * 0.6)
            + BASE_COLOR * nearBase;
        color = inscribe(color, w, dots, 0.5);
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

    vec3 far = Proximity < 1.0 ? cloak(uv0, px, t, d, depth) : vec3(0.0);
    vec3 near = Proximity > 0.0 ? frosted(pushed, px, t, depth) : vec3(0.0);
    vec3 color = mix(far, near, Proximity);

    // The cloak's own rim stays faint so it isn't an outline from afar; the
    // shield's ward and brighter rim arrive with it.
    float shimmer = 0.6 + 0.4 * sin(t * 1.3 + dot(uv0, vec2(40.0)) + Seed);
    color += TINT * pow(1.0 - facing, 5.0) * shimmer * 0.05;
    color += Proximity * TINT * rim * 0.22;
    color += TINT * rings * RIPPLE_GLOW * mix(0.5, 1.0, Proximity);
    color += BASE_COLOR * (nearBase + farBaseLine(d) * BASE_FAR_SHARE);
    // Only worked out where it shows: the cloak from afar carries none.
    if (Proximity > 0.0) {
        float dots;
        vec4 w = wardHere(t, rings, dots);
        color = inscribe(color, w, dots, Proximity);
    }
    if (Rise < 1.0) {
        color = rising(color, risen, reveal, glow, line);
    }

    fragColor = vec4(color, 1.0);
}
