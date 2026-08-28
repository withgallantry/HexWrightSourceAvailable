#version 150

// Quartz core shield, contact-glow pass.
//
// Where the shield's six faces cut through solid matter, a band of fixed width
// is drawn along the cut - on the shield's own surface, either side of the
// crossing curve.
//
// THE MEASURE IS NOT THE DEPTH BUFFER, and that is the whole point. Two
// previous generations of this shader asked "how far in front of the nearest
// visible surface is this fragment", which is a difference of eye distances
// and is viewpoint-dependent in a way no amount of shaping can fix: with the
// eye h blocks above a floor and a shield wall D away, the ray through a point
// y up the wall meets the floor at D*h/(h-y), so as the eye drops level with
// the surface that ray skims parallel to it and lands tens of blocks past the
// shield. The crossing is then simply not present in the depth buffer. The
// band thinned to a mark lying in the floor's plane, disappeared at grazing
// angles, and stopped dead at the edge of the visible floor - all of which
// read as the glow being painted onto the blocks rather than drawn on the
// shield, because a screen-space measure can only ever follow screen features.
//
// SamplerContactField is built on the CPU from block collision shapes (see
// ShieldContactField): one tile per face, each texel holding the distance, in
// blocks measured ACROSS THE FACE, to the nearest solid/empty transition on
// it. Sampling it costs one fetch and refers to the camera nowhere, so the
// band is the same width from every angle and exists only on the real
// intersection curve.
//
// The depth buffer is still read, but only for what a depth buffer is for:
// hiding the parts of the shield that are behind the world. That test is done
// here with slack rather than by GL, because this pass paints fragments lying
// exactly on the surfaces they are being tested against, which is where a
// LEQUAL test z-fights into speckle.
//
// The band's width is modulated by value noise sampled at a world position
// snapped to a quarter-block grid, so the line breaks up into chunky ink that
// spreads and joins along the surface. Both grids are locked to world space
// (see InkOrigin), so the ink stays put on the rock while the caster walks
// their shield through it.

uniform sampler2D SamplerSceneDepth;
uniform sampler2D SamplerContactField;

uniform vec2 ScreenSize;
uniform float GameTime;
uniform vec2 NearFar;
uniform vec3 InkOrigin;
uniform float GlowWidth;
// Blocks of contact distance the field texture's full range covers - must
// match ShieldContactField.RANGE.
uniform float FieldRange;
// The field's sample spacing in blocks, which is the finest contour it can
// resolve. See WIDTH_FLOOR_CELLS.
uniform float FieldCell;
// 0 off. 1 raw contact distance as grey, 2 band(red)+fresnel(green),
// 3 face normal, 4 the final alpha, 5 fresnel alone.
// Driven by -Dhexwright.shield.debug=N; see the block in main().
uniform float DebugMode;
// Draw only one of the six faces (0..5), or all of them at -1.
//
// Worth having because this pass runs with NO DEPTH TEST and NO CULLING - the
// caster is inside the cube, so every face has to draw from behind - which
// means up to six fragments land on each pixel with nothing ordering them.
// Blended that is a sum and reads as intended, but any opaque debug readout
// becomes a mosaic decided by draw order rather than by which face is nearest,
// and the mosaic reshuffles as fragments cross the occlusion test. That looks
// exactly like the field itself being unstable, and it is not.
// Driven by -Dhexwright.shield.face=N.
uniform float DebugFace;

in vec4 vertexColor;
in vec3 shieldPos;
in vec2 fieldUV;

out vec4 fragColor;

// Ink grid, in cells per block. A power of two so the world-space wrap the
// caller applies to InkOrigin lands on an exact cell boundary.
const float INK_CELLS_PER_BLOCK = 4.0;
// Noise lattice spacing, in blocks - roughly the size of one splotch.
const float INK_FREQUENCY = 0.25;
const float INK_DRIFT = 0.08;

// The band, in blocks across the face: full strength out to GlowWidth, fading
// to nothing over CONTACT_FADE after it. Both are true distances on the
// shield's surface now, so they mean the same thing at any range and any
// viewing angle - unlike every previous version of these constants.
const float CONTACT_FADE = 0.13;
// How much the ink noise is allowed to swing the width, as a fraction.
const float INK_WIDTH_SWING = 0.35;
// Floor on the band's width, in field cells.
//
// The field is sampled every FieldCell blocks and cannot describe a contour
// any finer, so a band thinner than about a cell comes out broken or misses
// the contour altogether. That is invisible at the default spacing and would
// bite exactly once - when a caster with a large enough Ambit pushes the field
// down to a coarser rung of ShieldContactField.CELL_SIZES and the glow
// quietly disappears on the biggest shield in the game.
const float WIDTH_FLOOR_CELLS = 0.8;

// Fresnel exponent and weight, from the Photon 2 reference shield. This is the
// term that makes the surface read as a shield rather than as a mark painted
// on the world: it lights the faces at grazing angles, so the contact band
// sits on something visibly glowing.
const float FRESNEL_POWER = 0.5;
const float FRESNEL_STRENGTH = 1.0;

// How far behind the scene a fragment may sit and still draw, in blocks.
// Covers depth-buffer precision and the shield sitting flush against a block
// face. Occlusion only - nothing about the glow's shape depends on it.
const float DEPTH_SLACK = 0.06;
// Softness of the ink reveal front. vertexColor.a carries the cube's open
// progress, and the band inks itself in splotch by splotch against the same
// noise that shapes it - low-ink cells first, spreading until they join.
const float REVEAL_SOFTNESS = 0.18;

float hash13(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.71, 0.113, 0.419));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float valueNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash13(i + vec3(0.0, 0.0, 0.0)), hash13(i + vec3(1.0, 0.0, 0.0)), u.x),
                   mix(hash13(i + vec3(0.0, 1.0, 0.0)), hash13(i + vec3(1.0, 1.0, 0.0)), u.x), u.y),
               mix(mix(hash13(i + vec3(0.0, 0.0, 1.0)), hash13(i + vec3(1.0, 0.0, 1.0)), u.x),
                   mix(hash13(i + vec3(0.0, 1.0, 1.0)), hash13(i + vec3(1.0, 1.0, 1.0)), u.x), u.y), u.z);
}

// Window depth (0..1) to distance from the eye in blocks.
float linearDepth(float windowZ) {
    float zNear = NearFar.x;
    float zFar = NearFar.y;
    float ndc = windowZ * 2.0 - 1.0;
    return (2.0 * zNear * zFar) / (zFar + zNear - ndc * (zFar - zNear));
}

// GameTime is the day fraction; scale it to something that moves.
float animTime() {
    return GameTime * 1200.0;
}

// textureLod rather than texture: plain texture() picks its mip level from
// implicit derivatives, which are undefined in non-uniform control flow. These
// attachments have no mips, so asking for level 0 outright costs nothing and
// stays well defined wherever it is called from.
float sceneDepthAt(vec2 uv) {
    return linearDepth(textureLod(SamplerSceneDepth, uv, 0.0).r);
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;

    // Occlusion, and nothing else. shieldPos is camera-relative, so its length
    // is the distance to this fragment, and the scene surface lies along the
    // same ray scaled by the ratio of the two view depths.
    float sceneZ = sceneDepthAt(uv);
    float fragZ = linearDepth(gl_FragCoord.z);
    float gap = length(shieldPos) * (sceneZ / max(fragZ, 1e-4) - 1.0);

    // Face normal straight from the screen derivatives of the position. The
    // faces are flat, so this is exact, and it saves carrying a normal through
    // the vertex format. Taken in uniform control flow, before any discard.
    vec3 viewDir = -normalize(shieldPos);
    // Guarded: where the two derivatives run parallel, or where a face is so
    // edge-on that both are vanishing, the cross product has no length and
    // normalize() returns NaN - which propagates into alpha and reaches the
    // framebuffer as solid opaque. Fall back to the view direction, which
    // reads as perfectly face-on and so contributes no rim at all.
    vec3 faceCross = cross(dFdx(shieldPos), dFdy(shieldPos));
    float faceCrossLength = length(faceCross);
    vec3 faceNormal = faceCrossLength > 1e-9 ? faceCross / faceCrossLength : viewDir;
    // abs(): the caster is inside the cube and sees its faces from behind, so
    // which way the normal happens to point must not decide whether it lights.
    float fresnel = pow(1.0 - abs(dot(faceNormal, viewDir)), FRESNEL_POWER);

    // Which tile of the atlas this fragment reads is which face it is on.
    if (DebugFace >= 0.0) {
        float face = floor(min(fieldUV.x, 0.999) * 3.0) + 3.0 * floor(min(fieldUV.y, 0.999) * 2.0);
        if (abs(face - DebugFace) > 0.5) {
            discard;
        }
    }

    // Blocks across the face to the nearest solid/empty transition on it.
    float contactDist = textureLod(SamplerContactField, fieldUV, 0.0).r * FieldRange;

    // Above the occlusion discard on purpose: a debug readout exists to show
    // one quantity and nothing else, and mixing the depth test into it was how
    // the field last got blamed for holes the depth buffer had punched in it.
    if (DebugMode > 0.5 && DebugMode < 1.5) {
        // The field itself. Grey ramps from black at the crossing to white
        // FieldRange blocks away; flat white means this face found no
        // transition at all, which is the correct answer for a face entirely
        // in open air or entirely inside rock.
        fragColor = vec4(vec3(contactDist / FieldRange), 1.0);
        return;
    }

    if (gap < -DEPTH_SLACK) {
        discard;
    }

    vec3 cell = floor((shieldPos + InkOrigin) * INK_CELLS_PER_BLOCK) / INK_CELLS_PER_BLOCK;
    float ink = valueNoise(cell * INK_FREQUENCY + vec3(0.0, animTime() * INK_DRIFT, 0.0));

    float width = max(GlowWidth * (1.0 - INK_WIDTH_SWING + 2.0 * INK_WIDTH_SWING * ink),
                      FieldCell * WIDTH_FLOOR_CELLS);
    float contact = smoothstep(0.0, 1.0, 1.0 - (contactDist - width) / CONTACT_FADE);

    float drive = vertexColor.a * (1.0 + 2.0 * REVEAL_SOFTNESS);
    float reveal = smoothstep(ink - REVEAL_SOFTNESS, ink + REVEAL_SOFTNESS, drive);

    float alpha = clamp(fresnel * FRESNEL_STRENGTH + contact, 0.0, 1.0) * reveal;

    if (DebugMode > 0.5) {
        vec3 dbg = vec3(0.0);
        if (DebugMode < 2.5) {
            dbg = vec3(contact, fresnel, 0.0);
        } else if (DebugMode < 3.5) {
            dbg = faceNormal * 0.5 + 0.5;
        } else if (DebugMode < 4.5) {
            // Alpha in red, on a fixed dark-blue floor. A plain greyscale
            // readout is useless against a night sky - "black" then means
            // either zero alpha or simply no daylight, and those are the two
            // answers being told apart. The blue floor marks where this pass
            // has geometry at all, whatever the alpha turns out to be.
            dbg = vec3(alpha, 0.0, 0.25);
        } else {
            dbg = vec3(fresnel, 0.0, 0.25);
        }
        fragColor = vec4(dbg, 1.0);
        return;
    }

    if (alpha <= 0.0) {
        discard;
    }

    // The caster's pigment, straight through - the same colour the shield's own
    // faces are drawn in. There used to be a 70% mix toward white on the band's
    // core, on top of the 55% the vertex colour had already been lightened by,
    // which between them left the glow reading as white with a tint rather than
    // as the caster's colour at all.
    fragColor = vec4(vertexColor.rgb, alpha);
}
