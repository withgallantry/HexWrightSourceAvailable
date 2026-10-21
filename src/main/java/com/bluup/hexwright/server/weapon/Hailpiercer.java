package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.inits.HexwrightNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Hailpiercer {

    public static final int VFX_INHALE = 0;
    public static final int VFX_SPIKE_NEAR = 1;
    public static final int VFX_SPIKE_SIDE = 2;
    public static final int VFX_SPIKE_FAR = 3;

    private static final double INHALE_FORWARD = 1.5D;

    private static final int PULLS = 5;
    private static final int PULL_INTERVAL_TICKS = 5;

    private static final double PULL_REACH = 9.0D;

    private static final double PULL_RADIUS = 3.0D;

    private static final double PULL_SPEED = 0.35D;

    private static final double PULL_CLOSEST = 2.0D;

    public static final int ERUPT_TICKS = 30;

    private record Spike(int delayTicks, double forward, double right, int vfx) {
    }

    private static final List<Spike> SPIKES = List.of(
        new Spike(0, 2.0D, 0.0D, VFX_SPIKE_NEAR),
        new Spike(5, 6.0D, -1.0D, VFX_SPIKE_SIDE),
        new Spike(10, 10.0D, 0.0D, VFX_SPIKE_FAR));

    private static final int SPIKE_BREAK_TICKS = 31;
    private static final int SPIKE_CRACK_TICKS = 33;

    private record Burst(int delayTicks, double forward) {
    }

    private static final List<Burst> BURSTS = List.of(
        new Burst(2, 3.0D),
        new Burst(7, 9.0D),
        new Burst(12, 15.0D));

    private static final int LAST_BURST_TICKS = ERUPT_TICKS + 12;

    public static final double BURST_RADIUS = 6.0D;
    private static final int BURST_LIMIT = 5;

    public static final float BURST_DAMAGE = 8.0F;

    public static final int SLOW_TICKS = 80;

    private static final int SURFACE_SEARCH_DOWN = 4;
    private static final int SURFACE_SEARCH_UP = 2;

    private Hailpiercer() {
    }

    public static void start(ServerLevel level, ServerPlayer wielder, @Nullable CompoundTag hex) {
        float yaw = wielder.getYRot();
        float yawRad = yaw * Mth.DEG_TO_RAD;
        Vec3 heading = new Vec3(-Mth.sin(yawRad), 0.0D, Mth.cos(yawRad));
        Vec3 right = new Vec3(-heading.z, 0.0D, heading.x);
        Vec3 origin = wielder.position();
        RandomSource random = level.getRandom();

        HexwrightNetworking.sendHailpiercerVfx(level, VFX_INHALE,
            onSurface(level, origin.add(heading.scale(INHALE_FORWARD))), yaw);
        level.playSound(null, origin.x, origin.y, origin.z, SilverStarlightSounds.ICE_CHARGE.get(),
            SoundSource.PLAYERS, 1.0F, pitch(random, 0.9F, 1.1F));

        for (int pull = 0; pull < PULLS; pull++) {
            SlamWindUp.schedule(level.getServer(), pull * PULL_INTERVAL_TICKS,
                () -> pull(level, wielder, origin, heading));
        }
        for (Spike spike : SPIKES) {
            Vec3 at = onSurface(level, origin.add(heading.scale(spike.forward())).add(right.scale(spike.right())));
            SlamWindUp.schedule(level.getServer(), ERUPT_TICKS + spike.delayTicks(),
                () -> erupt(level, wielder, at, heading, yaw, spike.vfx()));
        }

        Set<LivingEntity> struck = new LinkedHashSet<>();
        for (Burst burst : BURSTS) {
            Vec3 centre = origin.add(heading.scale(burst.forward())).add(0.0D, 1.0D, 0.0D);
            SlamWindUp.schedule(level.getServer(), ERUPT_TICKS + burst.delayTicks(),
                () -> burst(level, wielder, centre, struck));
        }
        if (hex != null) {
            Vec3 impact = origin.add(heading.scale(BURSTS.get(1).forward()));
            SlamWindUp.schedule(level.getServer(), LAST_BURST_TICKS,
                () -> {
                    if (canAct(level, wielder)) {
                        WeaponHexCasting.castOnCaught(wielder, hex, List.copyOf(struck), impact);
                    }
                });
        }
    }

    private static void pull(ServerLevel level, ServerPlayer wielder, Vec3 origin, Vec3 heading) {
        if (!canAct(level, wielder)) {
            return;
        }
        Vec3 end = origin.add(heading.scale(PULL_REACH));
        double radiusSq = PULL_RADIUS * PULL_RADIUS;
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class,
            new AABB(origin, end).inflate(PULL_RADIUS),
            victim -> TeleportStrike.strikeable(wielder, victim)
                && horizontalDistanceToSegmentSq(victim.position(), origin, end) <= radiusSq);
        for (LivingEntity victim : caught) {
            double dx = wielder.getX() - victim.getX();
            double dz = wielder.getZ() - victim.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance <= PULL_CLOSEST) {
                continue;
            }
            Vec3 motion = victim.getDeltaMovement();
            victim.setDeltaMovement(dx / distance * PULL_SPEED, motion.y, dz / distance * PULL_SPEED);
            victim.hurtMarked = true;
        }
    }

    private static void erupt(ServerLevel level, ServerPlayer wielder, Vec3 at, Vec3 heading, float yaw, int vfx) {
        if (!canAct(level, wielder)) {
            return;
        }
        RandomSource random = level.getRandom();
        HexwrightNetworking.sendHailpiercerVfx(level, vfx, at, yaw);
        level.playSound(null, at.x, at.y, at.z, SilverStarlightSounds.ICE_SPIKE.get(),
            SoundSource.PLAYERS, 0.7F, pitch(random, 0.9F, 1.1F));

        Vec3 body = at.add(heading.scale(3.0D)).add(0.0D, 1.0D, 0.0D);
        SlamWindUp.schedule(level.getServer(), SPIKE_BREAK_TICKS,
            () -> level.playSound(null, body.x, body.y, body.z, SilverStarlightSounds.ICE_BREAK.get(),
                SoundSource.PLAYERS, 0.7F, pitch(level.getRandom(), 0.9F, 1.1F)));
        SlamWindUp.schedule(level.getServer(), SPIKE_CRACK_TICKS,
            () -> level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()),
                body.x, body.y, body.z, 10, 0.3D, 0.3D, 0.3D, 0.0D));
    }

    private static void burst(ServerLevel level, ServerPlayer wielder, Vec3 centre, Set<LivingEntity> struck) {
        if (!canAct(level, wielder)) {
            return;
        }
        double radiusSq = BURST_RADIUS * BURST_RADIUS;
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class,
            new AABB(centre, centre).inflate(BURST_RADIUS),
            victim -> TeleportStrike.strikeable(wielder, victim)
                && victim.getBoundingBox().distanceToSqr(centre) <= radiusSq);
        caught.sort(Comparator.comparingDouble(victim -> victim.getBoundingBox().distanceToSqr(centre)));
        if (caught.size() > BURST_LIMIT) {
            caught.subList(BURST_LIMIT, caught.size()).clear();
        }

        RandomSource random = level.getRandom();
        DamageSource source = level.damageSources().playerAttack(wielder);
        for (LivingEntity victim : caught) {
            Vec3 motion = victim.getDeltaMovement();
            victim.invulnerableTime = 0;
            victim.hurt(source, BURST_DAMAGE);
            victim.setDeltaMovement(motion);
            if (victim.isAlive()) {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 0,
                    false, false, true), wielder);
            }
            struck.add(victim);

            Vec3 hit = victim.getBoundingBox().getCenter();
            HexwrightNetworking.sendSkyfallVfx(level, PiercingSkyfall.VFX_HIT, hit, 0.0F, 0);
            level.playSound(null, hit.x, hit.y, hit.z, StarfallSounds.HIT_THUD.get(),
                SoundSource.PLAYERS, 0.45F, pitch(random, 1.1F, 1.2F));
        }
    }

    static Vec3 onSurface(ServerLevel level, Vec3 at) {
        BlockPos start = BlockPos.containing(at.x, at.y + SURFACE_SEARCH_UP, at.z);
        for (int down = 0; down <= SURFACE_SEARCH_UP + SURFACE_SEARCH_DOWN; down++) {
            BlockPos pos = start.below(down);
            BlockPos below = pos.below();
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return new Vec3(at.x, pos.getY(), at.z);
            }
        }
        return at;
    }

    static double horizontalDistanceToSegmentSq(Vec3 point, Vec3 from, Vec3 to) {
        double sx = to.x - from.x;
        double sz = to.z - from.z;
        double lengthSq = sx * sx + sz * sz;
        double t = lengthSq <= 0.0D ? 0.0D
            : Mth.clamp(((point.x - from.x) * sx + (point.z - from.z) * sz) / lengthSq, 0.0D, 1.0D);
        double dx = point.x - (from.x + sx * t);
        double dz = point.z - (from.z + sz * t);
        return dx * dx + dz * dz;
    }

    private static boolean canAct(ServerLevel level, ServerPlayer wielder) {
        return !wielder.isRemoved() && wielder.isAlive() && wielder.level() == level;
    }

    private static float pitch(RandomSource random, float low, float high) {
        return low + random.nextFloat() * (high - low);
    }
}
