#version 150


uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform int FogShape;

uniform sampler3D DustField;
uniform sampler3D NoiseTex;
uniform vec4 Impacts[16];

uniform vec3 Origin;
uniform vec3 GridMin;
uniform vec3 GridSpan;
uniform vec3 GridRes;
uniform vec3 ProxyMin;
uniform vec3 ProxyMax;
uniform vec4 Band;
uniform vec4 Form;
uniform vec4 FormDir;
uniform vec4 Lighting;
uniform vec4 Life;
uniform vec4 Quality;
uniform vec4 Motion;
uniform vec3 ColorDark;
uniform vec3 ColorLight;
uniform vec3 ColorGlow;

in vec3 rayRel;

out vec4 fragColor;

float insideScale = 1.0;


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

const mat3 NOISE_SKEW = mat3(0.89101, 0.23382, -0.38915, 0.00000, 0.85717, 0.51504, 0.45399, -0.45890, 0.76374);

float vnoise(vec3 p) {
    return texture(NoiseTex, (NOISE_SKEW * p) * (1.0 / 64.0)).r;
}


vec3 travelStretch(vec3 q) {
    if (Motion.w <= 1.001) {
        return q;
    }
    float u = dot(q, Motion.xyz);
    float s = 1.0 + (Motion.w - 1.0) * (u < 0.0 ? 1.5 : 1.0);
    return q + Motion.xyz * (u / s - u);
}

float field(vec3 p) {
    vec3 q = travelStretch(p);
    vec3 f = (q - GridMin) / GridSpan;
    vec3 c = clamp(f, 0.0, 1.0);
    vec3 uvw = (c * (GridRes - 1.0) + 0.5) / GridRes;
    return texture(DustField, uvw.zyx).r + length((f - c) * GridSpan);
}

float ridged(vec3 p) {
    return 1.0 - abs(2.0 * vnoise(p) - 1.0);
}

float rough(vec3 q) {
    vec3 drift = vec3(0.0, Form.w * 0.0004, 0.0);
    float large = (vnoise(q * 0.9) - 0.5) * 2.0;
    float medium = ridged(q * 4.2 + 11.0) - 0.5;
    float fine = (vnoise(q * 11.0 + drift + 23.0) - 0.5) * 2.0;
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
    float disturbed = hurt * 8.0 * (vnoise(q * 14.0 + 31.0) - 0.5) * Band.z;
    float d = field(q) + rough(q) + ripple(q) + hurt + disturbed;
    return abs(d - Band.x) - Band.y;
}

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


float grainFade(float t) {
    float footprint = max(t * Lighting.z, 1.0e-5);
    return clamp(Band.w / footprint * 0.5 * Life.w - 0.4, 0.0, 1.0);
}

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

float shuffle(float id, float rate) {
    float t = Form.w * rate + id * 17.0;
    float step0 = floor(t);
    float frac = t - step0;
    float a = hash13(vec3(id * 91.7, step0, 3.1));
    float b = hash13(vec3(id * 91.7, step0 + 1.0, 3.1));
    return mix(a, b, smoothstep(0.0, 1.0, frac));
}

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

float buildOrder(vec3 q, float id) {
    vec3 n = q / max(length(q), 1.0e-3);
    float facing = 0.5 - 0.5 * dot(n, FormDir.xyz);
    float patchy = vnoise(q * 0.9 + 41.0);
    float order = Form.z > 0.5
        ? (1.0 - facing) * 0.45 + patchy * 0.55
        : facing * 0.6 + patchy * 0.4;
    return order + id * 0.08;
}

float presence(vec3 q, float id, float fade, float t, out float front, out float flash) {
    front = 1.0;
    flash = 0.0;
    bool settled = Form.y >= 0.999 && Form.z < 0.5;
    if (Form.x * insideScale >= 0.999 && settled && Lighting.w < 0.5 && t > 0.8) {
        return 1.0;
    }
    float coarseHoles = vnoise(q * 1.6 + 5.0);
    float fineHoles = vnoise(q * 3.4 + 9.0);
    float holes = smoothstep(0.2, 0.8, coarseHoles * 0.6 + fineHoles * 0.4);
    float grainy = mix(0.22, 0.1, smoothstep(0.3, 0.95, Form.x)) * fade;
    float v = holes * (1.0 - grainy) + id * grainy;
    float waver = (mix(coarseHoles, fineHoles, 0.5 + 0.5 * sin(Form.w * 0.02)) - 0.5) * 0.07 * Life.z;
    float cover = Form.x * insideScale * 1.02 + waver - damage(q, flash) * 1.1;
    if (!settled) {
        front = Form.y * 1.1 - buildOrder(q, id);
    }
    float p = min(cover - v, front);
    return min(p, t / 0.7 - id);
}


float fogDistance(vec3 pos) {
    if (FogShape == 0) {
        return length(pos);
    }
    return max(length(pos.xz), abs(pos.y));
}

void shade(vec3 q, vec3 dir, float t, float fade, float f1, float id, float front, float flash) {
    vec3 n = bodyNormal(q);
    bool innerFace = dot(n, dir) > 0.0;
    if (innerFace) {
        n = -n;
    }
    float compaction = smoothstep(0.3, 0.95, Form.x);

    float f2 = 0.5;
    float id2 = id;
    if (fade > 0.15) {
        vec3 fq = q + vec3(0.0, Form.w * 0.002, 0.0);
        float wobble = vnoise(fq * (2.0 / Band.w)) - 0.5;
        id2 = hash13(floor((fq + wobble * Band.w * vec3(0.9, -0.7, 0.5)) / (Band.w * 0.45)) + 0.5);
    }
    vec3 facet = hash33(vec3(id, id2, id * 3.7 + id2) * 37.0) - 0.5;
    vec3 lit = normalize(n + facet * mix(0.5, 0.26, compaction) * fade);
    float ridge = ridged(q * 4.2 + 11.0);
    float broad = vnoise(q * 0.9) * 0.65 + vnoise(q * 2.1 + 7.0) * 0.35;

    float unsettled = clamp(1.0 - Form.y, 0.0, 1.0) + Form.z * 0.6;
    float rate = Life.y * (1.0 + 2.5 * unsettled) * (0.35 + 1.3 * broad);
    float grit = mix(0.5, id * 0.5 + shuffle(id2, rate) * 0.5, fade);

    float tone = clamp(0.44 + 0.34 * (broad - 0.5) + 0.30 * (grit - 0.5)
        + 0.2 * (id - 0.5) * mix(1.0, 0.45, compaction) * fade - 0.18 * (ridge - 0.5), 0.0, 1.0);
    vec3 albedo = mix(ColorDark, ColorLight, tone);

    float ndl = dot(lit, normalize(vec3(0.35, 1.0, 0.25)));
    float diffuse = 0.32 + 0.68 * clamp(ndl * 0.8 + 0.2, 0.0, 1.0);
    float ambient = mix(0.78, 1.06, lit.y * 0.5 + 0.5);
    float crevice = smoothstep(0.5, 0.95, f1) * fade;
    float cavity = mix(0.78, 1.0, ridge) * mix(1.0, 0.82, crevice);
    vec3 color = albedo * diffuse * ambient * cavity * Lighting.x;
    if (innerFace && insideScale >= 1.0) {
        color *= 0.55;
    }

    color += ColorGlow * Lighting.y * (0.55 + 0.45 * grit) * cavity;

    float rim = 1.0 - abs(dot(n, dir));
    rim = smoothstep(0.55, 1.0, rim);
    color += ColorGlow * Lighting.y * rim * (0.3 + 0.7 * grit) * 1.6;

    float glint = sparkle(id, rim + unsettled * 0.5, clamp(flash * 2.0, 0.0, 1.0));
    color += ColorGlow * glint * Life.x * (0.5 + 0.5 * Lighting.x);
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
        discard;
    }

    float t = max(tEnter, 0.05);
    insideScale = field(ro) < Band.x + Band.y ? 0.6 : 1.0;
    int steps = int(Quality.x);
    int rejectBudget = int(Quality.z);
    if (insideScale < 1.0) {
        rejectBudget = min(rejectBudget, 3);
    } else {
        rejectBudget = int(float(rejectBudget) * (0.35 + 0.65 * Form.x));
    }
    int rejects = 0;
    float outside = t;
    bool crossed = false;
    for (int i = 0; i < 256; i++) {
        if (i >= steps || t > tExit) {
            break;
        }
        vec3 q = ro + dir * t;
        float coarse = min(0.004 + t * Quality.w, max(0.02, Band.y * 0.7));
        float plain;
        float bound = bodyBound(q, plain);
        if (bound > 0.05) {
            if (crossed && Form.x < 0.999 && plain < Band.x - Band.y) {
                break;
            }
            outside = t;
            t += max(bound, coarse);
            continue;
        }
        crossed = true;
        float d = body(q);
        if (d < 0.004) {
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
            outside = t;
            t += max(Band.w * 1.1 + max(-d, 0.0) * 0.6, coarse);
            if (++rejects > rejectBudget) {
                break;
            }
            continue;
        }
        outside = t;
        t += max(d * Quality.y, coarse);
    }
    discard;
}
