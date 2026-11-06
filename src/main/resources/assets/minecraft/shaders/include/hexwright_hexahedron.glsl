
float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float hash(vec3 p) {
    vec3 p3 = fract(p * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float low = mix(mix(hash(i), hash(i + vec3(1.0, 0.0, 0.0)), u.x),
                    mix(hash(i + vec3(0.0, 1.0, 0.0)), hash(i + vec3(1.0, 1.0, 0.0)), u.x), u.y);
    float high = mix(mix(hash(i + vec3(0.0, 0.0, 1.0)), hash(i + vec3(1.0, 0.0, 1.0)), u.x),
                     mix(hash(i + vec3(0.0, 1.0, 1.0)), hash(i + vec3(1.0, 1.0, 1.0)), u.x), u.y);
    return mix(low, high, u.z);
}

float fbm(vec2 p, int octaves) {
    float sum = 0.0;
    float amplitude = 0.5;
    float total = 0.0;
    for (int octave = 0; octave < octaves; octave++) {
        sum += amplitude * noise(p);
        total += amplitude;
        p = p * 2.07 + vec2(1.7, 9.2);
        amplitude *= 0.5;
    }
    return sum / total;
}

vec3 fire(float heat) {
    vec3 c = mix(vec3(0.03, 0.05, 0.30), vec3(0.07, 0.30, 1.0), smoothstep(0.0, 0.35, heat));
    c = mix(c, vec3(0.35, 0.78, 1.0), smoothstep(0.35, 0.7, heat));
    return mix(c, vec3(0.92, 0.98, 1.0), smoothstep(0.7, 1.0, heat));
}
