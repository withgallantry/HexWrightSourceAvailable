package com.bluup.hexwright.common.staff_assembly;

public enum StaffPartCategory {
    MODEL("Model"),
    CORE("Core"),
    BINDING("Binding"),
    FOCUS("Focus"),
    CATALYST("Catalyst");

    private final String label;

    StaffPartCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
