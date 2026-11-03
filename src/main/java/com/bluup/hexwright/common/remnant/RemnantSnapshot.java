package com.bluup.hexwright.common.remnant;

import java.util.List;
import java.util.UUID;

public record RemnantSnapshot(List<Remnant> remnants, long capturedAt, UUID id) {

    public static final long GRACE_TICKS = RemnantDecay.GRACE_TICKS;

    public static final long DECAY_TICKS = RemnantDecay.DECAY_TICKS;

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
        return RemnantDecay.potency(capturedAt, now);
    }

    public boolean isSpent(long now) {
        return potencyAt(now) <= 0.0;
    }
}
