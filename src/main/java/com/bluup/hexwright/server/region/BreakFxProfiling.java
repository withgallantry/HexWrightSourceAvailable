package com.bluup.hexwright.server.region;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class BreakFxProfiling {

    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static final AtomicBoolean SUPPRESS = new AtomicBoolean(false);
    private static final AtomicLong COUNT = new AtomicLong(0);

    private BreakFxProfiling() {
    }

    public static void begin(boolean suppress) {
        COUNT.set(0);
        SUPPRESS.set(suppress);
        ACTIVE.set(true);
    }

    public static long end() {
        ACTIVE.set(false);
        return COUNT.get();
    }

    public static boolean onLevelEvent(int id) {
        if (!ACTIVE.get() || id != 2001) {
            return false;
        }
        COUNT.incrementAndGet();
        return SUPPRESS.get();
    }
}
