package com.bluup.hexwright.server.portal;

import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public record VoidTear(UUID id, UUID caster, Vec3 center, Vec3 u, Vec3 v, long seed, long openedAt) {

    public static final int LIFETIME_TICKS = 40;

    public static final int OPEN_TICKS = 3;

    public static final int SEAL_TICKS = 3;

    public double length() {
        return u.length() * 2.0;
    }

    public double aperture() {
        return v.length() * 2.0;
    }

    public Vec3 normal() {
        return u.normalize().cross(v.normalize()).normalize();
    }

    public double signedDistance(Vec3 point) {
        return point.subtract(center).dot(normal());
    }

    public boolean containsProjected(Vec3 point, double margin) {
        Vec3 local = point.subtract(center);
        double halfLength = u.length();
        double halfAperture = v.length();
        double along = Math.abs(local.dot(u.normalize()));
        double across = Math.abs(local.dot(v.normalize()));
        double a = Math.max(0.0, along - margin) / halfLength;
        double b = Math.max(0.0, across - margin) / halfAperture;
        return a * a + b * b <= 1.0;
    }

    public Vec3 pointAt(double a, double b) {
        return center.add(u.scale(a)).add(v.scale(b));
    }

    public AABB gatherBox(double margin) {
        double reach = u.length() + v.length() + margin;
        return new AABB(center.subtract(reach, reach, reach), center.add(reach, reach, reach));
    }

    public boolean isValid() {
        return u.length() > 1.0e-4 && v.length() > 1.0e-4
            && isFinite(center) && isFinite(u) && isFinite(v)
            && Math.abs(u.normalize().dot(v.normalize())) < 1.0e-3;
    }

    private static boolean isFinite(Vec3 vec) {
        return Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        writeVec(buf, center);
        writeVec(buf, u);
        writeVec(buf, v);
        buf.writeLong(seed);
    }

    public static VoidTear read(FriendlyByteBuf buf, long openedAt) {
        UUID id = buf.readUUID();
        Vec3 center = readVec(buf);
        Vec3 u = readVec(buf);
        Vec3 v = readVec(buf);
        long seed = buf.readLong();
        return new VoidTear(id, Util.NIL_UUID, center, u, v, seed, openedAt);
    }

    private static void writeVec(FriendlyByteBuf buf, Vec3 vec) {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}
