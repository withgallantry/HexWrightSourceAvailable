package com.bluup.hexwright.server.weapon;

import at.petrak.hexcasting.api.mod.HexConfig;
import at.petrak.hexcasting.common.casting.actions.spells.great.OpTeleport;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.boss.Bosses;
import com.bluup.hexwright.server.effect.HexwrightEffects;
import com.bluup.hexwright.server.worldgen.TeleportWards;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public final class TeleportStrike {

    public static final int VFX_TELEPORT = 0;
    public static final int VFX_EXPLOSION = 1;

    public static final double REACH = 10.0D;

    private static final double FRONT_COS = Math.cos(Math.toRadians(45.0D));

    private static final double ARRIVAL_GAP = 0.3D;

    private static final double ARRIVAL_STEP = 0.5D;

    private static final double BLAST_RADIUS = 5.0D;
    private static final int BLAST_LIMIT = 7;
    private static final float BLAST_DAMAGE = 4.0F;

    public static final int STUN_TICKS = 40;

    private TeleportStrike() {
    }

    public static boolean start(ServerLevel level, ServerPlayer wielder, @Nullable CompoundTag hex) {
        LivingEntity target = target(level, wielder);
        if (target == null) {
            wielder.displayClientMessage(Component.translatable("message.hexwright.teleport_strike.no_target"), true);
            return false;
        }

        Vec3 from = wielder.position();
        Vec3 arrival = arrival(level, wielder, target);
        boolean moves = arrival != null && !arrival.equals(from);
        if (arrival == null || (moves && !HexConfig.server().canTeleportInThisDimension(level.dimension()))) {
            wielder.displayClientMessage(Component.translatable("message.hexwright.teleport_strike.blocked"), true);
            return false;
        }
        TeleportWards.Check ward = moves ? TeleportWards.firstRefusing(level, from, arrival) : null;
        if (ward != null) {
            ward.notifyRefused(wielder);
            return false;
        }

        float yaw = wielder.getYRot();
        HexwrightNetworking.sendTeleportStrikeVfx(level, VFX_TELEPORT, from, yaw);
        if (moves) {
            OpTeleport.INSTANCE.teleportRespectSticky(wielder, arrival.subtract(from), level);
            wielder.resetFallDistance();
            HexwrightNetworking.sendTeleportStrikeVfx(level, VFX_TELEPORT, wielder.position(), yaw);
        }
        Vec3 at = wielder.position();
        level.playSound(null, at.x, at.y, at.z, KingOfPurgatorySounds.TELEPORT.get(),
            SoundSource.PLAYERS, 1.0F, pitch(level.getRandom(), 0.9F, 1.1F));

        strike(level, wielder, hex);
        return true;
    }

    private static @Nullable LivingEntity target(ServerLevel level, ServerPlayer wielder) {
        Vec3 eye = wielder.getEyePosition();
        Vec3 heading = Vec3.directionFromRotation(0.0F, wielder.getYRot());
        double reachSq = REACH * REACH;
        List<LivingEntity> ahead = level.getEntitiesOfClass(LivingEntity.class,
            wielder.getBoundingBox().inflate(REACH),
            candidate -> strikeable(wielder, candidate)
                && candidate.getBoundingBox().distanceToSqr(eye) <= reachSq
                && inFront(wielder, heading, candidate));
        ahead.sort(Comparator.comparingDouble(candidate -> candidate.getBoundingBox().distanceToSqr(eye)));
        for (LivingEntity candidate : ahead) {
            if (wielder.hasLineOfSight(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean inFront(ServerPlayer wielder, Vec3 heading, LivingEntity candidate) {
        double dx = candidate.getX() - wielder.getX();
        double dz = candidate.getZ() - wielder.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        return length < 1.0E-4D || (dx * heading.x + dz * heading.z) / length >= FRONT_COS;
    }

    private static @Nullable Vec3 arrival(ServerLevel level, ServerPlayer wielder, LivingEntity target) {
        Vec3 from = wielder.position();
        Vec3 back = from.subtract(target.position());
        double length = back.length();
        double gap = (target.getBbWidth() + wielder.getBbWidth()) / 2.0D + ARRIVAL_GAP;
        if (length <= gap) {
            return from;
        }
        for (double distance = gap; distance < Math.min(length, BLAST_RADIUS); distance += ARRIVAL_STEP) {
            Vec3 spot = target.position().add(back.scale(distance / length));
            if (level.noCollision(wielder, wielder.getBoundingBox().move(spot.subtract(from)))) {
                return spot;
            }
        }
        return null;
    }

    private static void strike(ServerLevel level, ServerPlayer wielder, @Nullable CompoundTag hex) {
        if (wielder.isRemoved() || !wielder.isAlive() || wielder.level() != level) {
            return;
        }
        Vec3 at = wielder.position();
        RandomSource random = level.getRandom();
        HexwrightNetworking.sendTeleportStrikeVfx(level, VFX_EXPLOSION, at, wielder.getYRot());
        level.playSound(null, at.x, at.y, at.z, KingOfPurgatorySounds.STRIKE.get(),
            SoundSource.PLAYERS, 0.7F, pitch(random, 0.6F, 0.8F));

        Vec3 body = wielder.getBoundingBox().getCenter();
        double radiusSq = BLAST_RADIUS * BLAST_RADIUS;
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class,
            wielder.getBoundingBox().inflate(BLAST_RADIUS),
            victim -> strikeable(wielder, victim) && victim.getBoundingBox().distanceToSqr(body) <= radiusSq);
        caught.sort(Comparator.comparingDouble(victim -> victim.getBoundingBox().distanceToSqr(body)));
        if (caught.size() > BLAST_LIMIT) {
            caught.subList(BLAST_LIMIT, caught.size()).clear();
        }

        DamageSource source = level.damageSources().playerAttack(wielder);
        for (LivingEntity victim : caught) {
            Vec3 motion = victim.getDeltaMovement();
            victim.hurt(source, BLAST_DAMAGE);
            victim.setDeltaMovement(motion);
            if (victim.isAlive() && !Bosses.is(victim)) {
                victim.addEffect(new MobEffectInstance(HexwrightEffects.STUNNED, STUN_TICKS, 0, false, false, true),
                    wielder);
            }

            Vec3 hit = victim.getBoundingBox().getCenter();
            HexwrightNetworking.sendSkyfallVfx(level, PiercingSkyfall.VFX_HIT, hit, 0.0F, 0);
            level.playSound(null, hit.x, hit.y, hit.z, StarfallSounds.HIT_THUD.get(),
                SoundSource.PLAYERS, 0.45F, pitch(random, 1.1F, 1.2F));
            if (hex != null) {
                WeaponHexCasting.castOnTarget(wielder, hex, victim, victim.position());
            }
        }
    }

    static boolean strikeable(ServerPlayer wielder, LivingEntity candidate) {
        return candidate != wielder && candidate.isAlive() && !candidate.isSpectator()
            && !(candidate instanceof ArmorStand)
            && candidate.getRootVehicle() != wielder.getRootVehicle()
            && !candidate.isAlliedTo(wielder);
    }

    private static float pitch(RandomSource random, float low, float high) {
        return low + random.nextFloat() * (high - low);
    }
}
