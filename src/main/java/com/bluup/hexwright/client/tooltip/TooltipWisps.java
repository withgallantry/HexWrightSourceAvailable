package com.bluup.hexwright.client.tooltip;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

final class TooltipWisps {

    private static final int SPACING = 16;
    private static final int CORNER = 13;
    private static final int INSET = 5;
    private static final float STEP = 0.5F;

    private static final int WHITE = 0xFFFFFF;
    private static final int BRIGHT = 0xDAE2EE;
    private static final int MID = 0xA6B4C8;
    private static final int DIM = 0x707C92;
    private static final int EDGE = 0x96C4E2;
    private static final int SHADE = 0x606C82;
    private static final int HALO = 0xBECDE1;

    private TooltipWisps() {
    }

    static void render(GuiGraphics graphics, int left, int top, int width, int height) {
        double seconds = System.currentTimeMillis() / 1000.0;
        Long2ObjectOpenHashMap<Dot> dots = new Long2ObjectOpenHashMap<>();
        edge(dots, seconds, 0, left, top + INSET, 1, 0, 0, -1, width, 0.0F);
        edge(dots, seconds, 1, left, top + height - 1 - INSET, 1, 0, 0, 1, width, 0.6F);
        edge(dots, seconds, 2, left + INSET, top, 0, 1, -1, 0, height, 1.0F);
        edge(dots, seconds, 3, left + width - 1 - INSET, top, 0, 1, 1, 0, height, 1.0F);

        for (Long2ObjectMap.Entry<Dot> entry : dots.long2ObjectEntrySet()) {
            int x = (int) (entry.getLongKey() >> 32);
            int y = (int) entry.getLongKey();
            graphics.fill(x, y, x + 1, y + 1, entry.getValue().argb());
        }
    }

    private static void edge(Long2ObjectOpenHashMap<Dot> dots, double seconds, int edge,
                             int startX, int startY, int tx, int ty, int nx, int ny, int span, float rise) {
        int usable = span - 2 * CORNER;
        if (usable <= 0) {
            return;
        }
        int count = Math.max(1, usable / SPACING);
        for (int slot = 0; slot < count; slot++) {
            long seed = HashCommon.mix(edge * 7919L + slot * 104729L + 1L);
            double period = 1.4 + random(seed, 0) * 0.9;
            double position = seconds / period + random(seed, 1);
            long cycle = (long) Math.floor(position);
            float age = (float) (position - cycle);
            long shape = seed + cycle * 0x9E3779B97F4A7C15L;
            if (random(shape, 0) < 0.25F) {
                continue;
            }
            float along = CORNER + (slot + 0.5F) * usable / count + (random(shape, 1) - 0.5F) * SPACING * 0.6F;
            float length = 12.0F + random(shape, 2) * 7.0F;
            float sway = 0.8F + random(shape, 3) * 1.4F;
            int curl = random(shape, 4) < 0.5F ? -1 : 1;
            float phase = random(shape, 5) * Mth.TWO_PI;
            wisp(dots, startX + tx * along, startY + ty * along, tx, ty, nx, ny,
                age, length, sway, curl, phase, rise);
        }
    }

    private static void wisp(Long2ObjectOpenHashMap<Dot> dots, float rootX, float rootY,
                             int tx, int ty, int nx, int ny,
                             float age, float length, float sway, int curl, float phase, float rise) {
        float grow = Math.min(1.0F, age / 0.4F);
        float head = length * (1.0F - (1.0F - grow) * (1.0F - grow)) + Math.max(0.0F, age - 0.4F) * length * 0.35F;
        float tail = head * (float) Math.pow(Math.max(0.0F, (age - 0.3F) / 0.7F), 0.9);
        float drift = Math.max(0.0F, age - 0.3F) * length * 0.45F;
        float fade = fade(age);
        if (head - tail < 0.5F || fade <= 0.0F) {
            return;
        }
        boolean bulb = age < 0.3F;
        float lateral = 0.0F;
        float out = 0.0F;
        for (float s = 0.0F; s <= head; s += STEP) {
            if (s >= tail) {
                float u = (s - tail) / Math.max(1.0F, head - tail);
                float bright = (age < 0.4F ? 0.55F + 0.45F * u : 1.0F - 0.5F * u) * fade;
                float distance = out + drift;
                float lift = rise * (distance / length) * (distance / length) * 4.0F;
                int x = Math.round(rootX + nx * distance + tx * lateral);
                int y = Math.round(rootY + ny * distance + ty * lateral - lift);
                put(dots, x, y, bright, 0);
                if (bulb && head - s < 3.0F) {
                    put(dots, x + tx * curl, y + ty * curl, bright * 0.9F, EDGE);
                    put(dots, x - tx * curl, y - ty * curl, bright * 0.6F, SHADE);
                } else if (u < 0.5F && age < 0.55F) {
                    put(dots, x + tx * curl, y + ty * curl, bright * 0.6F, EDGE);
                }
                float halo = bright * 0.3F;
                put(dots, x + 1, y, halo, HALO);
                put(dots, x - 1, y, halo, HALO);
                put(dots, x, y + 1, halo, HALO);
                put(dots, x, y - 1, halo, HALO);
            }
            float bend = Math.max(0.0F, s - 0.3F * length) / length;
            float heading = sway * 0.25F * Mth.sin(s * 0.45F + phase) + curl * 4.0F * (float) Math.pow(bend, 1.5);
            lateral += Mth.sin(heading) * STEP;
            out += Mth.cos(heading) * STEP;
        }
    }

    private static float fade(float age) {
        if (age < 0.08F) {
            return age / 0.08F;
        }
        return age < 0.5F ? 1.0F : (1.0F - age) / 0.5F;
    }

    private static void put(Long2ObjectOpenHashMap<Dot> dots, int x, int y, float bright, int colour) {
        if (bright <= 0.05F) {
            return;
        }
        long key = ((long) x << 32) | (y & 0xFFFFFFFFL);
        Dot existing = dots.get(key);
        if (existing == null || existing.bright() < bright) {
            dots.put(key, new Dot(bright, colour == 0 ? body(bright) : colour));
        }
    }

    private static int body(float bright) {
        if (bright >= 0.78F) {
            return WHITE;
        }
        if (bright >= 0.55F) {
            return BRIGHT;
        }
        return bright >= 0.34F ? MID : DIM;
    }

    private static float random(long key, int salt) {
        return (HashCommon.mix(key + salt * 0xC2B2AE3D27D4EB4FL) >>> 40) / (float) (1L << 24);
    }

    private record Dot(float bright, int rgb) {
        int argb() {
            int alpha = Math.min(255, Math.round(this.bright * 1.4F * 255.0F));
            return alpha << 24 | this.rgb;
        }
    }
}
