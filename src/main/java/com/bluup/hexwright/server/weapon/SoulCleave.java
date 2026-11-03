package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.common.animation.PlayerAnimationLayer;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.boss.Bosses;
import com.bluup.hexwright.server.effect.HexwrightEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SoulCleave {

    public static final int VFX_SLASH_RIGHT = 0;
    public static final int VFX_SLASH_LEFT = 1;
    public static final int VFX_PIERCE = 2;

    public static final int CLEAVE_TICKS = 120;
    public static final int SELF_CLEAVE_TICKS = 100;

    private static final int SECOND_CUT_TICKS = 10;
    private static final int LUNGE_TICKS = 20;
    private static final int BLOW_DELAY_TICKS = 3;
    public static final int DURATION_TICKS = 30;

    private static final double CUT_FORWARD = 2.5D;
    private static final double CUT_RADIUS = 3.0D;
    public static final float CUT_DAMAGE = 6.0F;

    private static final double THRUST_FORWARD = 3.0D;
    private static final double THRUST_RADIUS = 3.0D;
    public static final float THRUST_DAMAGE = 9.0F;

    private static final double LUNGE_SPEED = 1.0D;
    private static final int LUNGE_PUSHES = 3;

    private static final String[] CUT_CLIPS = {"sword_diagonal_slash_1", "sword_diagonal_slash_2"};
    private static final String THRUST_CLIP = "sword_diagonal_slash_3_running";

    private SoulCleave() {
    }

    public static void start(ServerLevel level, ServerPlayer wielder, Item blade) {
        Cleave cleave = new Cleave(level, wielder, blade);
        wielder.addEffect(new MobEffectInstance(HexwrightEffects.SOUL_CLEAVED, SELF_CLEAVE_TICKS, 0,
            false, false, true));
        wielder.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SELF_CLEAVE_TICKS, 1));

        cut(cleave, 0);
        SlamWindUp.schedule(level.getServer(), SECOND_CUT_TICKS, () -> cut(cleave, 1));
        SlamWindUp.schedule(level.getServer(), LUNGE_TICKS, () -> lunge(cleave));
        SlamWindUp.schedule(level.getServer(), DURATION_TICKS, () -> finish(cleave));
        HexwrightDebug.log(HexwrightDebug.WEAPON, "[weapon] soul cleave from {}", wielder.getScoreboardName());
    }

    private static void cut(Cleave cleave, int index) {
        if (!cleave.live()) {
            return;
        }
        ServerPlayer wielder = cleave.wielder;
        ServerLevel level = cleave.level;
        HexwrightNetworking.sendPlayerAnimation(wielder, PlayerAnimationLayer.ONE_SHOT, CUT_CLIPS[index]);
        HexwrightNetworking.sendSoulCleaveVfx(level, index == 0 ? VFX_SLASH_RIGHT : VFX_SLASH_LEFT,
            ahead(wielder, 0.5D, 1.0D), wielder.getYRot());
        level.playSound(null, wielder.getX(), wielder.getY(), wielder.getZ(), ShadowMonarchSounds.SLASH.get(),
            SoundSource.PLAYERS, 0.8F, pitch(level.getRandom(), 0.9F, 1.1F));
        SlamWindUp.schedule(level.getServer(), BLOW_DELAY_TICKS, () -> {
            if (cleave.live()) {
                cleave.hit(caught(cleave, CUT_FORWARD, CUT_RADIUS), CUT_DAMAGE);
            }
        });
    }

    private static void lunge(Cleave cleave) {
        if (!cleave.live()) {
            return;
        }
        ServerPlayer wielder = cleave.wielder;
        ServerLevel level = cleave.level;
        HexwrightNetworking.sendPlayerAnimation(wielder, PlayerAnimationLayer.ONE_SHOT, THRUST_CLIP);
        level.playSound(null, wielder.getX(), wielder.getY(), wielder.getZ(), ShadowMonarchSounds.MOVE.get(),
            SoundSource.PLAYERS, 0.7F, pitch(level.getRandom(), 0.9F, 1.1F));
        for (int push = 0; push < LUNGE_PUSHES; push++) {
            SlamWindUp.schedule(level.getServer(), push, () -> {
                if (cleave.live()) {
                    Vec3 heading = Vec3.directionFromRotation(0.0F, wielder.getYRot());
                    wielder.setDeltaMovement(heading.x * LUNGE_SPEED, wielder.getDeltaMovement().y,
                        heading.z * LUNGE_SPEED);
                    wielder.hurtMarked = true;
                }
            });
        }
        SlamWindUp.schedule(level.getServer(), BLOW_DELAY_TICKS, () -> {
            if (!cleave.live()) {
                return;
            }
            HexwrightNetworking.sendSoulCleaveVfx(level, VFX_PIERCE, ahead(wielder, 1.5D, 0.9D), wielder.getYRot());
            level.playSound(null, wielder.getX(), wielder.getY(), wielder.getZ(), ShadowMonarchSounds.THRUST.get(),
                SoundSource.PLAYERS, 0.8F, pitch(level.getRandom(), 0.9F, 1.1F));
            cleave.hit(caught(cleave, THRUST_FORWARD, THRUST_RADIUS), THRUST_DAMAGE);
        });
    }

    private static void finish(Cleave cleave) {
        HexwrightDebug.log(HexwrightDebug.WEAPON, "[weapon] soul cleave from {} done, {} struck",
            cleave.wielder.getScoreboardName(), cleave.struck.size());
    }

    private static List<LivingEntity> caught(Cleave cleave, double forward, double radius) {
        ServerPlayer wielder = cleave.wielder;
        Vec3 centre = wielder.position().add(Vec3.directionFromRotation(0.0F, wielder.getYRot()).scale(forward));
        double radiusSq = radius * radius;
        return cleave.level.getEntitiesOfClass(LivingEntity.class,
            new AABB(centre.x - radius, wielder.getY() - 1.5D, centre.z - radius,
                centre.x + radius, wielder.getY() + 3.0D, centre.z + radius),
            victim -> TeleportStrike.strikeable(wielder, victim)
                && victim.getBoundingBox().distanceToSqr(centre) <= radiusSq);
    }

    private static Vec3 ahead(ServerPlayer wielder, double forward, double up) {
        return wielder.position().add(Vec3.directionFromRotation(0.0F, wielder.getYRot()).scale(forward))
            .add(0.0D, up, 0.0D);
    }

    private static float pitch(RandomSource random, float low, float high) {
        return low + random.nextFloat() * (high - low);
    }

    private static final class Cleave {
        private final ServerLevel level;
        private final ServerPlayer wielder;
        private final Item blade;
        private final Set<LivingEntity> struck = new LinkedHashSet<>();

        private Cleave(ServerLevel level, ServerPlayer wielder, Item blade) {
            this.level = level;
            this.wielder = wielder;
            this.blade = blade;
        }

        private boolean live() {
            return !this.wielder.isRemoved() && this.wielder.isAlive() && this.wielder.level() == this.level
                && this.wielder.getMainHandItem().is(this.blade);
        }

        private void hit(List<LivingEntity> victims, float damage) {
            DamageSource source = this.level.damageSources().playerAttack(this.wielder);
            RandomSource random = this.level.getRandom();
            for (LivingEntity victim : victims) {
                victim.invulnerableTime = 0;
                if (!victim.hurt(source, Bosses.sparedDamage(victim, damage))) {
                    continue;
                }
                this.struck.add(victim);
                Vec3 hit = victim.getBoundingBox().getCenter();
                boolean cleaves = victim instanceof ServerPlayer player ? Silence.mayLock(player) : !Bosses.is(victim);
                if (victim.isAlive() && cleaves) {
                    victim.addEffect(new MobEffectInstance(HexwrightEffects.SOUL_CLEAVED, CLEAVE_TICKS, 0,
                        false, false, true), this.wielder);
                    if (victim instanceof ServerPlayer player) {
                        Silence.noteLock(player, CLEAVE_TICKS);
                    }
                    this.level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.SOUL_ESCAPE,
                        SoundSource.PLAYERS, 1.0F, pitch(random, 0.8F, 1.0F));
                }
                HexwrightNetworking.sendSkyfallVfx(this.level, PiercingSkyfall.VFX_HIT, hit, 0.0F, 0);
                this.level.playSound(null, hit.x, hit.y, hit.z, StarfallSounds.HIT_THUD.get(),
                    SoundSource.PLAYERS, 0.45F, pitch(random, 0.8F, 0.9F));
            }
        }
    }
}
