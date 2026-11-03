package com.bluup.hexwright.server.hexweave;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent;
import org.jetbrains.annotations.Nullable;

public final class WeaveExecutionBudget implements CastingEnvironmentComponent {

    public static final int MAX_INVOCATIONS = 64;

    private static final Key<WeaveExecutionBudget> KEY = new Key<>() {
    };

    private long opsConsumed;
    private int invocations;
    private boolean opsExhausted;

    public static @Nullable WeaveExecutionBudget of(CastingEnvironment env) {
        return env.getExtension(KEY);
    }

    @Override
    public Key<?> getKey() {
        return KEY;
    }

    public long opsConsumed() {
        return opsConsumed;
    }

    public int invocations() {
        return invocations;
    }

    boolean invocationsExhausted() {
        return invocations >= MAX_INVOCATIONS;
    }

    boolean opsExhausted(int maxOpCount) {
        return opsExhausted || opsConsumed >= maxOpCount;
    }

    void recordRun(long opsBefore, long opsAfter, boolean ranOutOfOps) {
        invocations++;
        opsConsumed += Math.max(0L, opsAfter - opsBefore);
        opsExhausted |= ranOutOfOps;
    }
}
