package com.bluup.hexwright.server.fluid;

import net.minecraft.util.StringRepresentable;

public enum TankPart implements StringRepresentable {
    SOLO("solo"),
    BOTTOM("bottom"),
    MIDDLE("middle"),
    TOP("top");

    private final String name;

    TankPart(String name) {
        this.name = name;
    }

    public static TankPart of(boolean tankBelow, boolean tankAbove) {
        if (tankBelow) {
            return tankAbove ? MIDDLE : TOP;
        }
        return tankAbove ? BOTTOM : SOLO;
    }

    public boolean isController() {
        return this == SOLO || this == BOTTOM;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
