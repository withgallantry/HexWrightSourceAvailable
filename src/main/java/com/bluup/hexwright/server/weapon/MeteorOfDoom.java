package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.boss.Bosses;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MeteorOfDoom {

    public static final int VFX_CHARGE = 0;
    public static final int VFX_FLIGHT = 1;
    public static final int VFX_CROSS = 2;
    public static final int VFX_BURST = 3;

    private static final int RISE_TICKS = 24;
    private static final int HOVER_END_TICKS = 50;
    private static final int SLOW_FALL_TICKS = 40;

    public static final int CHARGE_TICKS = 40;

    private static final double LAUNCH_HEIGHT = 6.0D;

    public static final double RANGE = 32.0D;

    private static final double FLIGHT_SPEED = 1.25D;
    private static final int MIN_FLIGHT_TICKS = 6;

    public static final double IMPACT_RADIUS = 8.0D;
    private static final int IMPACT_LIMIT = 12;
    public static final float IMPACT_DAMAGE = 18.0F;
    public static final int IGNITE_SECONDS = 8;
    private static final double THROW_SPEED = 1.1D;

    public static final float WIELDER_SHARE = 0.5F;

    public static final float BLOOD_PRICE = 0.05F;

    private static final double[][] FIRES = {{0, 0}, {4, 4}, {-4, 4}, {4, -4}, {-4, -4}};
    private static final double FIRE_RADIUS = 2.0D;
    public static final int BURN_TICKS = 80;
    private static final int BURN_INTERVAL_TICKS = 10;
    public static final float BURN_DAMAGE = 2.0F;
    private static final int BURN_IGNITE_SECONDS = 4;

    private static final int[][] BURSTS = {{2, 0, 0}, {5, 3, 3}, {8, 6, 6}};

    private MeteorOfDoom() {
    }

    public static boolean start(ServerLevel level, ServerPlayer wielder, Item blade) {
        if (!payBlood(level, wielder)) {
            wielder.displayClientMessage(Component.translatable("message.hexwright.dragonborn_broadsword.too_weak"), true);
            return false;
        }

        wielder.addEffect(new MobEffectInstance(MobEffects.LEVITATION, RISE_TICKS, 1, false, false, false));
        HexwrightNetworking.sendMeteorVfx(level, VFX_CHARGE, wielder.position(), wielder.getYRot(), wielder.getId(),
            wielder.position(), CHARGE_TICKS);
        level.playSound(null, wielder.getX(), wielder.getY(), wielder.getZ(), DragonbornSounds.METEOR_CREATION.get(),
            SoundSource.PLAYERS, 1.0F, pitch(level.getRandom(), 0.9F, 1.1F));

        SlamWindUp.schedule(level.getServer(), RISE_TICKS, () -> {
            if (canAct(level, wielder)) {
                wielder.removeEffect(MobEffects.LEVITATION);
                wielder.addEffect(new MobEffectInstance(MobEffects.LEVITATION, HOVER_END_TICKS - RISE_TICKS, -1,
                    false, false, false));
            }
        });
        SlamWindUp.schedule(level.getServer(), HOVER_END_TICKS, () -> {
            if (canAct(level, wielder)) {
                wielder.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, false, false));
            }
        });
        SlamWindUp.schedule(level.getServer(), CHARGE_TICKS, () -> launch(level, wielder, blade));
        return true;
    }

    private static boolean payBlood(ServerLevel level, ServerPlayer wielder) {
        if (wielder.getAbilities().instabuild) {
            return true;
        }
        float price = wielder.getMaxHealth() * BLOOD_PRICE;
        if (wielder.getHealth() - price < 1.0F) {
            return false;
        }
        wielder.setHealth(wielder.getHealth() - price);
        level.playSound(null, wielder.getX(), wielder.getY(), wielder.getZ(), SoundEvents.PLAYER_HURT_ON_FIRE,
            SoundSource.PLAYERS, 0.8F, 0.8F);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, wielder.getX(), wielder.getY(0.6D), wielder.getZ(),
            6, 0.3D, 0.3D, 0.3D, 0.1D);
        return true;
    }

    private static void launch(ServerLevel level, ServerPlayer wielder, Item blade) {
        if (!canAct(level, wielder) || !wielder.getMainHandItem().is(blade)) {
            return;
        }
        Vec3 target = target(level, wielder);
        float yaw = wielder.getYRot();
        Vec3 heading = Vec3.directionFromRotation(0.0F, yaw);
        Vec3 from = wielder.position().add(0.0D, LAUNCH_HEIGHT, 0.0D).subtract(heading.scale(0.5D));
        int flight = Math.max(MIN_FLIGHT_TICKS, Mth.ceil(from.distanceTo(target) / FLIGHT_SPEED));
        float flightYaw = (float) (Mth.atan2(target.z - from.z, target.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;

        HexwrightNetworking.sendMeteorVfx(level, VFX_FLIGHT, from, flightYaw, -1, target, flight);
        level.playSound(null, wielder.getX(), wielder.getY(), wielder.getZ(), DragonbornSounds.METEOR_SHOOT.get(),
            SoundSource.PLAYERS, 0.8F, pitch(level.getRandom(), 0.9F, 1.1F));

        SlamWindUp.schedule(level.getServer(), flight, () -> impact(level, wielder, target));
        HexwrightDebug.log(HexwrightDebug.WEAPON, "[weapon] meteor from {} at {}, {} ticks out",
            wielder.getScoreboardName(), target, flight);
    }

    private static Vec3 target(ServerLevel level, ServerPlayer wielder) {
        HitResult hit = wielder.pick(RANGE, 1.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation();
        }
        Vec3 end = wielder.getEyePosition().add(wielder.getViewVector(1.0F).scale(RANGE));
        BlockHitResult ground = level.clip(new ClipContext(end, end.subtract(0.0D, RANGE, 0.0D),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, wielder));
        return ground.getType() == HitResult.Type.BLOCK ? ground.getLocation() : end;
    }

    private static void impact(ServerLevel level, ServerPlayer wielder, Vec3 at) {
        if (wielder.level() != level) {
            return;
        }
        RandomSource random = level.getRandom();
        HexwrightNetworking.sendMeteorVfx(level, VFX_CROSS, at, 0.0F, -1, at, 0);
        level.playSound(null, at.x, at.y, at.z, DragonbornSounds.METEOR_EXPLOSION.get(),
            SoundSource.PLAYERS, 1.0F, pitch(random, 0.9F, 1.1F));
        level.playSound(null, at.x, at.y, at.z, DragonbornSounds.RUPTURE.get(),
            SoundSource.PLAYERS, 0.8F, pitch(random, 0.9F, 1.1F));
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.1D, at.z, 25, 2.3D, 1.2D, 2.3D, 0.1D);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.1D, at.z, 40, 2.3D, 1.2D, 2.3D, 0.1D);
        for (int[] ring : BURSTS) {
            SlamWindUp.schedule(level.getServer(), ring[2], () -> bursts(level, at, ring[0], ring[1]));
        }

        Set<LivingEntity> caught = new LinkedHashSet<>(blast(level, wielder, at));
        for (int tick = BURN_INTERVAL_TICKS; tick <= BURN_TICKS; tick += BURN_INTERVAL_TICKS) {
            SlamWindUp.schedule(level.getServer(), tick, () -> burn(level, wielder, at, caught));
        }
    }

    private static List<LivingEntity> blast(ServerLevel level, ServerPlayer wielder, Vec3 at) {
        double radiusSq = IMPACT_RADIUS * IMPACT_RADIUS;
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class,
            new AABB(at, at).inflate(IMPACT_RADIUS),
            victim -> TeleportStrike.strikeable(wielder, victim) && victim.getBoundingBox().distanceToSqr(at) <= radiusSq);
        caught.sort(Comparator.comparingDouble(victim -> victim.getBoundingBox().distanceToSqr(at)));
        if (caught.size() > IMPACT_LIMIT) {
            caught.subList(IMPACT_LIMIT, caught.size()).clear();
        }

        RandomSource random = level.getRandom();
        DamageSource source = level.damageSources().playerAttack(wielder);
        List<LivingEntity> struck = new ArrayList<>();
        for (LivingEntity victim : caught) {
            victim.invulnerableTime = 0;
            if (!victim.hurt(source, IMPACT_DAMAGE)) {
                continue;
            }
            DragonbornBroadswordItem.kindle(victim, IGNITE_SECONDS);
            throwFrom(victim, at);
            struck.add(victim);
            Vec3 hit = victim.getBoundingBox().getCenter();
            HexwrightNetworking.sendSkyfallVfx(level, PiercingSkyfall.VFX_HIT, hit, 0.0F, 0);
            level.playSound(null, hit.x, hit.y, hit.z, StarfallSounds.HIT_THUD.get(),
                SoundSource.PLAYERS, 0.45F, pitch(random, 1.1F, 1.2F));
        }

        if (wielder.isAlive() && wielder.level() == level && wielder.getBoundingBox().distanceToSqr(at) <= radiusSq) {
            wielder.invulnerableTime = 0;
            if (wielder.hurt(level.damageSources().explosion(null, null), IMPACT_DAMAGE * WIELDER_SHARE)) {
                DragonbornBroadswordItem.kindle(wielder, IGNITE_SECONDS);
            }
        }
        HexwrightDebug.log(HexwrightDebug.WEAPON, "[weapon] meteor blast: {} caught, {} struck", caught.size(), struck.size());
        return struck;
    }

    private static void bursts(ServerLevel level, Vec3 at, int count, double radius) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = radius * Math.sqrt(random.nextDouble());
            Vec3 spot = Hailpiercer.onSurface(level, at.add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance));
            HexwrightNetworking.sendMeteorVfx(level, VFX_BURST, spot, random.nextFloat() * 360.0F, -1, spot,
                random.nextInt(2));
        }
        level.playSound(null, at.x, at.y, at.z, DragonbornSounds.FIRE_EXPLODE.get(),
            SoundSource.PLAYERS, 0.6F, pitch(random, 0.8F, 1.0F));
    }

    private static void burn(ServerLevel level, ServerPlayer wielder, Vec3 at, Set<LivingEntity> caught) {
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(DamageTypes.IN_FIRE), wielder);
        double radiusSq = FIRE_RADIUS * FIRE_RADIUS;
        for (double[] offset : FIRES) {
            Vec3 fire = at.add(offset[0], 0.0D, offset[1]);
            level.sendParticles(ParticleTypes.FLAME, fire.x, fire.y + 0.2D, fire.z, 8, 1.0D, 0.3D, 1.0D, 0.02D);
            level.sendParticles(ParticleTypes.LAVA, fire.x, fire.y + 0.2D, fire.z, 1, 0.8D, 0.1D, 0.8D, 0.0D);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(fire, fire).inflate(FIRE_RADIUS, 1.5D, FIRE_RADIUS),
                victim -> victim.isAlive() && !victim.isSpectator()
                    && horizontalDistanceSq(victim.position(), fire) <= radiusSq)) {
                if (victim != wielder && !TeleportStrike.strikeable(wielder, victim)) {
                    continue;
                }
                victim.invulnerableTime = 0;
                if (victim.hurt(source, BURN_DAMAGE)) {
                    DragonbornBroadswordItem.kindle(victim, BURN_IGNITE_SECONDS);
                    if (victim != wielder) {
                        caught.add(victim);
                    }
                }
            }
        }
    }

    private static void throwFrom(LivingEntity victim, Vec3 at) {
        double speed = THROW_SPEED * (1.0D - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        if (Bosses.is(victim) || speed <= 0.0D) {
            return;
        }
        double dx = victim.getX() - at.x;
        double dz = victim.getZ() - at.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        Vec3 out = distance < 1.0E-4D ? Vec3.ZERO : new Vec3(dx / distance, 0.0D, dz / distance);
        victim.setDeltaMovement(out.x * speed, 0.45D, out.z * speed);
        victim.hurtMarked = true;
    }

    private static double horizontalDistanceSq(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    private static boolean canAct(ServerLevel level, ServerPlayer wielder) {
        return !wielder.isRemoved() && wielder.isAlive() && wielder.level() == level;
    }

    private static float pitch(RandomSource random, float low, float high) {
        return low + random.nextFloat() * (high - low);
    }
}
