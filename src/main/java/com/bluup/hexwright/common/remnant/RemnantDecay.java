package com.bluup.hexwright.common.remnant;

public final class RemnantDecay {

    public static final long GRACE_TICKS = 6_000L;

    public static final long DECAY_TICKS = 72_000L;

    public static final long HORIZON_TICKS = GRACE_TICKS + DECAY_TICKS;

    private RemnantDecay() {
    }

    public static double potency(long takenAt, long now) {
        long age = now - takenAt;
        if (age <= GRACE_TICKS) {
            return 1.0;
        }
        long past = age - GRACE_TICKS;
        if (past >= DECAY_TICKS) {
            return 0.0;
        }
        return 1.0 - ((double) past / (double) DECAY_TICKS);
    }
}
