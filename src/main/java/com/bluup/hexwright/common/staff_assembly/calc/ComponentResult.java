package com.bluup.hexwright.common.staff_assembly.calc;

public record ComponentResult(double output, double statValue, double usableValue, double attunementCost, double efficiency, int itemCount) {
    public static final ComponentResult EMPTY = new ComponentResult(0, 0, 0, 0, 0, 0);
}
