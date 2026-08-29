package com.bluup.hexwright.common.remnant;

public record Remnant(RemnantType type, double drams) {

    public Remnant {
        drams = Math.max(0.0, drams);
    }

    public boolean isEmpty() {
        return drams <= 0.0;
    }

    public Remnant withDrams(double newDrams) {
        return new Remnant(type, newDrams);
    }

    public int wholeDrams() {
        return (int) Math.floor(drams);
    }
}
