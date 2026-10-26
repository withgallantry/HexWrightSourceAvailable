package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.boss.Bosses;
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
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FrozenPursuit {

    public static final int VFX_DASH = 0;
    public static final int VFX_CUT = 1;

    public static final int CUT_VARIANTS = 5;

    public static final int CUTS = 7;

    private static final int CUT_INTERVAL_TICKS = 3;

    private static final int CUT_DELAY_TICKS = 20;

    private static final float CUT_DAMAGE = 3.0F;

    private static final int LUNGES = 3;
    private static final double LUNGE_SPEED = 1.8D;
    private static final double LUNGE_DROP = -0.05D;

    private static final int MARK_TICKS = 15;

    private static final double MARK_RADIUS = 3.0D;

    private static final int MARKS_PER_TICK = 6;

    private static final int MARK_LIMIT = 12;

    public static final int DURATION_TICKS =
        (MARK_TICKS - 1) + CUT_DELAY_TICKS + (CUTS - 1) * CUT_INTERVAL_TICKS;

    private static final int FREEZE_TICKS = CUT_DELAY_TICKS + (CUTS - 1) * CUT_INTERVAL_TICKS + 4;

    private static final int FREEZE_AMPLIFIER = 9;

    private FrozenPursuit() {
    }

    public static void start(ServerLevel level, ServerPlayer wielder, @Nullable CompoundTag hex) {
        float yaw = wielder.getYRot();
        float yawRad = yaw * Mth.DEG_TO_RAD;
        Vec3 heading = new Vec3(-Mth.sin(yawRad), 0.0D, Mth.cos(yawRad));

        Vec3 at = wielder.position();
        RandomSource random = level.getRandom();
        HexwrightNetworking.sendFrostPursuitVfx(level, VFX_DASH, at, yaw, 0);
        level.playSound(null, at.x, at.y, at.z, FrostbiteSounds.STRONG_SLASH.get(),
            SoundSource.PLAYERS, 0.85F, pitch(random, 1.3F, 1.4F));
        level.playSound(null, at.x, at.y, at.z, FrostbiteSounds.DASH.get(),
            SoundSource.PLAYERS, 0.8F, pitch(random, 1.1F, 1.2F));

        for (int lunge = 0; lunge < LUNGES; lunge++) {
            SlamWindUp.schedule(level.getServer(), lunge, () -> lunge(level, wielder, heading));
        }

        Set<Integer> marked = new HashSet<>();
        for (int tick = 0; tick < MARK_TICKS; tick++) {
            SlamWindUp.schedule(level.getServer(), tick, () -> mark(level, wielder, hex, marked));
        }
    }

    private static void lunge(ServerLevel level, ServerPlayer wielder, Vec3 heading) {
        if (!canAct(level, wielder)) {
            return;
        }
        wielder.setDeltaMovement(heading.x * LUNGE_SPEED, LUNGE_DROP, heading.z * LUNGE_SPEED);
        wielder.hurtMarked = true;
        wielder.fallDistance = 0.0F;
    }

    private static void mark(ServerLevel level, ServerPlayer wielder, @Nullable CompoundTag hex,
                             Set<Integer> marked) {
        if (!canAct(level, wielder) || marked.size() >= MARK_LIMIT) {
            return;
        }
        Vec3 body = wielder.getBoundingBox().getCenter();
        double radiusSq = MARK_RADIUS * MARK_RADIUS;
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class,
            wielder.getBoundingBox().inflate(MARK_RADIUS),
            victim -> victim.isAlive() && victim != wielder && !victim.isSpectator()
                && !(victim instanceof ArmorStand) && !marked.contains(victim.getId())
                && victim.getBoundingBox().distanceToSqr(body) <= radiusSq);
        near.sort(Comparator.comparingDouble(victim -> victim.getBoundingBox().distanceToSqr(body)));

        int count = Math.min(Math.min(near.size(), MARKS_PER_TICK), MARK_LIMIT - marked.size());
        for (int i = 0; i < count; i++) {
            LivingEntity victim = near.get(i);
            marked.add(victim.getId());
            freeze(victim);
            for (int cut = 0; cut < CUTS; cut++) {
                boolean first = cut == 0;
                SlamWindUp.schedule(level.getServer(), CUT_DELAY_TICKS + cut * CUT_INTERVAL_TICKS,
                    () -> cut(level, wielder, victim, first ? hex : null));
            }
        }
    }

    private static void freeze(LivingEntity victim) {
        if (Bosses.is(victim)) {
            return;
        }
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FREEZE_TICKS,
            FREEZE_AMPLIFIER, false, false, true));
        frost(victim);
    }

    private static void frost(LivingEntity victim) {
        if (victim.canFreeze() && !Bosses.is(victim)) {
            victim.setTicksFrozen(Math.max(victim.getTicksFrozen(),
                victim.getTicksRequiredToFreeze() + FREEZE_TICKS * 2));
        }
    }

    private static void cut(ServerLevel level, ServerPlayer wielder, LivingEntity victim,
                            @Nullable CompoundTag hex) {
        if (!victim.isAlive() || victim.isRemoved() || victim.level() != level) {
            return;
        }
        DamageSource source = level.damageSources().playerAttack(wielder);
        Vec3 motion = victim.getDeltaMovement();
        victim.invulnerableTime = 0;
        victim.hurt(source, CUT_DAMAGE);
        victim.setDeltaMovement(motion);
        frost(victim);

        RandomSource random = level.getRandom();
        Vec3 at = victim.position().add(0.0D, Math.min(1.2D, victim.getBbHeight() * 0.6D), 0.0D);
        HexwrightNetworking.sendFrostPursuitVfx(level, VFX_CUT, at, facing(wielder, victim),
            random.nextInt(CUT_VARIANTS));
        level.playSound(null, at.x, at.y, at.z, FrostbiteSounds.SLASH.get(),
            SoundSource.PLAYERS, 0.75F, pitch(random, 1.3F, 1.4F));
        level.playSound(null, at.x, at.y, at.z, StarfallSounds.HIT_THUD.get(),
            SoundSource.PLAYERS, 0.45F, pitch(random, 1.1F, 1.2F));

        if (hex != null && canAct(level, wielder)) {
            WeaponHexCasting.castOnTarget(wielder, hex, victim, victim.position());
        }
    }

    private static float facing(ServerPlayer wielder, LivingEntity victim) {
        double dx = victim.getX() - wielder.getX();
        double dz = victim.getZ() - wielder.getZ();
        if (wielder.isRemoved() || wielder.level() != victim.level() || dx * dx + dz * dz < 1.0E-4D) {
            return victim.getYRot();
        }
        return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private static boolean canAct(ServerLevel level, ServerPlayer wielder) {
        return !wielder.isRemoved() && wielder.isAlive() && wielder.level() == level;
    }

    private static float pitch(RandomSource random, float low, float high) {
        return low + random.nextFloat() * (high - low);
    }
}
