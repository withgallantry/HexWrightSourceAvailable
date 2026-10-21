package com.bluup.hexwright.client.inspiration;

import com.bluup.hexwright.server.inspiration.InspirationLevels;
import net.minecraft.Util;

public final class ClientInspiration {

    private static volatile int total;
    private static volatile boolean synced;
    private static volatile long gainedAtMillis = Long.MIN_VALUE;

    private ClientInspiration() {
    }

    public static void set(int inspiration) {
        int next = Math.max(0, inspiration);
        if (synced && next > total) {
            gainedAtMillis = Util.getMillis();
        }
        total = next;
        synced = true;
    }

    public static void clear() {
        total = 0;
        synced = false;
        gainedAtMillis = Long.MIN_VALUE;
    }

    public static int total() {
        return total;
    }

    public static int levels() {
        return InspirationLevels.levels(total);
    }

    public static float fraction() {
        return InspirationLevels.fraction(total);
    }

    public static long millisSinceGain() {
        long gainedAt = gainedAtMillis;
        return gainedAt == Long.MIN_VALUE ? Long.MAX_VALUE : Util.getMillis() - gainedAt;
    }
}
