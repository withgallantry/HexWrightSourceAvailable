package com.bluup.hexwright.server.fluid;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.Nullable;

public enum PipeRun implements StringRepresentable {
    FREE("free", null),
    EAST_WEST("x", Direction.Axis.X),
    UP_DOWN("y", Direction.Axis.Y),
    NORTH_SOUTH("z", Direction.Axis.Z);

    private final String name;
    private final Direction.Axis axis;

    PipeRun(String name, @Nullable Direction.Axis axis) {
        this.name = name;
        this.axis = axis;
    }

    public static PipeRun along(Direction.Axis axis) {
        return switch (axis) {
            case X -> EAST_WEST;
            case Y -> UP_DOWN;
            case Z -> NORTH_SOUTH;
        };
    }

    public boolean isStraight() {
        return axis != null;
    }

    public boolean allows(Direction side) {
        return axis == null || side.getAxis() == axis;
    }

    public PipeRun rotate(Rotation rotation) {
        if (axis == null || axis == Direction.Axis.Y) {
            return this;
        }
        boolean quarter = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
        if (!quarter) {
            return this;
        }
        return this == EAST_WEST ? NORTH_SOUTH : EAST_WEST;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
