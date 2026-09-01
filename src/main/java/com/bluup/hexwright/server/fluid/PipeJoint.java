package com.bluup.hexwright.server.fluid;

import net.minecraft.util.StringRepresentable;

public enum PipeJoint implements StringRepresentable {
    NONE("none"),
    PIPE("pipe"),
    TANK("tank");

    private final String name;

    PipeJoint(String name) {
        this.name = name;
    }

    public boolean joined() {
        return this != NONE;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
