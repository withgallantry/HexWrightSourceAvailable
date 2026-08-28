package com.bluup.hexwright.server.portal;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class PortalTransform {

    private final PortalWindow from;
    private final PortalWindow to;
    private final float yawDeltaDegrees;

    public PortalTransform(PortalWindow from, PortalWindow to) {
        this.from = from;
        this.to = to;
        this.yawDeltaDegrees = (float) Mth.wrapDegrees(
            yawOfDegrees(to.normal().scale(-1.0)) - yawOfDegrees(from.normal()));
    }

    private static double yawOfDegrees(Vec3 dir) {
        return Math.toDegrees(Math.atan2(-dir.x, dir.z));
    }

    public Vec3 apply(Vec3 point) {
        Vec3 rel = point.subtract(from.center());
        double a = rel.dot(from.uHat());
        double b = rel.dot(from.vHat());
        double c = rel.dot(from.normal());
        return to.center()
            .add(to.uHat().scale(-a))
            .add(to.vHat().scale(b))
            .add(to.normal().scale(-c));
    }

    public Vec3 applyDirection(Vec3 dir) {
        double a = dir.dot(from.uHat());
        double b = dir.dot(from.vHat());
        double c = dir.dot(from.normal());
        return to.uHat().scale(-a)
            .add(to.vHat().scale(b))
            .add(to.normal().scale(-c));
    }

    public float yawDeltaDegrees() {
        return yawDeltaDegrees;
    }

    public PortalWindow toWindow() {
        return to;
    }
}
