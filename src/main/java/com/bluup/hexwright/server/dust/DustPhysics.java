package com.bluup.hexwright.server.dust;

import at.petrak.hexcasting.api.mod.HexTags;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.region.RegionDistance;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DustPhysics {

    public static final ResourceKey<DamageType> DUST_DAMAGE =
        ResourceKey.create(Registries.DAMAGE_TYPE, Hexwright.id("controlled_dust"));

    private static final int MAX_SWEEP_SAMPLES = 8;

    private DustPhysics() {
    }

    public interface Shape {
        double distance(double x, double y, double z);
    }

    public static double contact(DustBody body, ServerLevel level, AABB area, Shape shape, boolean hollow,
                                 boolean sparesCaster, @Nullable Shape oneWay, double strength,
                                 double ux, double uy, double uz, double[] reaction) {
        if (strength < 1.0e-3) {
            return 0.0;
        }
        LivingEntity caster = body.caster;
        Entity ignored = sparesCaster ? caster : null;
        List<Entity> found = level.getEntities(ignored, area.inflate(3.0),
            entity -> affectable(body, entity, sparesCaster));
        int limit = Math.min(found.size(), DustTuning.maxContacts);
        double spent = 0.0;

        for (int i = 0; i < limit; i++) {
            Entity entity = found.get(i);
            if (hollow && body.isCaptured(entity)) {
                continue;
            }
            AABB box = entity.getBoundingBox();
            if (oneWay != null && !crossingOutward(oneWay, box, entity.getDeltaMovement(), ux, uy, uz)) {
                continue;
            }
            double radius = Math.min((box.getXsize() + box.getYsize()) * 0.25, 1.5);
            Vec3 motion = entity.getDeltaMovement();
            double d = sweptDistance(shape, box, motion);

            double immersion = (radius - d) / (2.0 * radius + 1.0e-3);
            if (immersion <= 0.0) {
                continue;
            }
            immersion = Math.min(immersion, 1.0);

            double inertia = inertia(entity);
            double k = 1.0 - Math.exp(-DustTuning.dragRate * strength * immersion / inertia);
            double rx = motion.x - ux;
            double ry = motion.y - uy;
            double rz = motion.z - uz;
            double dvx = -rx * k;
            double dvy = -ry * k;
            double dvz = -rz * k;
            if (entity.onGround() && ry < 0.0) {
                dvy = 0.0;
            }
            double impulse = Math.sqrt(dvx * dvx + dvy * dvy + dvz * dvz);
            if (impulse < DustTuning.freeImpulse) {
                continue;
            }

            push(entity, motion.x + dvx, motion.y + dvy, motion.z + dvz);
            if (entity instanceof AbstractHurtingProjectile fireball) {
                double keep = 1.0 - k;
                fireball.xPower *= keep;
                fireball.yPower *= keep;
                fireball.zPower *= keep;
            }
            double weight = Math.max(inertia, 1.0);
            spent += impulse * weight * DustTuning.massPerImpulse;
            if (reaction != null) {
                reaction[0] -= dvx * inertia;
                reaction[1] -= dvy * inertia;
                reaction[2] -= dvz * inertia;
            }

            double relativeSpeed = Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (entity instanceof LivingEntity living && relativeSpeed > DustTuning.damageSpeed) {
                float damage = (float) (DustTuning.damagePerSpeed * strength * immersion
                    * (relativeSpeed - DustTuning.damageSpeed));
                if (damage >= 0.5f && living.hurt(damageSource(level, caster), damage)) {
                    spent += damage * DustTuning.massPerDamage;
                }
            }
        }
        return spent;
    }

    public static double constrain(Entity entity, Shape shape, DustFormation formation,
                                   double ox, double oy, double oz, double strength,
                                   double ux, double uy, double uz, double[] normal) {
        AABB box = entity.getBoundingBox();
        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double d = shape.distance(cx, cy, cz);
        if (d > DustTuning.constrainEscape * (0.5 + strength)) {
            return -1.0;
        }
        if (strength < 1.0e-3) {
            return 0.0;
        }

        Vec3 motion = entity.getDeltaMovement();
        double dvx = 0.0;
        double dvy = 0.0;
        double dvz = 0.0;

        double penetration = d + DustTuning.constrainSoftZone;
        if (penetration > 0.0) {
            RegionDistance.gradient(formation.region, cx - ox, cy - oy, cz - oz, normal, 0);
            double nx = normal[0];
            double ny = normal[1];
            double nz = normal[2];
            double vn = (motion.x - ux) * nx + (motion.y - uy) * ny + (motion.z - uz) * nz;
            double push = Math.min(penetration * DustTuning.constrainStiffness, DustTuning.constrainMaxPush);
            if (vn > -push) {
                double correction = (-push - vn) * strength;
                dvx += nx * correction;
                dvy += ny * correction;
                dvz += nz * correction;
            }
        }

        double carrySq = ux * ux + uy * uy + uz * uz;
        if (carrySq > 1.0e-4) {
            double carry = DustTuning.constrainCarry * strength;
            dvx += (ux - motion.x) * carry;
            dvy += (uy - motion.y) * carry;
            dvz += (uz - motion.z) * carry;
        }

        entity.resetFallDistance();

        double impulse = Math.sqrt(dvx * dvx + dvy * dvy + dvz * dvz);
        if (impulse < DustTuning.freeImpulse * 0.5) {
            return 0.0;
        }
        push(entity, motion.x + dvx, motion.y + dvy, motion.z + dvz);
        return impulse * Math.max(inertia(entity), 1.0) * DustTuning.massPerImpulse;
    }

    public static boolean physical(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator()) {
            return false;
        }
        return !(entity instanceof Display) && !(entity instanceof Marker)
            && !(entity instanceof Interaction) && !(entity instanceof HangingEntity)
            && !(entity instanceof ExperienceOrb);
    }

    static boolean affectable(DustBody body, Entity entity, boolean sparesCaster) {
        if (!physical(entity) || entity.isPassenger()) {
            return false;
        }
        if (!sparesCaster && DustTuning.formationSparesCaster == 0) {
            return true;
        }
        LivingEntity caster = body.caster;
        if (entity.getRootVehicle() == caster.getRootVehicle()) {
            return false;
        }
        return !(entity instanceof Projectile projectile) || projectile.getOwner() != caster;
    }

    private static boolean crossingOutward(Shape region, AABB box, Vec3 motion,
                                           double ux, double uy, double uz) {
        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double h = 0.1;
        double nx = region.distance(cx + h, cy, cz) - region.distance(cx - h, cy, cz);
        double ny = region.distance(cx, cy + h, cz) - region.distance(cx, cy - h, cz);
        double nz = region.distance(cx, cy, cz + h) - region.distance(cx, cy, cz - h);
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0e-6) {
            return true;
        }
        double vx = motion.x - ux;
        double vy = motion.y - uy;
        double vz = motion.z - uz;
        return (vx * nx + vy * ny + vz * nz) / length > 0.0;
    }

    static boolean capturable(DustBody body, Entity entity) {
        return affectable(body, entity, false)
            && !(entity instanceof Projectile)
            && !entity.getType().is(HexTags.Entities.CANNOT_TELEPORT);
    }

    static double inertia(Entity entity) {
        if (entity instanceof Projectile) {
            return DustTuning.projectileInertia;
        }
        AABB box = entity.getBoundingBox();
        double volume = box.getXsize() * box.getYsize() * box.getZsize();
        return Math.max(Math.sqrt(Math.max(volume, 0.05)), 0.3);
    }

    private static double sweptDistance(Shape shape, AABB box, Vec3 motion) {
        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double length = motion.length();
        int samples = Math.min(MAX_SWEEP_SAMPLES, Math.max(1, (int) Math.ceil(length / 0.5)));
        double best = shape.distance(cx, cy, cz);
        for (int s = 1; s <= samples; s++) {
            double t = s / (double) samples;
            double d = shape.distance(cx - motion.x * t, cy - motion.y * t, cz - motion.z * t);
            if (d < best) {
                best = d;
            }
        }
        return best;
    }

    private static void push(Entity entity, double vx, double vy, double vz) {
        entity.setDeltaMovement(vx, vy, vz);
        entity.hurtMarked = true;
        entity.hasImpulse = true;
    }

    private static DamageSource damageSource(ServerLevel level, LivingEntity caster) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(DUST_DAMAGE), caster);
    }
}
