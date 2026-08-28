package com.bluup.hexwright.server.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class PortalWindow {

    public static final double MIN_SPAN = 0.5;

    public static final double MAX_SPAN = 8.0;

    private final Vec3 origin;
    private final Vec3 u;
    private final Vec3 v;

    private final Vec3 uHat;
    private final Vec3 vHat;
    private final Vec3 normal;
    private final double width;
    private final double height;

    public PortalWindow(Vec3 origin, Vec3 u, Vec3 v) {
        this.origin = origin;
        this.u = u;
        this.v = v;
        this.width = u.length();
        this.height = v.length();
        this.uHat = u.scale(1.0 / this.width);
        this.vHat = v.scale(1.0 / this.height);
        this.normal = this.uHat.cross(this.vHat);
    }

    public static @Nullable PortalWindow fromCorners(Vec3 first, Vec3 second) {
        var firstBlock = BlockPos.containing(first);
        var secondBlock = BlockPos.containing(second);
        Vec3 firstCenter = new Vec3(firstBlock.getX() + 0.5, 0.0, firstBlock.getZ() + 0.5);
        Vec3 secondCenter = new Vec3(secondBlock.getX() + 0.5, 0.0, secondBlock.getZ() + 0.5);
        Vec3 span = secondCenter.subtract(firstCenter);
        double centerDistance = span.length();
        if (centerDistance < 1.0e-6) {
            return null;
        }
        Vec3 uHat = span.scale(1.0 / centerDistance);
        double width = centerDistance + 1.0;
        double height = Math.abs(firstBlock.getY() - secondBlock.getY()) + 1.0;
        Vec3 bottom = firstCenter.subtract(uHat.scale(0.5));
        Vec3 origin = new Vec3(bottom.x, Math.min(firstBlock.getY(), secondBlock.getY()), bottom.z);
        return new PortalWindow(origin, uHat.scale(width), new Vec3(0.0, height, 0.0));
    }

    public boolean isValid() {
        return width >= MIN_SPAN && height >= MIN_SPAN
            && isFinite(origin) && isFinite(u) && isFinite(v) && isFinite(normal);
    }

    private static boolean isFinite(Vec3 vec) {
        return Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
    }

    public Vec3 origin() {
        return origin;
    }

    public Vec3 u() {
        return u;
    }

    public Vec3 v() {
        return v;
    }

    public Vec3 uHat() {
        return uHat;
    }

    public Vec3 vHat() {
        return vHat;
    }

    public Vec3 normal() {
        return normal;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public Vec3 center() {
        return origin.add(u.scale(0.5)).add(v.scale(0.5));
    }

    public double signedDistance(Vec3 point) {
        return point.subtract(origin).dot(normal);
    }

    public double localU(Vec3 point) {
        return point.subtract(origin).dot(uHat);
    }

    public double localV(Vec3 point) {
        return point.subtract(origin).dot(vHat);
    }

    public boolean containsProjected(Vec3 point, double margin) {
        double a = localU(point);
        double b = localV(point);
        return a >= -margin && a <= width + margin && b >= -margin && b <= height + margin;
    }

    public double rayHit(Vec3 from, Vec3 dir, double maxDist) {
        double denom = dir.dot(normal);
        if (Math.abs(denom) < 1.0e-6) {
            return -1.0;
        }
        double t = -signedDistance(from) / denom;
        if (t < 0.001 || t > maxDist) {
            return -1.0;
        }
        Vec3 hit = from.add(dir.scale(t));
        return containsProjected(hit, 0.0) ? t : -1.0;
    }

    public double distanceSqToRect(Vec3 point) {
        double a = Math.max(0.0, Math.min(width, localU(point)));
        double b = Math.max(0.0, Math.min(height, localV(point)));
        Vec3 closest = origin.add(uHat.scale(a)).add(vHat.scale(b));
        return point.distanceToSqr(closest);
    }

    public Vec3 pointAt(double a, double b) {
        return origin.add(uHat.scale(a)).add(vHat.scale(b));
    }

    public AABB bounds(double normalInflate) {
        Vec3 c0 = origin;
        Vec3 c1 = origin.add(u).add(v);
        return new AABB(c0, c1).inflate(
            Math.abs(normal.x) * normalInflate + 1.0e-3,
            Math.abs(normal.y) * normalInflate + 1.0e-3,
            Math.abs(normal.z) * normalInflate + 1.0e-3);
    }

    public boolean roughlyMatches(PortalWindow other) {
        return center().distanceToSqr(other.center()) < 1.0
            && Math.abs(width - other.width()) < 0.5
            && Math.abs(height - other.height()) < 0.5;
    }


    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        putVec(tag, "Origin", origin);
        putVec(tag, "U", u);
        putVec(tag, "V", v);
        return tag;
    }

    public static PortalWindow load(CompoundTag tag) {
        return new PortalWindow(getVec(tag, "Origin"), getVec(tag, "U"), getVec(tag, "V"));
    }

    public void write(FriendlyByteBuf buf) {
        writeVec(buf, origin);
        writeVec(buf, u);
        writeVec(buf, v);
    }

    public static PortalWindow read(FriendlyByteBuf buf) {
        return new PortalWindow(readVec(buf), readVec(buf), readVec(buf));
    }

    private static void putVec(CompoundTag tag, String key, Vec3 vec) {
        tag.putDouble(key + "X", vec.x);
        tag.putDouble(key + "Y", vec.y);
        tag.putDouble(key + "Z", vec.z);
    }

    private static Vec3 getVec(CompoundTag tag, String key) {
        return new Vec3(tag.getDouble(key + "X"), tag.getDouble(key + "Y"), tag.getDouble(key + "Z"));
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
