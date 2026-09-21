package com.bluup.hexwright.server.dust;

import com.bluup.hexwright.server.region.Region;
import com.bluup.hexwright.server.region.RegionDistance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class DustSupport {

    public interface Source {
        void collect(List<Surface> into);
    }

    private static final int MAX_SAMPLES = 3;
    private static final double GROUND_TOLERANCE = 0.35;
    private static final double FIRM = 0.5;
    private static final double CATCHES = 0.25;

    private static final Side SERVER = new Side();
    private static final Side CLIENT = new Side();

    private DustSupport() {
    }

    public static final class Surface {

        public final Level level;
        private final Region region;
        private final double ox;
        private final double oy;
        private final double oz;
        private final boolean shell;
        private final double hold;
        private final double vx;
        private final double vy;
        private final double vz;
        private final AABB reach;
        private final int casterId;
        private final double band;
        private final double speed;

        public Surface(Level level, Region region, double ox, double oy, double oz, boolean shell,
                       double hold, double vx, double vy, double vz, int casterId) {
            this.level = level;
            this.region = region;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.shell = shell;
            this.hold = hold;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.casterId = casterId;
            this.band = DustTuning.surfaceProud - DustTuning.shellThickness * 0.5;
            this.speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            this.reach = region.bounds().move(ox, oy, oz)
                .inflate(DustFormation.surfaceReach() + GROUND_TOLERANCE + speed);
        }

        public double distance(double x, double y, double z) {
            return DustFormation.surfaceDistance(
                RegionDistance.distance(region, x - ox, y - oy, z - oz), shell);
        }

        public void normal(double x, double y, double z, double[] out) {
            RegionDistance.gradient(region, x - ox, y - oy, z - oz, out, 0);
            if (shell && RegionDistance.distance(region, x - ox, y - oy, z - oz) < band) {
                out[0] = -out[0];
                out[1] = -out[1];
                out[2] = -out[2];
            }
        }

        public double hold() {
            return hold;
        }

        public double bearing(Entity entity, double[] normal) {
            double tolerance = GROUND_TOLERANCE + speed;
            AABB box = entity.getBoundingBox();
            if (!reach.intersects(box.inflate(tolerance))) {
                return 0.0;
            }
            double x = (box.minX + box.maxX) * 0.5;
            double z = (box.minZ + box.maxZ) * 0.5;
            double y = box.minY - GROUND_TOLERANCE * 0.5;
            if (distance(x, y, z) > tolerance) {
                return 0.0;
            }
            normal(x, y, z, normal);
            return normal[1] >= DustTuning.supportNormalY ? hold : 0.0;
        }
    }


    public static void source(boolean client, Source source) {
        (client ? CLIENT : SERVER).source = source;
    }

    public static void invalidate(boolean client) {
        (client ? CLIENT : SERVER).stale = true;
    }

    public static void clear(boolean client) {
        Side side = client ? CLIENT : SERVER;
        side.surfaces.clear();
        side.stale = true;
    }

    private static List<Surface> surfaces(Level level) {
        Side side = level.isClientSide ? CLIENT : SERVER;
        if (side.stale) {
            side.stale = false;
            side.surfaces.clear();
            if (side.source != null) {
                side.source.collect(side.surfaces);
            }
        }
        return side.surfaces;
    }


    public static void apply(Entity entity) {
        Level level = entity.level();
        List<Surface> found = surfaces(level);
        if (found.isEmpty()) {
            return;
        }
        if (!level.isClientSide && entity instanceof Player) {
            return;
        }
        if (!bearable(entity)) {
            return;
        }
        AABB box = entity.getBoundingBox();
        double[] normal = new double[3];
        for (int i = 0; i < found.size(); i++) {
            Surface surface = found.get(i);
            if (surface.level == level && surface.hold > 0.0 && surface.reach.intersects(box)) {
                resolve(entity, surface, normal);
                box = entity.getBoundingBox();
            }
        }
    }

    public static double groundHold(Entity entity) {
        Level level = entity.level();
        if (!bearable(entity)) {
            return 0.0;
        }
        List<Surface> found = surfaces(level);
        if (found.isEmpty()) {
            return 0.0;
        }
        double[] normal = new double[3];
        double best = 0.0;
        for (int i = 0; i < found.size(); i++) {
            Surface surface = found.get(i);
            if (surface.level == level && surface.hold > best) {
                best = Math.max(best, surface.bearing(entity, normal));
            }
        }
        return best;
    }

    public static boolean holdsUp(Entity entity) {
        return groundHold(entity) >= FIRM;
    }

    public static boolean catches(Entity entity) {
        return groundHold(entity) > CATCHES;
    }

    public static double hold(double strength) {
        double low = DustTuning.supportLow;
        double high = Math.max(DustTuning.supportFirm, low + 1.0e-3);
        if (strength <= low) {
            return 0.0;
        }
        double t = Math.min(1.0, (strength - low) / (high - low));
        return t * t * (3.0 - 2.0 * t);
    }

    public static boolean bearable(Entity entity) {
        return DustPhysics.physical(entity) && !entity.noPhysics && !entity.isPassenger();
    }

    private static void resolve(Entity entity, Surface surface, double[] normal) {
        AABB box = entity.getBoundingBox();
        double radius = Math.max(Math.min(box.getXsize(), box.getZsize()) * 0.5, 0.05);
        double x = (box.minX + box.maxX) * 0.5;
        double z = (box.minZ + box.maxZ) * 0.5;
        double low = box.minY + radius;
        double high = Math.max(box.maxY - radius, low);
        int samples = high - low < 1.0e-4 ? 1 : MAX_SAMPLES;

        double depth = -Double.MAX_VALUE;
        double at = low;
        for (int s = 0; s < samples; s++) {
            double y = samples == 1 ? low : low + (high - low) * s / (samples - 1.0);
            double penetration = radius - surface.distance(x, y, z);
            if (penetration > depth) {
                depth = penetration;
                at = y;
            }
        }
        if (depth <= -(GROUND_TOLERANCE + surface.speed)) {
            return;
        }

        surface.normal(x, at, z, normal);
        double nx = normal[0];
        double ny = normal[1];
        double nz = normal[2];
        if (nx * nx + ny * ny + nz * nz < 0.5) {
            nx = 0.0;
            ny = 1.0;
            nz = 0.0;
        }
        boolean ground = ny >= DustTuning.supportNormalY;
        if (!ground && entity.getId() == surface.casterId) {
            return;
        }

        double receding = -(surface.vx * nx + surface.vy * ny + surface.vz * nz);
        if (depth <= -(GROUND_TOLERANCE + Math.max(receding, 0.0))) {
            return;
        }

        double hold = surface.hold;
        Vec3 motion = entity.getDeltaMovement();
        if (ground) {
            double carry = DustTuning.supportCarry * hold;
            if (carry > 0.0 && surface.speed > 0.0) {
                entity.setPos(entity.getX() + surface.vx * carry, entity.getY() + surface.vy * carry,
                    entity.getZ() + surface.vz * carry);
            }
            if (motion.y < 0.0) {
                entity.setDeltaMovement(motion.x, motion.y * (1.0 - hold), motion.z);
            }
            if (hold >= FIRM) {
                entity.setOnGround(true);
                entity.verticalCollisionBelow = true;
            }
            if (hold > CATCHES) {
                entity.resetFallDistance();
            }
        } else if (depth > 0.0) {
            double vn = (motion.x - surface.vx) * nx + (motion.y - surface.vy) * ny
                + (motion.z - surface.vz) * nz;
            if (vn < 0.0) {
                entity.setDeltaMovement(motion.x - nx * vn * hold, motion.y - ny * vn * hold,
                    motion.z - nz * vn * hold);
            }
        }

        double push = Math.min(Math.max(depth, 0.0), DustTuning.supportMaxPush) * hold;
        if (push > 1.0e-4) {
            entity.setPos(entity.getX() + nx * push, entity.getY() + ny * push, entity.getZ() + nz * push);
        }
        entity.hasImpulse = true;
    }

    private static final class Side {
        Source source;
        final List<Surface> surfaces = new ArrayList<>();
        boolean stale = true;
    }
}
