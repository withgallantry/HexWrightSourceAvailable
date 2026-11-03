#version 150


uniform sampler2D SamplerSceneDepth;
uniform sampler2D SamplerContactField;

uniform vec2 ScreenSize;
uniform float GameTime;
uniform vec2 NearFar;
uniform vec3 InkOrigin;
uniform float GlowWidth;
uniform float FieldRange;
uniform float FieldCell;
uniform float DebugMode;
uniform float DebugFace;

in vec4 vertexColor;
in vec3 shieldPos;
in vec2 fieldUV;

out vec4 fragColor;

const float INK_CELLS_PER_BLOCK = 4.0;
const float INK_FREQUENCY = 0.25;
const float INK_DRIFT = 0.08;

const float CONTACT_FADE = 0.13;
const float INK_WIDTH_SWING = 0.35;
const float WIDTH_FLOOR_CELLS = 0.45;

const float FRESNEL_POWER = 3.0;
const float FRESNEL_STRENGTH = 0.25;

const float DEPTH_SLACK = 0.06;
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

float linearDepth(float windowZ) {
    float zNear = NearFar.x;
    float zFar = NearFar.y;
    float ndc = windowZ * 2.0 - 1.0;
    return (2.0 * zNear * zFar) / (zFar + zNear - ndc * (zFar - zNear));
}

float animTime() {
    return GameTime * 1200.0;
}

float sceneDepthAt(vec2 uv) {
    return linearDepth(textureLod(SamplerSceneDepth, uv, 0.0).r);
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;

    float sceneZ = sceneDepthAt(uv);
    float fragZ = linearDepth(gl_FragCoord.z);
    float gap = length(shieldPos) * (sceneZ / max(fragZ, 1e-4) - 1.0);

    vec3 viewDir = -normalize(shieldPos);

    float faceIndex = floor(min(fieldUV.x, 0.999) * 3.0) + 3.0 * floor(min(fieldUV.y, 0.999) * 2.0);
    float faceAxis = floor(faceIndex * 0.5);
    float faceSide = mix(-1.0, 1.0, step(0.5, mod(faceIndex, 2.0)));
    vec3 faceNormal = vec3(1.0 - step(0.5, faceAxis),
                           step(0.5, faceAxis) - step(1.5, faceAxis),
                           step(1.5, faceAxis)) * faceSide;

    float fresnel = pow(clamp(1.0 - abs(dot(faceNormal, viewDir)), 0.0, 1.0), FRESNEL_POWER);

    if (DebugFace >= 0.0 && abs(faceIndex - DebugFace) > 0.5) {
        discard;
    }

    float contactDist = textureLod(SamplerContactField, fieldUV, 0.0).r * FieldRange;

    if (DebugMode > 0.5 && DebugMode < 1.5) {
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

    fragColor = vec4(vertexColor.rgb, alpha);
}
