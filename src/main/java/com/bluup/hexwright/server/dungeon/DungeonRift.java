package com.bluup.hexwright.server.dungeon;

import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record DungeonRift(int instance, Kind kind, Vec3 center, Direction facing, long seed,
                          long opensAt, long closesAt) {

    public enum Kind {
        FORMING,
        ENTRANCE,
        EXIT
    }

    public static final double HALF_LENGTH = 1.35;

    public static final double HALF_APERTURE = 0.55;

    private static final double REACH_ACROSS = 0.15;
    private static final double REACH_DEPTH = 0.45;

    public Vec3 u() {
        return new Vec3(0.0, HALF_LENGTH, 0.0);
    }

    public Vec3 v() {
        Direction across = this.facing.getClockWise();
        return new Vec3(across.getStepX() * HALF_APERTURE, 0.0, across.getStepZ() * HALF_APERTURE);
    }

    public AABB triggerBox() {
        double across = HALF_APERTURE + REACH_ACROSS;
        boolean alongX = this.facing.getAxis() == Direction.Axis.X;
        double halfX = alongX ? REACH_DEPTH : across;
        double halfZ = alongX ? across : REACH_DEPTH;
        return new AABB(this.center.x - halfX, this.center.y - HALF_LENGTH, this.center.z - halfZ,
            this.center.x + halfX, this.center.y + HALF_LENGTH, this.center.z + halfZ);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.instance);
        buf.writeEnum(this.kind);
        buf.writeDouble(this.center.x);
        buf.writeDouble(this.center.y);
        buf.writeDouble(this.center.z);
        buf.writeEnum(this.facing);
        buf.writeLong(this.seed);
        buf.writeLong(this.opensAt);
        buf.writeLong(this.closesAt);
    }

    public static DungeonRift read(FriendlyByteBuf buf) {
        return new DungeonRift(buf.readVarInt(), buf.readEnum(Kind.class),
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readEnum(Direction.class),
            buf.readLong(), buf.readLong(), buf.readLong());
    }
}
