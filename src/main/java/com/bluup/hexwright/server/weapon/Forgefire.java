package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.effect.HexwrightEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class Forgefire {

    public static final float SCORCH_DAMAGE = 5.0F;
    public static final int SCORCH_SECONDS = 4;

    public static final int TEMPER_TICKS = 200;
    public static final double TEMPER_ATTACK_DAMAGE = 2.0D;
    public static final double TEMPER_ARMOUR = 4.0D;
    public static final double TEMPER_MEDIA_DISCOUNT = 0.15D;

    private static final double CATCH_BELOW = 1.5D;
    private static final double CATCH_ABOVE = 3.0D;

    private static final int RING_FLAMES = 36;

    private Forgefire() {
    }

    public static List<LivingEntity> strike(ServerLevel level, ServerPlayer wielder, BlockPos impact, float radius) {
        Vec3 centre = GroundSlam.centreOf(impact);
        AABB reach = new AABB(
            centre.x - radius, centre.y - CATCH_BELOW, centre.z - radius,
            centre.x + radius, centre.y + CATCH_ABOVE, centre.z + radius);

        temper(level, wielder);
        List<LivingEntity> scorched = new ArrayList<>();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, reach,
            entity -> entity.isAlive() && !entity.isSpectator() && entity != wielder)) {
            double dx = entity.getX() - centre.x;
            double dz = entity.getZ() - centre.z;
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            if (entity instanceof Player ally) {
                temper(level, ally);
            } else if (scorches(entity, wielder)) {
                entity.hurt(level.damageSources().playerAttack(wielder), SCORCH_DAMAGE);
                entity.setSecondsOnFire(SCORCH_SECONDS);
                scorched.add(entity);
            }
        }

        ring(level, centre, radius);
        HexwrightNetworking.sendGroundSlam(level, impact, radius);
        return scorched;
    }

    private static boolean scorches(LivingEntity entity, ServerPlayer wielder) {
        if (entity instanceof ArmorStand || entity.isAlliedTo(wielder)
            || entity instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
            return false;
        }
        boolean afterAPlayer = entity instanceof Mob mob && mob.getTarget() instanceof Player;
        if (entity instanceof NeutralMob neutral) {
            return neutral.isAngryAt(wielder) || afterAPlayer;
        }
        return entity instanceof Enemy || afterAPlayer;
    }

    private static void temper(ServerLevel level, Player player) {
        player.addEffect(new MobEffectInstance(HexwrightEffects.TEMPERED, TEMPER_TICKS, 0, false, true, true));
        level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY(0.5D), player.getZ(),
            12, 0.4D, 0.6D, 0.4D, 0.0D);
    }

    private static void ring(ServerLevel level, Vec3 centre, float radius) {
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 0.7F);
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS,
            1.0F, 0.8F);
        for (int flame = 0; flame < RING_FLAMES; flame++) {
            float angle = flame * Mth.TWO_PI / RING_FLAMES;
            double out = flame % 2 == 0 ? radius : radius * 0.55D;
            level.sendParticles(ParticleTypes.FLAME,
                centre.x + Mth.cos(angle) * out, centre.y + 0.1D, centre.z + Mth.sin(angle) * out,
                2, 0.1D, 0.05D, 0.1D, 0.01D);
        }
        level.sendParticles(ParticleTypes.LAVA, centre.x, centre.y + 0.1D, centre.z, 14, radius * 0.3D, 0.1D,
            radius * 0.3D, 0.0D);
    }
}
