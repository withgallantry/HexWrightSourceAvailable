package com.bluup.hexwright.client.tooltip;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

final class TooltipSoulfire {

    private static final int INSET = 5;
    private static final int CORNER = 14;

    private static final int WHITE = 0xF2FFFF;
    private static final int BRIGHT = 0x8AF6F6;
    private static final int MID = 0x2FD4E0;
    private static final int DEEP = 0x137F9A;
    private static final int DARK = 0x0A3F52;

    private static final int HALO_RINGS = 9;
    private static final float HALO_ALPHA = 0.55F;
    private static final double HALO_PERIOD = 2.4;

    private static final float FLAME_MIN = 2.0F;
    private static final float FLAME_RANGE = 12.0F;
    private static final float SIDE_FLAME_RANGE = 6.0F;

    private static final double COMET_SPEED = 70.0;
    private static final int COMET_TAIL = 22;

    private static final int EMBER_SPACING = 14;
    private static final float EMBER_RISE = 26.0F;

    private static final double CRACKLE_PERIOD = 2.7;
    private static final double CRACKLE_LIFE = 0.16;

    private static final double SWEEP_PERIOD = 4.5;
    private static final double SWEEP_TIME = 1.1;
    private static final int SWEEP_HALF_WIDTH = 8;
    private static final int TITLE_TOP = 7;
    private static final int TITLE_BOTTOM = 20;

    private TooltipSoulfire() {
    }

    static void behind(GuiGraphics graphics, int left, int top, int width, int height) {
        double seconds = seconds();
        float pulse = 0.65F + 0.35F * Mth.sin((float) (seconds / HALO_PERIOD * Mth.TWO_PI));
        int x0 = left + INSET;
        int y0 = top + INSET;
        int x1 = left + width - INSET;
        int y1 = top + height - INSET;
        for (int ring = 1; ring <= HALO_RINGS; ring++) {
            float fall = 1.0F - (ring - 1) / (float) HALO_RINGS;
            int argb = argb(HALO_ALPHA * pulse * fall * fall, ring <= 2 ? BRIGHT : ring <= 5 ? MID : DEEP);
            outline(graphics, x0 - ring, y0 - ring, x1 + ring, y1 + ring, argb);
        }
    }

    static void front(GuiGraphics graphics, int left, int top, int width, int height) {
        double seconds = seconds();
        int x0 = left + INSET;
        int y0 = top + INSET;
        int x1 = left + width - INSET - 1;
        int y1 = top + height - INSET - 1;

        flames(graphics, x0, x1, y0, seconds);
        sideFlames(graphics, x0, x1, y0, y1, seconds);
        sweep(graphics, left, top, width, seconds);

        Long2IntOpenHashMap dots = new Long2IntOpenHashMap();
        comets(dots, x0, y0, x1, y1, seconds);
        embers(dots, x0, y0, x1, y1, seconds);
        crackle(dots, x0, y0, x1, y1, seconds);
        for (Long2IntMap.Entry entry : dots.long2IntEntrySet()) {
            int x = (int) (entry.getLongKey() >> 32);
            int y = (int) entry.getLongKey();
            graphics.fill(x, y, x + 1, y + 1, entry.getIntValue());
        }
    }

    private static void flames(GuiGraphics graphics, int x0, int x1, int y0, double seconds) {
        int from = x0 + CORNER;
        int to = x1 - CORNER;
        for (int x = from; x <= to; x++) {
            float edge = Math.min(1.0F, Math.min(x - from, to - x) / 8.0F);
            float n = noise(x * 0.13, seconds * 1.9) * 0.7F + noise(x * 0.47 + 40.0, seconds * 4.7) * 0.3F;
            float tongue = Mth.sin((float) (x * 0.42 - seconds * 3.1 + n * 3.0));
            tongue = tongue * tongue;
            int height = Math.round((FLAME_MIN + FLAME_RANGE * (float) Math.pow(n, 1.6))
                * (0.45F + 0.55F * tongue) * (0.3F + 0.7F * edge));
            if (height <= 0) {
                continue;
            }
            int root = y0 - 1;
            band(graphics, x, root, height, 0.0F, 0.15F, WHITE, 0.95F);
            band(graphics, x, root, height, 0.15F, 0.4F, BRIGHT, 0.9F);
            band(graphics, x, root, height, 0.4F, 0.7F, MID, 0.8F);
            band(graphics, x, root, height, 0.7F, 1.0F, DEEP, 0.55F);
        }
    }

    private static void sideFlames(GuiGraphics graphics, int x0, int x1, int y0, int y1, double seconds) {
        int from = y0 + CORNER;
        int to = y1 - CORNER;
        int span = Math.max(1, to - from);
        for (int side = 0; side < 2; side++) {
            int root = side == 0 ? x0 - 1 : x1 + 1;
            int out = side == 0 ? -1 : 1;
            for (int y = from; y <= to; y++) {
                float edge = Math.min(1.0F, Math.min(y - from, to - y) / 8.0F);
                float climb = 0.45F + 0.55F * (1.0F - (y - from) / (float) span);
                float n = noise(y * 0.21 + seconds * 2.6 + side * 57.0, seconds * 0.7);
                int length = Math.round((1.0F + SIDE_FLAME_RANGE * (float) Math.pow(n, 1.8)) * climb * edge);
                if (length <= 0) {
                    continue;
                }
                sideBand(graphics, root, y, out, length, 0.0F, 0.25F, BRIGHT, 0.85F);
                sideBand(graphics, root, y, out, length, 0.25F, 0.6F, MID, 0.7F);
                sideBand(graphics, root, y, out, length, 0.6F, 1.0F, DEEP, 0.45F);
            }
        }
    }

    private static void sideBand(GuiGraphics graphics, int root, int y, int out, int length, float from, float to,
                             int rgb, float alpha) {
        int near = root + out * Math.round(length * from);
        int far = root + out * Math.round(length * to);
        if (near != far) {
            graphics.fill(Math.min(near, far), y, Math.max(near, far), y + 1, argb(alpha, rgb));
        }
    }

    private static void band(GuiGraphics graphics, int x, int root, int height, float from, float to,
                             int rgb, float alpha) {
        int low = root - Math.round(height * from);
        int high = root - Math.round(height * to);
        if (high < low) {
            graphics.fill(x, high + 1, x + 1, low + 1, argb(alpha, rgb));
        }
    }

    private static void sweep(GuiGraphics graphics, int left, int top, int width, double seconds) {
        double phase = seconds % SWEEP_PERIOD;
        if (phase > SWEEP_TIME) {
            return;
        }
        double centre = left - 20 + (width + 40) * (phase / SWEEP_TIME);
        for (int y = top + TITLE_TOP; y <= top + TITLE_BOTTOM; y++) {
            int mid = (int) Math.round(centre + (y - top) * 0.7);
            for (int dx = -SWEEP_HALF_WIDTH; dx <= SWEEP_HALF_WIDTH; dx++) {
                int x = mid + dx;
                if (x < left + INSET + 1 || x >= left + width - INSET - 1) {
                    continue;
                }
                float alpha = 0.16F * (1.0F - Math.abs(dx) / (float) (SWEEP_HALF_WIDTH + 1));
                graphics.fill(x, y, x + 1, y + 1, argb(alpha, WHITE));
            }
        }
    }

    private static void comets(Long2IntOpenHashMap dots, int x0, int y0, int x1, int y1, double seconds) {
        int perimeter = 2 * ((x1 - x0) + (y1 - y0));
        if (perimeter <= 0) {
            return;
        }
        double head = (seconds * COMET_SPEED) % perimeter;
        for (int comet = 0; comet < 2; comet++) {
            int direction = comet == 0 ? 1 : -1;
            double start = comet == 0 ? head : perimeter - head + perimeter / 2.0;
            for (int k = 0; k < COMET_TAIL; k++) {
                int s = Math.floorMod((int) Math.round(start - direction * k), perimeter);
                long point = perimeterPoint(s, x0, y0, x1, y1);
                int x = (int) (point >> 32);
                int y = (int) point;
                float bright = (float) Math.pow(1.0F - k / (float) COMET_TAIL, 1.5);
                put(dots, x, y, bright, k < 3 ? WHITE : body(bright));
                if (k < 2) {
                    put(dots, x + 1, y + 1, bright, WHITE);
                }
                put(dots, x - 1, y, bright * 0.35F, MID);
                put(dots, x + 1, y, bright * 0.35F, MID);
                put(dots, x, y - 1, bright * 0.35F, MID);
                put(dots, x, y + 1, bright * 0.35F, MID);
            }
        }
    }

    private static long perimeterPoint(int s, int x0, int y0, int x1, int y1) {
        int w = x1 - x0;
        int h = y1 - y0;
        int x;
        int y;
        if (s < w) {
            x = x0 + s;
            y = y0;
        } else if (s < w + h) {
            x = x1;
            y = y0 + (s - w);
        } else if (s < 2 * w + h) {
            x = x1 - (s - w - h);
            y = y1;
        } else {
            x = x0;
            y = y1 - (s - 2 * w - h);
        }
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static void embers(Long2IntOpenHashMap dots, int x0, int y0, int x1, int y1, double seconds) {
        int top = x1 - x0;
        int side = (y1 - y0) * 2 / 3;
        int span = top + 2 * side;
        int count = Math.max(1, span / EMBER_SPACING);
        for (int slot = 0; slot < count; slot++) {
            long seed = HashCommon.mix(slot * 7919L + 31L);
            double period = 1.5 + random(seed, 0) * 1.1;
            double position = seconds / period + random(seed, 1);
            long cycle = (long) Math.floor(position);
            float age = (float) (position - cycle);
            long roll = seed + cycle * 0x9E3779B97F4A7C15L;
            if (random(roll, 0) < 0.25F) {
                continue;
            }
            int along = (int) (random(roll, 1) * span);
            float startX;
            float startY;
            float out;
            if (along < top) {
                startX = x0 + along;
                startY = y0;
                out = 0.0F;
            } else if (along < top + side) {
                startX = x0;
                startY = y0 + (along - top);
                out = -1.0F;
            } else {
                startX = x1;
                startY = y0 + (along - top - side);
                out = 1.0F;
            }
            float rise = EMBER_RISE * (0.6F + 0.4F * random(roll, 2)) * age;
            float sway = Mth.sin((float) (age * Mth.TWO_PI * 1.5 + random(roll, 3) * Mth.TWO_PI)) * 2.0F;
            int x = Math.round(startX + out * (3.0F + 6.0F * age) + sway);
            int y = Math.round(startY - rise);
            float bright = (age < 0.15F ? age / 0.15F : 1.0F - (age - 0.15F) / 0.85F)
                * (0.7F + 0.3F * Mth.sin((float) (seconds * 23.0 + slot)));
            put(dots, x, y, bright, age < 0.4F ? WHITE : body(bright));
            put(dots, x, y + 1, bright * 0.45F, MID);
        }
    }

    private static void crackle(Long2IntOpenHashMap dots, int x0, int y0, int x1, int y1, double seconds) {
        long cycle = (long) Math.floor(seconds / CRACKLE_PERIOD);
        double phase = seconds - cycle * CRACKLE_PERIOD;
        if (phase > CRACKLE_LIFE) {
            return;
        }
        long roll = HashCommon.mix(cycle * 0xC2B2AE3D27D4EB4FL + 17L);
        if (random(roll, 0) < 0.35F) {
            return;
        }
        float fade = 1.0F - (float) (phase / CRACKLE_LIFE);
        int edge = (int) (random(roll, 1) * 4);
        boolean horizontal = edge < 2;
        int span = horizontal ? x1 - x0 : y1 - y0;
        int length = Math.min(span - 2 * CORNER, 18 + (int) (random(roll, 2) * 26));
        if (length <= 4) {
            return;
        }
        int start = CORNER + (int) (random(roll, 3) * (span - 2 * CORNER - length));
        int outward = edge == 0 || edge == 2 ? -1 : 1;
        int prevX = 0;
        int prevY = 0;
        for (int step = 0; step <= length; step += 3) {
            int jitter = Math.round((random(roll, 10 + step) - 0.35F) * 5.0F) * outward;
            int x;
            int y;
            if (horizontal) {
                x = x0 + start + step;
                y = (edge == 0 ? y0 : y1) + jitter;
            } else {
                x = (edge == 2 ? x0 : x1) + jitter;
                y = y0 + start + step;
            }
            if (step > 0) {
                line(dots, prevX, prevY, x, y, fade);
            }
            prevX = x;
            prevY = y;
        }
    }

    private static void line(Long2IntOpenHashMap dots, int ax, int ay, int bx, int by, float bright) {
        int steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        for (int i = 0; i <= steps; i++) {
            int x = ax + Math.round((bx - ax) * (i / (float) Math.max(1, steps)));
            int y = ay + Math.round((by - ay) * (i / (float) Math.max(1, steps)));
            put(dots, x, y, bright, WHITE);
            put(dots, x + 1, y, bright * 0.5F, BRIGHT);
            put(dots, x, y + 1, bright * 0.5F, BRIGHT);
        }
    }

    private static void outline(GuiGraphics graphics, int x0, int y0, int x1, int y1, int argb) {
        graphics.fill(x0, y0, x1, y0 + 1, argb);
        graphics.fill(x0, y1 - 1, x1, y1, argb);
        graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, argb);
        graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, argb);
    }

    private static void put(Long2IntOpenHashMap dots, int x, int y, float bright, int rgb) {
        if (bright <= 0.05F) {
            return;
        }
        long key = ((long) x << 32) | (y & 0xFFFFFFFFL);
        int argb = argb(Math.min(1.0F, bright * 1.3F), rgb);
        int existing = dots.getOrDefault(key, 0);
        if ((existing >>> 24) < (argb >>> 24)) {
            dots.put(key, argb);
        }
    }

    private static int body(float bright) {
        if (bright >= 0.75F) {
            return BRIGHT;
        }
        return bright >= 0.45F ? MID : bright >= 0.25F ? DEEP : DARK;
    }

    private static int argb(float alpha, int rgb) {
        return Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24 | rgb;
    }

    private static float noise(double x, double y) {
        int ix = Mth.floor(x);
        int iy = Mth.floor(y);
        float fx = ease((float) (x - ix));
        float fy = ease((float) (y - iy));
        float a = lattice(ix, iy);
        float b = lattice(ix + 1, iy);
        float c = lattice(ix, iy + 1);
        float d = lattice(ix + 1, iy + 1);
        return Mth.lerp(fy, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
    }

    private static float ease(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private static float lattice(int x, int y) {
        return random(HashCommon.mix(x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL), 0);
    }

    private static float random(long key, int salt) {
        return (HashCommon.mix(key + salt * 0xC2B2AE3D27D4EB4FL) >>> 40) / (float) (1L << 24);
    }

    private static double seconds() {
        return (System.currentTimeMillis() % 3_600_000L) / 1000.0;
    }
}
