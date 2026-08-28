package com.bluup.hexwright.common.staff_assembly.calc;

public record PersistedStaffStats(double coreAttunement, ComponentResult wrap, ComponentResult focus, ComponentResult catalyst) {
    public static final PersistedStaffStats EMPTY = new PersistedStaffStats(0, ComponentResult.EMPTY, ComponentResult.EMPTY, ComponentResult.EMPTY);
}
