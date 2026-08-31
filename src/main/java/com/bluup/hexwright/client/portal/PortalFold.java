package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.server.portal.PortalTransform;
import com.bluup.hexwright.server.portal.PortalWindow;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class PortalFold {

    private final @Nullable PortalFold parent;
    private final PortalTransform hop;
    private final float yawDeltaDegrees;
    private final int depth;

    private PortalFold(@Nullable PortalFold parent, PortalTransform hop) {
        this.parent = parent;
        this.hop = hop;
        this.yawDeltaDegrees = (float) Mth.wrapDegrees(
            (parent == null ? 0.0f : parent.yawDeltaDegrees) + hop.yawDeltaDegrees());
        this.depth = parent == null ? 1 : parent.depth + 1;
    }

    public static PortalFold of(PortalTransform hop) {
        return new PortalFold(null, hop);
    }

    public PortalFold then(PortalTransform next) {
        return new PortalFold(this, next);
    }

    public Vec3 apply(Vec3 point) {
        return hop.apply(parent == null ? point : parent.apply(point));
    }

    public Vec3 applyDirection(Vec3 direction) {
        return hop.applyDirection(parent == null ? direction : parent.applyDirection(direction));
    }

    public float yawDeltaDegrees() {
        return yawDeltaDegrees;
    }

    public PortalWindow toWindow() {
        return hop.toWindow();
    }

    public int depth() {
        return depth;
    }
}
