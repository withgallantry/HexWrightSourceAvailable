package com.bluup.hexwright.server.weapon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class Cleave {

    public static final float NETHERITE_REACH = 3.5F;

    public static final int NETHERITE_COOLDOWN_TICKS = 50;

    public static final float ARC_DEGREES = 120.0F;

    private static final float DAMAGE = 9.0F;

    private static final double KNOCKBACK_HORIZONTAL = 0.45D;
    private static final double KNOCKBACK_VERTICAL = 0.15D;

    private static final double CUT_HEIGHT = 1.2D;

    private Cleave() {
    }

    public static boolean cleave(ServerLevel level, ServerPlayer player, float reach) {
        Vec3 origin = player.position();
        Vec3 facing = horizontalFacing(player);
        double halfArcCos = Math.cos(Math.toRadians(ARC_DEGREES / 2.0F));

        DamageSource damage = level.damageSources().playerAttack(player);
        AABB caught = player.getBoundingBox().inflate(reach, CUT_HEIGHT, reach);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, caught,
            victim -> victim.isAlive() && victim != player && !victim.isSpectator());

        for (LivingEntity victim : victims) {
            double dx = victim.getX() - origin.x;
            double dz = victim.getZ() - origin.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > reach) {
                continue;
            }
            if (distance > 1.0E-4D) {
                double towards = (dx * facing.x + dz * facing.z) / distance;
                if (towards < halfArcCos) {
                    continue;
                }
            }

            victim.hurt(damage, DAMAGE);
            double outX = distance < 1.0E-4D ? facing.x : dx / distance;
            double outZ = distance < 1.0E-4D ? facing.z : dz / distance;
            victim.push(outX * KNOCKBACK_HORIZONTAL, KNOCKBACK_VERTICAL, outZ * KNOCKBACK_HORIZONTAL);
            syncVelocity(victim);
        }

        level.playSound(null, origin.x, origin.y, origin.z,
            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
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

    private static void syncVelocity(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
        } else {
            entity.hurtMarked = true;
        }
    }
}
