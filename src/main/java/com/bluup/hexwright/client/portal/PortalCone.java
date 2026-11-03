package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.server.portal.PortalWindow;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class PortalCone {

    private static final double SIDE_SLACK = 0.5;

    private static final double NEAR_SLACK = 1.0;

    private static final double MIN_EYE_DEPTH = 0.25;

    private static final int PLANES = 5;

    private final double eyeX;
    private final double eyeY;
    private final double eyeZ;

    private final double[] planes = new double[PLANES * 4];

    private final double boundMinX;
    private final double boundMinY;
    private final double boundMinZ;
    private final double boundMaxX;
    private final double boundMaxY;
    private final double boundMaxZ;

    public static @Nullable PortalCone of(Vec3 eye, PortalWindow window, double farDistance) {
        double depth = window.signedDistance(eye);
        if (!(Math.abs(depth) > MIN_EYE_DEPTH)) {
            return null;
        }
        return new PortalCone(eye, window, depth, farDistance);
    }

    private PortalCone(Vec3 eye, PortalWindow window, double depth, double farDistance) {
        this.eyeX = eye.x;
        this.eyeY = eye.y;
        this.eyeZ = eye.z;

        double w = window.width();
        double h = window.height();
        Vec3[] rays = {
            window.pointAt(0.0, 0.0).subtract(eye),
            window.pointAt(w, 0.0).subtract(eye),
            window.pointAt(w, h).subtract(eye),
            window.pointAt(0.0, h).subtract(eye),
        };
        Vec3 toCenter = window.center().subtract(eye);

        Vec3 near = window.normal().scale(depth > 0.0 ? -1.0 : 1.0);
        double nearOffset = Math.abs(depth) - NEAR_SLACK;
        planes[0] = near.x;
        planes[1] = near.y;
        planes[2] = near.z;
        planes[3] = nearOffset;

        for (int i = 0; i < 4; i++) {
            Vec3 normal = rays[i].cross(rays[(i + 1) & 3]);
            if (normal.dot(toCenter) < 0.0) {
                normal = normal.scale(-1.0);
            }
            normal = normal.normalize();
            planes[(i + 1) * 4] = normal.x;
            planes[(i + 1) * 4 + 1] = normal.y;
            planes[(i + 1) * 4 + 2] = normal.z;
            planes[(i + 1) * 4 + 3] = -SIDE_SLACK;
        }

        double cos = Math.max(-1.0, Math.min(1.0, nearOffset / farDistance));
        double sin = Math.sqrt(Math.max(0.0, 1.0 - cos * cos));
        this.boundMinX = eye.x + farDistance * axisMin(near.x, cos, sin) - SIDE_SLACK;
        this.boundMinY = eye.y + farDistance * axisMin(near.y, cos, sin) - SIDE_SLACK;
        this.boundMinZ = eye.z + farDistance * axisMin(near.z, cos, sin) - SIDE_SLACK;
        this.boundMaxX = eye.x + farDistance * axisMax(near.x, cos, sin) + SIDE_SLACK;
        this.boundMaxY = eye.y + farDistance * axisMax(near.y, cos, sin) + SIDE_SLACK;
        this.boundMaxZ = eye.z + farDistance * axisMax(near.z, cos, sin) + SIDE_SLACK;
    }

    private static double axisMax(double component, double cos, double sin) {
        if (component >= cos) {
            return 1.0;
        }
        double perpendicular = Math.sqrt(Math.max(0.0, 1.0 - component * component));
        return Math.min(1.0, component * cos + perpendicular * sin);
    }

    private static double axisMin(double component, double cos, double sin) {
        if (-component >= cos) {
            return -1.0;
        }
        double perpendicular = Math.sqrt(Math.max(0.0, 1.0 - component * component));
        return Math.max(-1.0, component * cos - perpendicular * sin);
    }

    public boolean isOutside(double minX, double minY, double minZ,
                             double maxX, double maxY, double maxZ) {
        if (maxX < boundMinX || minX > boundMaxX
            || maxY < boundMinY || minY > boundMaxY
            || maxZ < boundMinZ || minZ > boundMaxZ) {
            return true;
        }
        for (int i = 0; i < PLANES; i++) {
            double nx = planes[i * 4];
            double ny = planes[i * 4 + 1];
            double nz = planes[i * 4 + 2];
            double px = (nx > 0.0 ? maxX : minX) - eyeX;
            double py = (ny > 0.0 ? maxY : minY) - eyeY;
            double pz = (nz > 0.0 ? maxZ : minZ) - eyeZ;
            if (nx * px + ny * py + nz * pz < planes[i * 4 + 3]) {
                return true;
            }
        }
        return false;
    }
}
