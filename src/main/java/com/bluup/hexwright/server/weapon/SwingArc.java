package com.bluup.hexwright.server.weapon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SwingArc {

    public static final float DEFAULT_REACH = 3.5F;

    public static final float DEFAULT_ARC_DEGREES = 90.0F;

    private static final double SWING_HEIGHT = 1.0D;

    private SwingArc() {
    }

    public static void strike(ServerPlayer player, AnimatedWeapon weapon) {
        float reach = weapon.swingArcReach();
        if (reach <= 0.0F) {
            return;
        }
        LivingEntity victim = nearestInArc(player, reach, weapon.swingArcDegrees());
        if (victim != null) {
            player.attack(victim);
        }
    }

    @Nullable
    private static LivingEntity nearestInArc(ServerPlayer player, float reach, float arcDegrees) {
        ServerLevel level = player.serverLevel();
        Vec3 origin = player.position();
        Vec3 facing = horizontalFacing(player);
        double halfArcCos = Math.cos(Math.toRadians(arcDegrees / 2.0F));

        AABB caught = player.getBoundingBox().inflate(reach, SWING_HEIGHT, reach);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, caught,
            candidate -> isSwingable(player, candidate));

        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double dx = candidate.getX() - origin.x;
            double dz = candidate.getZ() - origin.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance - candidate.getBbWidth() / 2.0D > reach) {
                continue;
            }
            if (distance > 1.0E-4D) {
                double towards = (dx * facing.x + dz * facing.z) / distance;
                if (towards < halfArcCos) {
                    continue;
                }
            }
            if (distance >= nearestDistance) {
                continue;
            }
            if (!player.hasLineOfSight(candidate)) {
                continue;
            }
            nearest = candidate;
            nearestDistance = distance;
        }
        return nearest;
    }

    private static boolean isSwingable(ServerPlayer player, LivingEntity candidate) {
        if (candidate == player || !candidate.isAlive() || candidate.isSpectator()) {
            return false;
        }
        if (!candidate.isAttackable() || !candidate.isPickable()) {
            return false;
        }
        for (Entity vehicle = player.getVehicle(); vehicle != null; vehicle = vehicle.getVehicle()) {
            if (vehicle == candidate) {
                return false;
            }
        }
        return true;
    }

    private static Vec3 horizontalFacing(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-6D) {
            float yaw = player.getYRot() * ((float) Math.PI / 180F);
            return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        }
        return flat.normalize();
    }
}
