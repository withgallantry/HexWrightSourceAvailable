#version 150

// Controlled dust, formed - the compacted construct.
//
// Drawn on the back faces of a proxy box (front faces discard themselves, so winding and culling do
// not matter and a camera inside the box still sees it). Each pixel marches a ray through the
// Region's signed distance field - baked on the CPU into a 3D texture, so any Region, however
// compound, costs the same - and stops on the dust:
//
//   body(q) = |field(q) + rough(q) + ripple(q) - Band.x| - Band.y
//
// The band is where the dust has distributed itself, which is what constraint changes. Both start at
// the same outer face, just proud of the Region's surface, because constraint moves material about
// rather than moving the surface: a filled formation continues inward as a crust, a constrained one
// stops a shell's thickness in and leaves the interior open. Either band thins as the dust does, so
// a battered shell is a skin. Where the ray meets it, a grain is either there or it isn't:
//
//   - Coverage (Form.x) comes from the dust's density. Holes open in low-frequency patches whose
//     edges are grainy, so thin dust reads as an incomplete, patchy crust - never as uniform
//     transparency - and holes go all the way through, showing the far side.
//   - Build (Form.y) sweeps a front across the surface, from the side the dust arrived from, grain
//     by grain. A dissolving construct (Form.z) runs it the other way, breaking up in patches.
//   - Impacts knock grains out locally for a moment, dent the surface and send a ripple through it.
//
// The output is opaque and writes real depth, so the construct occludes and is occluded like any
// solid thing, and loose grains drawn afterwards sit in front of or behind it correctly. Nothing here
// moves quickly: the only global motion is a very slow crawl of the finest grain.
//
// MATERIAL: compressed granular crust, not stone. Three scales of detail, deliberately unalike, so
// the eye never settles on one repeated feature:
//
//   large   ~1 block   broad unevenness, so the shape is not machined
//   medium  ~0.25 block  ridges, compacted patches and shallow creases (ridged noise, not bumps)
//   fine    ~grain      grit: per-grain facets and speckle, the particulate cue and the strongest one
//
// It is lit as dry powder: no specular at all, soft wrapped diffuse, shallow cavity shading. The
// per-grain normals are random facets rather than little domes - domes are what read as pebbles - and
// the colour range is narrow, so grains differ in tone without each looking like its own stone.
//
// LIFE: stable at a distance, alive up close. Nothing here moves the surface as a whole - no flow, no
// swirl, no loop - because a construct that streams is a construct nobody believes they can stand on.
// What moves is individual grains:
//
//   shuffle  every grain re-rolls its own shade on its own clock, so the surface crawls without ever
//            travelling. No two grains change at the same moment, so there is no pulse to notice.
//   sparkle  a few grains in a thousand glint briefly, more of them at the silhouette and where
//            something has just struck. Accent, never glitter.
//   breathe  the dust's hold wavers by a few percent, so the edges of its gaps creep grain by grain.
//   stress   a struck area is dented, roughened and briefly busy, then settles.
//
// All of it runs faster while the construct is still forming or coming apart, and settles as it does.

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform int FogShape;

// Set directly, not through the shader JSON: two 3D textures and an array.
uniform sampler3D DustField;
uniform sampler3D NoiseTex;   // tiling value noise, one block per cell, 64 blocks of period
uniform vec4 Impacts[16];   // per impact: (x, y, z, radius), (intensity, age, ripple, 0)

uniform vec3 Origin;        // construct centre, camera-relative
uniform vec3 GridMin;       // centre-relative position of the field's first node
uniform vec3 GridSpan;      // first node to last
uniform vec3 GridRes;       // node counts
uniform vec3 ProxyMin;      // the proxy box, centre-relative
uniform vec3 ProxyMax;
uniform vec4 Band;          // band centre, band half-thickness, roughness, grain size
uniform vec4 Form;          // coverage, build, dissolving, age in ticks
uniform vec4 FormDir;       // direction the dust arrived from, centre-relative unit vector
uniform vec4 Lighting;      // world light, self-glow, radians per pixel, impact count
uniform vec4 Life;          // sparkle, grain shuffle rate, surface life, unused
uniform vec4 Quality;       // step budget, step scale, reject budget, ray LOD
uniform vec4 Motion;        // xyz travel direction (unit), w stretch along it (1 = at rest)
uniform vec3 ColorDark;
uniform vec3 ColorLight;
uniform vec3 ColorGlow;

in vec3 rayRel;

out vec4 fragColor;

// Set once per pixel in main().
float insideScale = 1.0;   // coverage multiplier: thinner when the eye is inside the construct

// ---- noise -------------------------------------------------------------------------------------

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

// The noise lattice, turned off the world axes. A texture lattice interpolates linearly, which
// leaves faint creases along its cell boundaries; on an axis-aligned wall those line up into visible
// banding. Rotated, they never align with anything the player builds or the Region can be.
const mat3 NOISE_SKEW = mat3(0.89101, 0.23382, -0.38915, 0.00000, 0.85717, 0.51504, 0.45399, -0.45890, 0.76374);

// Value noise, one cell per unit, from the texture rather than from eight hashes: this is called
// several times per marching step and it was most of the shader's cost (see DustNoise).
float vnoise(vec3 p) {
    return texture(NoiseTex, (NOISE_SKEW * p) * (1.0 / 64.0)).r;
}

// ---- shape -------------------------------------------------------------------------------------

/**
 * A body of dust under way is not a rigid one: it draws out along its travel, and its tail lags
 * further than its head leads. This is the whole of it - the sample point is pulled in along the
 * direction of travel before the shape is looked up, which renders as the shape drawn out along it.
 *
 * <p>Deliberately a contraction (both scales >= 1, so the point moves toward the centre): that keeps
 * the distance field's 1-Lipschitz property, which the ray march depends on, with no correction and
 * no extra cost. It also means the sampled point stays inside the baked grid. Stretching the field
 * the other way - squashing across the travel - would not, so it is not done.
 */
vec3 travelStretch(vec3 q) {
    if (Motion.w <= 1.001) {
        return q;
    }
    float u = dot(q, Motion.xyz);
    // The tail draws out half as much again as the head.
    float s = 1.0 + (Motion.w - 1.0) * (u < 0.0 ? 1.5 : 1.0);
    return q + Motion.xyz * (u / s - u);
}

float field(vec3 p) {
    vec3 q = travelStretch(p);
    vec3 f = (q - GridMin) / GridSpan;
    vec3 c = clamp(f, 0.0, 1.0);
    vec3 uvw = (c * (GridRes - 1.0) + 0.5) / GridRes;
    // The grid is x-major with z fastest: z is the texture's s, x its r.
    return texture(DustField, uvw.zyx).r + length((f - c) * GridSpan);
}

// Ridged noise: creases and compacted ridges rather than a field of domes.
float ridged(vec3 p) {
    return 1.0 - abs(2.0 * vnoise(p) - 1.0);
}

float rough(vec3 q) {
    // The one global motion, and it is barely a motion: the finest grain crawls, a block an hour.
    vec3 drift = vec3(0.0, Form.w * 0.0004, 0.0);
    float large = (vnoise(q * 0.9) - 0.5) * 2.0;
    float medium = ridged(q * 4.2 + 11.0) - 0.5;
    float fine = (vnoise(q * 11.0 + drift + 23.0) - 0.5) * 2.0;
    // Kept small: the silhouette should be granular, never lumpy - the Region has to stay readable.
    return Band.z * (large * 0.5 + medium * 0.75 + fine * 0.22);
}

float ripple(vec3 q) {
    float r = 0.0;
    for (int i = 0; i < 8; i++) {
        if (float(i) >= Lighting.w) {
            break;
        }
        vec4 a = Impacts[i * 2];
        vec4 b = Impacts[i * 2 + 1];
        if (b.z <= 0.0 || b.x <= 0.001) {
            continue;
        }
        float dist = length(q - a.xyz);
        float front = b.y * 0.1;
        float ring = exp(-pow((dist - front) / 0.2, 2.0));
        r += ring * b.x * 0.06 * exp(-dist / max(a.w * 2.0, 0.1));
    }
    return r;
}

// Where something struck, the surface is pressed in a little while it recovers.
float dent(vec3 q) {
    float r = 0.0;
    for (int i = 0; i < 8; i++) {
        if (float(i) >= Lighting.w) {
            break;
        }
        vec4 a = Impacts[i * 2];
        float fall = 1.0 - smoothstep(0.0, a.w, length(q - a.xyz));
        r += fall * fall * Impacts[i * 2 + 1].x * 0.12;
    }
    return r;
}

float body(vec3 q) {
    float hurt = dent(q);
    // A struck area is not just dented, it is disturbed: its grain stands up until it settles again.
    float disturbed = hurt * 8.0 * (vnoise(q * 14.0 + 31.0) - 0.5) * Band.z;
    float d = field(q) + rough(q) + ripple(q) + hurt + disturbed;
    return abs(d - Band.x) - Band.y;
}

// Never more than body(): what the roughness, ripples and dents can add is subtracted up front.
// The allowance for ripples and dents is only paid for when something has actually struck - it
// widens the band where every step has to do the full, expensive evaluation.
//
// Hands back the plain field value too, since the caller wants to know which side of the crust it is
// on and a second lookup for that would be a lookup wasted.
float bodyBound(vec3 q, out float plain) {
    plain = field(q);
    float strike = Lighting.w > 0.5 ? 0.2 : 0.0;
    return abs(plain - Band.x) - Band.y - Band.z * 2.6 - strike;
}

vec3 bodyNormal(vec3 q) {
    const float h = 0.015;
    const vec2 k = vec2(1.0, -1.0);
    vec3 n = k.xyy * body(q + k.xyy * h) + k.yyx * body(q + k.yyx * h)
           + k.yxy * body(q + k.yxy * h) + k.xxx * body(q + k.xxx * h);
    float len = length(n);
    return len > 1.0e-6 ? n / len : vec3(0.0, 1.0, 0.0);
}

// ---- grains ------------------------------------------------------------------------------------

/**
 * How much of the grain a pixel this far away can actually resolve, 0-1.
 *
 * <p>Grains keep ONE size in the world. An earlier version grew them in octaves to keep them a
 * couple of pixels wide, and every octave boundary was a ring at a fixed distance from the eye:
 * walking toward a construct swept those rings across it, which read as the surface rippling
 * whenever the viewer moved and stopping dead when they stood still. Now nothing about the material
 * changes with distance except how strongly its finest scale is shown - which fades out smoothly, as
 * it must, since a grain smaller than a pixel can only alias.
 */
float grainFade(float t) {
    float footprint = max(t * Lighting.z, 1.0e-5);
    // Life.w is the quality setting: below 1 the grain gives out closer to the eye, which is the only
    // part of a view from inside a construct that there is anything left to save on.
    return clamp(Band.w / footprint * 0.5 * Life.w - 0.4, 0.0, 1.0);
}

// Cellular grains: distance to the nearest jittered grain centre (in grain units) and that grain's
// id. Used for which grains are present and for the shallow gaps between them - NOT to round them
// into domes, which is what made an earlier pass read as heaped pebbles.
void grains(vec3 q, float g, out float f1, out float id) {
    vec3 p = q / g;
    vec3 base = floor(p - 0.5);
    float best = 9.0;
    id = 0.0;
    for (int x = 0; x < 2; x++) {
        for (int y = 0; y < 2; y++) {
            for (int z = 0; z < 2; z++) {
                vec3 c = base + vec3(float(x), float(y), float(z));
                vec3 j = c + 0.5 + (hash33(c) - 0.5) * 0.9;
                vec3 dv = p - j;
                float dd = dot(dv, dv);
                if (dd < best) {
                    best = dd;
                    id = hash13(c + 17.17);
                }
            }
        }
    }
    f1 = sqrt(best);
}

/**
 * A grain's own slowly re-rolled value. Each grain has its own phase and its own clock, so grains
 * change one at a time rather than the surface changing together: crawling, not flowing.
 */
float shuffle(float id, float rate) {
    float t = Form.w * rate + id * 17.0;
    float step0 = floor(t);
    float frac = t - step0;
    float a = hash13(vec3(id * 91.7, step0, 3.1));
    float b = hash13(vec3(id * 91.7, step0 + 1.0, 3.1));
    return mix(a, b, smoothstep(0.0, 1.0, frac));
}

/**
 * A brief glint on a rare grain. Rarer on a settled face, more common at the silhouette and where
 * the construct has just been struck - so sparkle reads as magic under strain, not as glitter.
 */
float sparkle(float id, float rim, float stress) {
    float t = Form.w * Life.y * 0.5 + id * 31.7;
    float cycle = floor(t);
    float pick = hash13(vec3(id * 57.3, cycle, 7.7));
    float rarity = 1.0 - (0.012 + 0.035 * rim + 0.06 * stress);
    if (pick < rarity) {
        return 0.0;
    }
    float flash = max(0.0, 1.0 - abs((t - cycle) - 0.5) * 6.0);
    return flash * flash;
}

// Recent impacts: grains knocked loose around each, and the brief brightening where it struck.
float damage(vec3 q, out float flash) {
    float dmg = 0.0;
    flash = 0.0;
    for (int i = 0; i < 8; i++) {
        if (float(i) >= Lighting.w) {
            break;
        }
        vec4 a = Impacts[i * 2];
        vec4 b = Impacts[i * 2 + 1];
        float dist = length(q - a.xyz);
        float fall = 1.0 - smoothstep(a.w * 0.3, a.w, dist);
        dmg += fall * b.x;
        flash += fall * b.x * exp(-b.y / 6.0);
    }
    return dmg;
}

// Order grains join a building construct (and leave a dissolving one), 0-1.08.
float buildOrder(vec3 q, float id) {
    vec3 n = q / max(length(q), 1.0e-3);
    float facing = 0.5 - 0.5 * dot(n, FormDir.xyz);  // 0 on the side the dust came from
    float patchy = vnoise(q * 0.9 + 41.0);
    float order = Form.z > 0.5
        ? (1.0 - facing) * 0.45 + patchy * 0.55     // breaks up in patches, far side first
        : facing * 0.6 + patchy * 0.4;              // fills from the near side
    return order + id * 0.08;
}

// > 0 where a grain is present. Also hands back how close this is to the build front, and impact flash.
float presence(vec3 q, float id, float fade, float t, out float front, out float flash) {
    front = 1.0;
    flash = 0.0;
    bool settled = Form.y >= 0.999 && Form.z < 0.5;
    // A finished, dense, undisturbed construct has no holes to work out and no front to sweep, which
    // is the common case and by far the cheapest: skip the lot.
    if (Form.x * insideScale >= 0.999 && settled && Lighting.w < 0.5 && t > 0.8) {
        return 1.0;
    }
    // Value noise bunches round 0.5; stretched, so coverage maps onto area more evenly and a thin
    // construct really is thin rather than pinholed. Loose dust breaks up grain by grain at the edges
    // of its patches; packed dust holds a cleaner face.
    float coarseHoles = vnoise(q * 1.6 + 5.0);
    float fineHoles = vnoise(q * 3.4 + 9.0);
    float holes = smoothstep(0.2, 0.8, coarseHoles * 0.6 + fineHoles * 0.4);
    // The grain-scale part of the threshold has to fade out with the grains themselves. Far away,
    // where a grain is under a pixel, its stand-in is a smooth noise - and a smooth noise at full
    // weight tears coherent holes in a body that should be solid. That was a real bug: dense
    // constructs opened up as the viewer walked away, and every hole cost a ray more marching.
    float grainy = mix(0.22, 0.1, smoothstep(0.3, 0.95, Form.x)) * fade;
    float v = holes * (1.0 - grainy) + id * grainy;
    // The magic holding the dust wavers by a few percent: two patterns traded back and forth, so the
    // hold changes in place rather than drifting anywhere, and the edges of the construct's gaps
    // creep grain by grain. The two are the hole noise's own octaves - this is evaluated on every
    // sample a ray tests through a patchy construct, and two lookups of its own were two too many.
    float waver = (mix(coarseHoles, fineHoles, 0.5 + 0.5 * sin(Form.w * 0.02)) - 0.5) * 0.07 * Life.z;
    float cover = Form.x * insideScale * 1.02 + waver - damage(q, flash) * 1.1;
    if (!settled) {
        front = Form.y * 1.1 - buildOrder(q, id);
    }
    float p = min(cover - v, front);
    // Grains right against the eye thin out, so standing in your own dust never fills the screen.
    return min(p, t / 0.7 - id);
}

// ---- output ------------------------------------------------------------------------------------

float fogDistance(vec3 pos) {
    if (FogShape == 0) {
        return length(pos);
    }
    return max(length(pos.xz), abs(pos.y));
}

void shade(vec3 q, vec3 dir, float t, float fade, float f1, float id, float front, float flash) {
    vec3 n = bodyNormal(q);
    // A normal pointing away from the eye means this is the crust's inner face: the inside of a
    // shell, a bubble seen from within, or the far wall glimpsed through a gap. The flip is free and
    // says exactly what a second, six-tap lookup of the Region's own normal used to say.
    bool innerFace = dot(n, dir) > 0.0;
    if (innerFace) {
        n = -n;
    }
    // How tightly this dust is packed. Thin dust is loose grit with its grains showing; dense dust is
    // a tight crust. Density therefore changes the material, not only how much of it there is.
    float compaction = smoothstep(0.3, 0.95, Form.x);

    // Fine scale, on the cells themselves rather than on a lattice - a floor(q / size) hash shows
    // its grid as soon as you stand close to it. A second, finer set of cells sits inside the first,
    // and is not even looked up once it is too small to see.
    float f2 = 0.5;
    float id2 = id;
    if (fade > 0.15) {
        // Warped lattice rather than a second cell search: this scale only feeds the speckle and the
        // facet direction, neither of which needs true cells, and the search is sixteen hashes on
        // every pixel that gets drawn - which at close range is all of them.
        vec3 fq = q + vec3(0.0, Form.w * 0.002, 0.0);
        float wobble = vnoise(fq * (2.0 / Band.w)) - 0.5;
        id2 = hash13(floor((fq + wobble * Band.w * vec3(0.9, -0.7, 0.5)) / (Band.w * 0.45)) + 0.5);
    }
    // Each grain is a flat facet turned its own way: dust is faceted powder, and giving grains dome
    // normals is exactly what made an earlier pass read as heaped pebbles.
    vec3 facet = hash33(vec3(id, id2, id * 3.7 + id2) * 37.0) - 0.5;
    vec3 lit = normalize(n + facet * mix(0.5, 0.26, compaction) * fade);
    // Medium scale: compacted patches, ridges and shallow creases - the same field the surface itself
    // is ridged by, so what is shaded as a crease is a crease.
    float ridge = ridged(q * 4.2 + 11.0);
    // Large scale: broad, quiet variation, so a big face is never one flat pigment.
    float broad = vnoise(q * 0.9) * 0.65 + vnoise(q * 2.1 + 7.0) * 0.35;

    // Speckle: the grain, and the grit within it. The finer grit re-rolls slowly, one grain at a
    // time, which is the whole of the surface's motion - it never travels anywhere. Some stretches of
    // the surface are livelier than others, and where those stretches are drifts very slowly, so the
    // crawl is never an even wash of static.
    float unsettled = clamp(1.0 - Form.y, 0.0, 1.0) + Form.z * 0.6;
    float rate = Life.y * (1.0 + 2.5 * unsettled) * (0.35 + 1.3 * broad);
    float grit = mix(0.5, id * 0.5 + shuffle(id2, rate) * 0.5, fade);

    // Narrow colour range on purpose: depth and grain, not confetti. Compacted areas sit darker,
    // exposed grains slightly brighter, and tighter dust varies less grain to grain.
    float tone = clamp(0.44 + 0.34 * (broad - 0.5) + 0.30 * (grit - 0.5)
        + 0.2 * (id - 0.5) * mix(1.0, 0.45, compaction) * fade - 0.18 * (ridge - 0.5), 0.0, 1.0);
    vec3 albedo = mix(ColorDark, ColorLight, tone);

    // Dry powder: no specular of any kind, soft wrapped diffuse, sky above and ground below, plus
    // shallow cavity shading in the creases and the gaps between grains. Gaps are only lightly
    // darkened - deep shadow between them is what makes packed spheres, not packed dust.
    float ndl = dot(lit, normalize(vec3(0.35, 1.0, 0.25)));
    float diffuse = 0.32 + 0.68 * clamp(ndl * 0.8 + 0.2, 0.0, 1.0);
    float ambient = mix(0.78, 1.06, lit.y * 0.5 + 0.5);
    float crevice = smoothstep(0.5, 0.95, f1) * fade;
    float cavity = mix(0.78, 1.0, ridge) * mix(1.0, 0.82, crevice);
    vec3 color = albedo * diffuse * ambient * cavity * Lighting.x;
    if (innerFace && insideScale >= 1.0) {
        // Seen from outside through a gap: the inner face of the crust, in its own shadow.
        color *= 0.55;
    }

    // Its own faint light: caught by the grain, never a wash - it must not flatten the texture out.
    color += ColorGlow * Lighting.y * (0.55 + 0.45 * grit) * cavity;

    // The silhouette is where a construct is least sure of itself: the grain there is loosest and
    // catches the most light, which is also where grains peel away into the loose dust.
    float rim = 1.0 - abs(dot(n, dir));
    rim = smoothstep(0.55, 1.0, rim);
    color += ColorGlow * Lighting.y * rim * (0.3 + 0.7 * grit) * 1.6;

    // Glints: a few grains in a thousand, brief, and never in step with each other. Keyed to the
    // visible grain, not to the grit inside it - grit cells go under a pixel at any distance, and a
    // glint the eye cannot resolve is a glint nobody sees.
    float glint = sparkle(id, rim + unsettled * 0.5, clamp(flash * 2.0, 0.0, 1.0));
    color += ColorGlow * glint * Life.x * (0.5 + 0.5 * Lighting.x);
    // The edge of a forming or breaking construct glows faintly, where grains are joining or leaving.
    color += ColorGlow * (1.0 - smoothstep(0.0, 0.1, front)) * 0.5;
    color += ColorGlow * clamp(flash, 0.0, 1.0) * 0.45;

    vec3 hit = dir * t;
    float fogDist = fogDistance(hit);
    float fog = fogDist <= FogStart ? 0.0 : (fogDist < FogEnd ? smoothstep(FogStart, FogEnd, fogDist) : 1.0);
    color = mix(color, FogColor.rgb, fog * FogColor.a);

    fragColor = vec4(color, 1.0);
    vec4 clip = ProjMat * ModelViewMat * vec4(hit, 1.0);
    gl_FragDepth = clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 1.0);
}

void main() {
    vec3 dir = normalize(rayRel);
    vec3 ro = -Origin;
    vec3 safe = mix(dir, vec3(1.0e-6), lessThan(abs(dir), vec3(1.0e-6)));
    vec3 inv = 1.0 / safe;
    vec3 ta = (ProxyMin - ro) * inv;
    vec3 tb = (ProxyMax - ro) * inv;
    vec3 lo = min(ta, tb);
    vec3 hi = max(ta, tb);
    float tEnter = max(max(lo.x, lo.y), lo.z);
    float tExit = min(min(hi.x, hi.y), hi.z);
    if (length(rayRel) < tExit - 0.02) {
        // A front face; the back face behind it marches this pixel.
        discard;
    }

    float t = max(tEnter, 0.05);
    // An eye inside its own construct sees it thinned, so a dome around the caster can be seen out of.
    insideScale = field(ro) < Band.x + Band.y ? 0.6 : 1.0;
    int steps = int(Quality.x);
    int rejectBudget = int(Quality.z);
    if (insideScale < 1.0) {
        // Seen from within, the dust is thinned so the caster can see out - which means most rays
        // find a gap in the near wall and then go hunting through the far wall's gaps as well, with
        // the whole screen doing it. One wall's worth of hunting is enough; what shows through a gap
        // from in here should be the world, not more of your own dust.
        rejectBudget = min(rejectBudget, 3);
    } else {
        // How many grains deep a ray may keep looking before it gives up and lets the world through.
        // Thin dust gets fewer tries, which is both what it should look like - ten chances to find a
        // grain makes a 40%-covered crust read as far denser than it is - and where the cost of a
        // patchy construct is: this is per pixel, over the whole of it.
        rejectBudget = int(float(rejectBudget) * (0.35 + 0.65 * Form.x));
    }
    int rejects = 0;
    float outside = t;   // the last place the ray was known to be clear of the dust
    bool crossed = false;  // this ray has already been through the crust once
    for (int i = 0; i < 256; i++) {
        if (i >= steps || t > tExit) {
            break;
        }
        vec3 q = ro + dir * t;
        // Detail the ray can no longer resolve is detail not worth marching for: the further along
        // the ray, the coarser the smallest step. A grazing ray would otherwise creep along a whole
        // platform in 4 mm steps, which is where most of the cost of a close view came from.
        //
        // But it can never be allowed past the crust itself. Uncapped, at 20 blocks the floor was
        // 0.6 of a block against a crust 0.54 thick: rays went straight through the near wall of a
        // hollow shape and out of the far side, and a sphere came out as a ring - solid only where
        // rays graze along the crust and cannot miss it. Everything the LOD is for happens well
        // inside this cap.
        float coarse = min(0.004 + t * Quality.w, max(0.02, Band.y * 0.7));
        float plain;
        float bound = bodyBound(q, plain);
        if (bound > 0.05) {
            // Through a gap and out the other side of the crust, into the hollow the construct
            // encloses: stop. What shows through a hole should be the world beyond, not the far wall
            // of the same body - and hunting that far wall, through its holes as well, is what made
            // thin and inside-out views cost several times what a solid one does.
            //
            // Only when the ray has gone *inward*: one that skims back out into open air may still
            // have another part of a compound Region ahead of it, or this one at a graze.
            if (crossed && plain < Band.x - Band.y) {
                break;
            }
            outside = t;
            t += max(bound, coarse);
            continue;
        }
        crossed = true;
        float d = body(q);
        if (d < 0.004) {
            // A coarse step lands some way inside the crust, and how far inside depends on the
            // step, which depends on distance. Left alone, the surface - and which of its grains
            // are present - creeps as the viewer walks: the rippling that octave-stepped grains
            // used to cause. Bisecting puts the hit where the surface is, whatever step found it.
            //
            // In two parts, because most candidates in a porous construct are gaps that this ray
            // should simply carry on through: one bisection now, which is all the low-frequency
            // hole pattern needs, and the rest only once the grain is known to be there.
            float near = outside;
            float far = t;
            if (d < -0.01) {
                float mid = (near + far) * 0.5;
                if (body(ro + dir * mid) < 0.004) {
                    far = mid;
                } else {
                    near = mid;
                }
                t = far;
                q = ro + dir * t;
            }
            float fade = grainFade(t);
            // For deciding whether a grain is there at all, a one-hash value per grain cell is
            // plenty: it only ruffles the edges of the holes. The cell search that shapes and shades
            // the grain costs sixteen hashes, and a ray through a patchy construct tests many more
            // samples than it ever draws - so that one waits until something is actually being drawn.
            // The lattice is nudged by a smooth noise before it is hashed, or its cube grid shows
            // as stair-steps along the edges of the holes.
            float id;
            if (fade > 0.02) {
                float wobble = vnoise(q * (0.9 / Band.w)) - 0.5;
                id = hash13(floor((q + wobble * Band.w * vec3(1.7, -1.3, 0.9)) / Band.w) + 0.5);
            } else {
                id = vnoise(q * (0.37 / Band.w));
            }
            float f1 = 0.5;
            float front;
            float flash;
            if (presence(q, id, fade, t, front, flash) > 0.0) {
                // Down to a fraction of a grain: any coarser and a pixel lands in a neighbouring
                // grain as the step changes, which is a shimmer of its own.
                float want = max(0.008, Band.w * 0.2);
                for (int r = 0; r < 4; r++) {
                    if (far - near <= want) {
                        break;
                    }
                    float mid = (near + far) * 0.5;
                    if (body(ro + dir * mid) < 0.004) {
                        far = mid;
                    } else {
                        near = mid;
                    }
                }
                if (far < t) {
                    t = far;
                    q = ro + dir * t;
                }
                if (fade > 0.02) {
                    grains(q, Band.w, f1, id);
                }
                shade(q, dir, t, fade, f1, id, front, flash);
                return;
            }
            // A gap in the dust: carry on through it, faster the deeper in the crust we are.
            outside = t;
            t += max(Band.w * 1.1 + max(-d, 0.0) * 0.6, coarse);
            if (++rejects > rejectBudget) {
                break;
            }
            continue;
        }
        // The noise makes the field steeper than a true distance; step short of it.
        outside = t;
        t += max(d * Quality.y, coarse);
    }
    discard;
}
