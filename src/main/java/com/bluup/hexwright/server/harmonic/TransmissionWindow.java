package com.bluup.hexwright.server.harmonic;

import java.util.Arrays;

public final class TransmissionWindow {

    public static final int WINDOW_SECONDS = 60;

    private final int[] buckets = new int[WINDOW_SECONDS];
    private long lastSecond = Long.MIN_VALUE;

    public void record(long gameTime) {
        advance(gameTime);
        buckets[(int) Math.floorMod(gameTime / 20L, WINDOW_SECONDS)]++;
    }

    public int lastMinute(long gameTime) {
        advance(gameTime);
        int total = 0;
        for (int count : buckets) {
            total += count;
        }
        return total;
    }

    private void advance(long gameTime) {
        long second = gameTime / 20L;
        if (lastSecond == Long.MIN_VALUE) {
            lastSecond = second;
            return;
        }
        if (second <= lastSecond) {
            if (second < lastSecond) {
                Arrays.fill(buckets, 0);
                lastSecond = second;
            }
            return;
        }
        long elapsed = second - lastSecond;
        if (elapsed >= WINDOW_SECONDS) {
            Arrays.fill(buckets, 0);
        } else {
            for (long s = lastSecond + 1; s <= second; s++) {
                buckets[(int) Math.floorMod(s, WINDOW_SECONDS)] = 0;
            }
        }
        lastSecond = second;
    }
}
