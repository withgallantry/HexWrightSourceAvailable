package com.bluup.hexwright.server.malediction;

import com.bluup.hexwright.common.malediction.MaledictionFinish;
import com.bluup.hexwright.server.effect.HexwrightEffects;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class MaledictionStrike {

    private static final int CONCUSSION_TICKS = 15;
    private static final double CONCUSSIVE_KNOCKBACK = 1.2;
    private static final float CONCUSSION_SHARE = 0.5F;
    private static final int SEARING_SECONDS = 4;
    private static final int RIME_SLOW_TICKS = 60;
    private static final int RIME_MAX_AMPLIFIER = 2;
    private static final int RIME_FREEZE_TICKS = 80;
    private static final int RIME_MAX_FREEZE = 300;
    private static final int WITHERING_TICKS = 100;
    private static final float RADIANT_UNDEAD = 2.0F;
    private static final float RADIANT_LIVING = 0.5F;
    private static final float GALVANIC_WET = 1.5F;
    private static final float GALVANIC_DRY = 0.6F;
    private static final float REAPING_FRESH = 0.5F;
    private static final float REAPING_SPENT = 1.5F;
    private static final float LEECHING_SHARE = 0.5F;
    private static final float LEECHING_MAX = 4.0F;
    private static final int RESONANCE_TICKS = 60;
    private static final float RESONANCE_BONUS = 1.5F;

    private record Concussion(ServerLevel level, LivingEntity victim, UUID caster, float damage, long until) {
    }

    private static final List<Concussion> CONCUSSIONS = new ArrayList<>();

    private MaledictionStrike() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> tickConcussions());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CONCUSSIONS.clear());
    }

    public static boolean mayHarm(ServerPlayer caster, Entity target) {
        if (!(target instanceof LivingEntity living) || target == caster || !living.isAlive()
            || target instanceof ArmorStand || target.isSpectator()) {
            return false;
        }
        return !(target instanceof OwnableEntity owned) || !caster.getUUID().equals(owned.getOwnerUUID());
    }

    public static boolean strike(ServerPlayer caster, LivingEntity target, float damage, MaledictionFinish finish,
                                 @Nullable Entity direct, Vec3 from) {
        if (!mayHarm(caster, target) || !(target.level() instanceof ServerLevel level)) {
            return false;
        }
        float amount = damage * finish.damageScale() * preyScale(finish, target);
        if (target.hasEffect(HexwrightEffects.RESONANCE)) {
            amount *= RESONANCE_BONUS;
            target.removeEffect(HexwrightEffects.RESONANCE);
        }

        float before = target.getHealth() + target.getAbsorptionAmount();
        if (!target.hurt(MaledictionDamage.source(level, MaledictionDamage.typeOf(finish), caster, direct), amount)) {
            return false;
        }
        float lost = Math.max(0.0F, before - target.getHealth() - target.getAbsorptionAmount());
        finish(caster, target, finish, amount, from, lost);
        MaledictionFx.burst(level, target.getBoundingBox().getCenter(), finish, 8, target.getBbWidth() * 0.4);
        return true;
    }

    private static float preyScale(MaledictionFinish finish, LivingEntity target) {
        return switch (finish) {
            case RADIANT -> target.getMobType() == MobType.UNDEAD ? RADIANT_UNDEAD : RADIANT_LIVING;
            case GALVANIC -> target.isInWaterRainOrBubble() ? GALVANIC_WET : GALVANIC_DRY;
            case REAPING -> {
                float hurt = 1.0F - Mth.clamp(target.getHealth() / target.getMaxHealth(), 0.0F, 1.0F);
                yield Mth.lerp(hurt, REAPING_FRESH, REAPING_SPENT);
            }
            default -> 1.0F;
        };
    }

    private static void finish(ServerPlayer caster, LivingEntity target, MaledictionFinish finish, float amount,
                               Vec3 from, float lost) {
        switch (finish) {
            case SEARING -> {
                if (!target.fireImmune()) {
                    target.setSecondsOnFire(SEARING_SECONDS);
                }
            }
            case RIME -> {
                if (target.canFreeze()) {
                    MobEffectInstance slow = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    int amplifier = slow == null ? 0 : Math.min(RIME_MAX_AMPLIFIER, slow.getAmplifier() + 1);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, RIME_SLOW_TICKS, amplifier),
                        caster);
                    target.setTicksFrozen(Math.min(RIME_MAX_FREEZE, target.getTicksFrozen() + RIME_FREEZE_TICKS));
                }
            }
            case CONCUSSIVE -> {
                Vec3 toward = from.subtract(target.position());
                target.knockback(CONCUSSIVE_KNOCKBACK, toward.x, toward.z);
                target.hurtMarked = true;
                CONCUSSIONS.add(new Concussion((ServerLevel) target.level(), target, caster.getUUID(),
                    amount * CONCUSSION_SHARE, target.level().getGameTime() + CONCUSSION_TICKS));
            }
            case WITHERING -> {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHERING_TICKS, 0), caster);
                target.addEffect(new MobEffectInstance(HexwrightEffects.BLIGHTED, WITHERING_TICKS, 0), caster);
            }
            case LEECHING -> {
                if (lost > 0.0F) {
                    caster.heal(Math.min(LEECHING_MAX, lost * LEECHING_SHARE));
                }
            }
            case RESONANT -> target.addEffect(new MobEffectInstance(HexwrightEffects.RESONANCE, RESONANCE_TICKS, 0),
                caster);
            case BALEFUL, SUNDERING, RADIANT, GALVANIC, REAPING -> {
            }
        }
    }

    private static void tickConcussions() {
        for (Iterator<Concussion> it = CONCUSSIONS.iterator(); it.hasNext(); ) {
            Concussion concussion = it.next();
            LivingEntity victim = concussion.victim;
            if (!victim.isAlive() || victim.level() != concussion.level
                || concussion.level.getGameTime() > concussion.until) {
                it.remove();
                continue;
            }
            if (!victim.horizontalCollision) {
                continue;
            }
            it.remove();
            ServerPlayer caster = concussion.level.getServer().getPlayerList().getPlayer(concussion.caster);
            if (caster != null && mayHarm(caster, victim)) {
                victim.invulnerableTime = 0;
                victim.hurt(MaledictionDamage.source(concussion.level, MaledictionDamage.MALEDICTION, caster, null),
                    concussion.damage);
                MaledictionFx.burst(concussion.level, victim.getBoundingBox().getCenter(), MaledictionFinish.CONCUSSIVE, 12,
                    victim.getBbWidth() * 0.6);
            }
        }
    }
}
