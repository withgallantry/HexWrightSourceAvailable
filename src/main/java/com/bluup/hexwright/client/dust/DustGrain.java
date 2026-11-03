package com.bluup.hexwright.client.dust;

public final class DustGrain {
    public float x;
    public float y;
    public float z;
    public float vx;
    public float vy;
    public float vz;
    public float life;

    public float radius;
    public float height;
    public float current;
    public float phase;
    public float turbulence;
    public float spread;
    public float lead;
    public float speed;

    void load(int identity) {
        int h = mix(identity);
        radius = unit(h);
        h = mix(h + 0x9E3779B9);
        height = unit(h);
        h = mix(h + 0x9E3779B9);
        current = unit(h);
        h = mix(h + 0x9E3779B9);
        phase = unit(h) * (float) (Math.PI * 2.0);
        h = mix(h + 0x9E3779B9);
        turbulence = unit(h);
        h = mix(h + 0x9E3779B9);
        spread = unit(h);
        h = mix(h + 0x9E3779B9);
        lead = unit(h);
        h = mix(h + 0x9E3779B9);
        speed = unit(h);
    }

    private static int mix(int h) {
        h ^= h >>> 16;
        h *= 0x7FEB352D;
        h ^= h >>> 15;
        h *= 0x846CA68B;
        h ^= h >>> 16;
        return h;
    }

    private static float unit(int h) {
        return (h >>> 8) * 0x1.0p-24f;
    }
}
