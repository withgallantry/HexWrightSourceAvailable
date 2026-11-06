#version 150

#moj_import <hexwright_hexahedron.glsl>


uniform vec3 CameraOffset;
uniform float Time;
uniform float Front;
uniform float Half;
uniform float Healing;
uniform float Fade;
uniform float Angle;
uniform float Flare;

in vec3 local;

out vec4 fragColor;

const float PIXEL = 2.0;
const float LENGTH = 0.66;
const float WIDTH = 0.17;
const float LANE = 24.0;
const float NEBULA_DEPTH = 900.0;
const float SWIRL_TURNS = 8.0;
const float WRAP_SECONDS = 1200.0;
const float VORTEX_REACH = 380.0;
const vec3 STORM_DARK = vec3(0.06, 0.05, 0.09);
const vec3 STORM_LIGHT = vec3(0.2, 0.17, 0.27);
const vec3 VOID = vec3(0.02, 0.0, 0.05);
const vec3 RIM = vec3(0.72, 0.55, 1.0);
const vec3 HOT = vec3(0.95, 0.92, 1.0);
const vec3 SEAM = vec3(1.0, 0.86, 0.55);

vec2 beyond(vec3 at, vec3 view, float depth) {
    return at.xz + view.xz * (depth / max(view.y, 0.06));
}

float vortex(vec2 p) {
    float r = length(p);
    float spin = atan(p.y, p.x) + 1.6 * log(1.0 + r / 30.0) - Time * 6.2831853 * SWIRL_TURNS / WRAP_SECONDS;
    vec2 ring = vec2(cos(spin), sin(spin)) * (1.5 + r / 90.0);
    return fbm(ring * 1.6 + vec2(r / 140.0, 0.0), 4);
}

vec3 otherWorld(vec3 at, vec3 view) {
    vec2 n = beyond(at, view, NEBULA_DEPTH) / 420.0;
    vec2 warp = vec2(fbm(n + Time * 0.01, 3), fbm(n + 5.2 - Time * 0.008, 3));
    float cloud = fbm(n + warp * 1.6, 4);
    vec3 colour = VOID + mix(vec3(0.28, 0.04, 0.38), vec3(0.04, 0.32, 0.45), warp.x) * smoothstep(0.35, 0.85, cloud);
    vec2 sky = floor(beyond(at, view, NEBULA_DEPTH * 0.6) / 3.0);
    float star = step(0.9965, hash(sky));
    colour += vec3(0.9, 0.85, 1.0) * star * (0.6 + 0.4 * sin(Time * 3.0 + hash(sky + 7.0) * 40.0));
    return colour;
}

void main() {
    vec2 p = (floor(local.xz / PIXEL) + 0.5) * PIXEL;
    vec2 q = vec2(cos(Angle) * p.x + sin(Angle) * p.y, cos(Angle) * p.y - sin(Angle) * p.x);
    float open = clamp(Front / (Half * 1.4142), 0.0, 1.0);
    float halfLength = Half * LENGTH * smoothstep(0.0, 0.45, open) + 1.0;
    float along = q.x / halfLength;
    float taper = pow(max(1.0 - along * along, 0.0), 0.8);
    float side = q.y > 0.0 ? 1.7 : 6.3;
    float rag = (fbm(vec2(q.x / 22.0, side), 3) - 0.5) * 0.9 + (noise(vec2(q.x / 3.0, side + 2.4)) - 0.5) * 0.3;
    float halfWidth = Half * WIDTH * smoothstep(0.08, 0.75, open) * taper * (1.0 + rag);
    float d = abs(q.y) - halfWidth;
    float within = step(abs(along), 1.0);
    float inside = (1.0 - smoothstep(-0.8, 0.8, d)) * within;
    float past = max(d, 0.0);
    float clear = abs(along) <= 1.0 ? past : length(vec2(abs(q.x) - halfLength, q.y));

    float crackle = noise(vec2(q.x / 6.0, Time * 4.0));
    float edge = exp(-abs(d) / (1.4 + 1.4 * crackle)) * within;
    float halo = exp(-past / 14.0) * within * smoothstep(0.0, 0.2, open);

    float lane = q.x / LANE;
    float id = floor(lane);
    float wander = (noise(vec2(past / 9.0, id * 3.7 + side)) - 0.5) * 0.7 + (hash(vec2(id, side)) - 0.5) * 0.5;
    float fromLine = abs(fract(lane) - 0.5 - wander * 0.5) * LANE;
    float reach = (25.0 + 110.0 * hash(vec2(id, side + 1.0))) * open;
    float crack = (1.0 - smoothstep(0.4, 1.4, fromLine)) * (1.0 - smoothstep(reach * 0.6, reach, past))
        * step(0.0, d) * step(abs(along), 0.95) * step(0.45, hash(vec2(id, side + 2.0)))
        * (0.6 + 0.4 * noise(vec2(id, Time * 2.0)));

    float cloud = vortex(p);
    float storm = smoothstep(0.38, 0.68, cloud) * smoothstep(0.0, 10.0, clear) * smoothstep(0.0, 0.5, open)
        * (1.0 - smoothstep(VORTEX_REACH * 0.6, VORTEX_REACH, length(p))) * 0.92;
    vec3 stormColour = mix(STORM_DARK, STORM_LIGHT, smoothstep(0.5, 0.8, cloud))
        + mix(RIM, SEAM, Healing) * 0.6 * exp(-clear / 40.0);

    vec3 view = normalize(local - CameraOffset);
    vec3 inner = otherWorld(local, view) * (1.0 - 0.6 * Healing) + HOT * Flare * 0.25;
    float glow = edge * 1.3 + halo * 0.25 + crack * 0.8 + Flare * halo * 0.35;
    vec3 light = mix(mix(RIM, SEAM, Healing), HOT, edge * 0.5) * glow;
    vec3 colour = inner * inside + stormColour * storm * (1.0 - inside) + light;
    float alpha = max(max(inside * 0.97, storm), clamp(edge * 0.5 + crack * 0.25, 0.0, 1.0));
    fragColor = vec4(min(colour, vec3(1.0)) * Fade, alpha * Fade);
}
