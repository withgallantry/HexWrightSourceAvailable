package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.boss.Bosses;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ViciousStrike {

    public static final int VFX_CHARGE = 0;
    public static final int VFX_RUPTURE = 1;
    public static final int VFX_SLASH = 2;

    public static final int CHARGE_TICKS = 25;

    private static final double RUPTURE_FORWARD = 0.7D;

    private static final double SLASH_HEIGHT = 1.4D;

    public static final double REACH = 15.0D;

    private static final double HALF_WIDTH = 2.0D;

    private static final double REACH_BELOW = 1.0D;
    private static final double REACH_ABOVE = 3.0D;

    private static final int LIMIT = 9;

    public static final float DAMAGE = 9.0F;

    private static final double THROW_SPEED = 1.2D;

    private static final int SMOKE_PUFFS = 5;
    private static final int SMOKE_INTERVAL_TICKS = 2;

    private static final int RUPTURE_SOUND_DELAY_TICKS = 2;

    private ViciousStrike() {
    }

    public static void charge(ServerLevel level, ServerPlayer wielder) {
        HexwrightNetworking.sendViciousStrikeVfx(level, VFX_CHARGE, wielder.position(), wielder.getYRot(),
            wielder.getId());
        level.playSound(null, wielder.getX(), wielder.getY(), wielder.getZ(), AbyssCleaverSounds.CHARGE.get(),
            SoundSource.PLAYERS, 0.7F, pitch(level.getRandom(), 0.9F, 1.1F));
    }

    public static void release(ServerLevel level, ServerPlayer wielder) {
        if (wielder.isRemoved() || !wielder.isAlive() || wielder.level() != level) {
            return;
        }
        float yaw = wielder.getYRot();
        float yawRad = yaw * Mth.DEG_TO_RAD;
        Vec3 heading = new Vec3(-Mth.sin(yawRad), 0.0D, Mth.cos(yawRad));
        Vec3 origin = wielder.position();
        Vec3 end = origin.add(heading.scale(REACH));
        RandomSource random = level.getRandom();

        HexwrightNetworking.sendViciousStrikeVfx(level, VFX_RUPTURE,
            Hailpiercer.onSurface(level, origin.add(heading.scale(RUPTURE_FORWARD))), yaw, -1);
        HexwrightNetworking.sendViciousStrikeVfx(level, VFX_SLASH, origin.add(0.0D, SLASH_HEIGHT, 0.0D), yaw, -1);
        level.playSound(null, origin.x, origin.y, origin.z, AbyssCleaverSounds.STOMP.get(),
            SoundSource.PLAYERS, 0.7F, pitch(random, 0.9F, 1.1F));
        SlamWindUp.schedule(level.getServer(), RUPTURE_SOUND_DELAY_TICKS,
            () -> level.playSound(null, origin.x, origin.y, origin.z, AbyssCleaverSounds.RUPTURE.get(),
                SoundSource.PLAYERS, 0.7F, pitch(level.getRandom(), 0.9F, 1.1F)));
        for (int puff = 0; puff < SMOKE_PUFFS; puff++) {
            Vec3 at = origin.add(heading.scale(REACH * (puff + 1) / (SMOKE_PUFFS + 1)));
            SlamWindUp.schedule(level.getServer(), puff * SMOKE_INTERVAL_TICKS,
                () -> level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.5D, at.z,
                    4, 0.4D, 0.1D, 0.4D, 0.1D));
        }

        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class,
            new AABB(origin, end).inflate(HALF_WIDTH, 0.0D, HALF_WIDTH)
                .expandTowards(0.0D, REACH_ABOVE, 0.0D).expandTowards(0.0D, -REACH_BELOW, 0.0D),
            victim -> TeleportStrike.strikeable(wielder, victim) && withinLine(victim, origin, end));
        caught.sort(Comparator.comparingDouble(victim -> victim.distanceToSqr(origin)));
        if (caught.size() > LIMIT) {
            caught.subList(LIMIT, caught.size()).clear();
        }

        DamageSource source = level.damageSources().playerAttack(wielder);
        List<LivingEntity> struck = new ArrayList<>();
        for (LivingEntity victim : caught) {
            Vec3 motion = victim.getDeltaMovement();
            victim.invulnerableTime = 0;
            if (!victim.hurt(source, Bosses.sparedDamage(victim, DAMAGE))) {
                continue;
            }
            if (Bosses.is(victim)) {
                victim.setDeltaMovement(motion);
            } else {
                stripExtraHealth(victim);
                shove(victim, wielder, motion);
            }
            struck.add(victim);

            Vec3 hit = victim.getBoundingBox().getCenter();
            HexwrightNetworking.sendSkyfallVfx(level, PiercingSkyfall.VFX_HIT, hit, 0.0F, 0);
            level.playSound(null, hit.x, hit.y, hit.z, StarfallSounds.HIT_THUD.get(),
                SoundSource.PLAYERS, 0.45F, pitch(random, 1.1F, 1.2F));
        }

        HexwrightDebug.log(HexwrightDebug.WEAPON, "[weapon] vicious strike from {}: {} caught, {} struck",
            wielder.getScoreboardName(), caught.size(), struck.size());
    }

    private static void stripExtraHealth(LivingEntity victim) {
        float absorption = victim.getAbsorptionAmount();
        victim.removeEffect(MobEffects.ABSORPTION);
        victim.removeEffect(MobEffects.HEALTH_BOOST);
        victim.setAbsorptionAmount(0.0F);
        HexwrightDebug.log(HexwrightDebug.WEAPON, "[weapon] vicious strike stripped {} absorption from {}",
            absorption, victim);
    }

    private static void shove(LivingEntity victim, ServerPlayer wielder, Vec3 motion) {
        double dx = victim.getX() - wielder.getX();
        double dz = victim.getZ() - wielder.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double speed = THROW_SPEED * (1.0D - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        if (distance < 1.0E-4D || speed <= 0.0D) {
            victim.setDeltaMovement(motion);
            return;
        }
        victim.setDeltaMovement(dx / distance * speed, motion.y, dz / distance * speed);
        victim.hurtMarked = true;
    }

    private static boolean withinLine(LivingEntity victim, Vec3 origin, Vec3 end) {
        double reach = HALF_WIDTH + victim.getBbWidth() / 2.0D;
        return Hailpiercer.horizontalDistanceToSegmentSq(victim.position(), origin, end) <= reach * reach;
    }

    private static float pitch(RandomSource random, float low, float high) {
        return low + random.nextFloat() * (high - low);
    }
}
