package com.bluup.hexwright.common.remnant;

import java.util.List;
import java.util.UUID;

public record RemnantSnapshot(List<Remnant> remnants, long capturedAt, UUID id) {

    public static final long GRACE_TICKS = 6_000L;

    public static final long DECAY_TICKS = 72_000L;

    public RemnantSnapshot {
        remnants = List.copyOf(remnants);
    }

    @Override
    public UUID id() {
        return id;
    }

    public static UUID legacyId(List<Remnant> remnants, long capturedAt) {
        return new UUID(capturedAt, remnants.hashCode());
    }

    public boolean isEmpty() {
        return remnants.isEmpty();
    }

    public double potencyAt(long now) {
        long age = now - capturedAt;
        if (age <= GRACE_TICKS) {
            return 1.0;
        }
        long past = age - GRACE_TICKS;
        if (past >= DECAY_TICKS) {
            return 0.0;
        }
        return 1.0 - ((double) past / (double) DECAY_TICKS);
    }

    public boolean isSpent(long now) {
        return potencyAt(now) <= 0.0;
    }
}
