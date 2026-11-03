package com.bluup.hexwright.common.remnant;

import org.jetbrains.annotations.Nullable;

public record Remnant(@Nullable RemnantType type, double drams) {

    public static final Remnant EMPTY = new Remnant(null, 0.0);

    public Remnant {
        drams = Math.max(0.0, drams);
    }

    public boolean isEmpty() {
        return type == null || drams <= 0.0;
    }

    public Remnant withDrams(double newDrams) {
        return type == null ? EMPTY : new Remnant(type, newDrams);
    }

    public int wholeDrams() {
        return (int) Math.floor(drams);
    }
}
