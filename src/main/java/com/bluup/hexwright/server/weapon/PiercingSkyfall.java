package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.inits.HexwrightNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PiercingSkyfall {

    public static final int VFX_LOOSE = 0;
    public static final int VFX_RAIN = 1;
    public static final int VFX_HIT = 2;

    public static final int RAIN_VARIANTS = 10;

    public static final int LOFT_TICKS = 20;

    private static final int FALL_TICKS = 3;

    private static final int RANK_TICKS = 1;

    private static final int RANKS = 16;

    private static final double RANK_SPACING = 1.75D;

    private static final double FIRST_RANK = 3.0D;

    public static final double REACH = FIRST_RANK + (RANKS - 1) * RANK_SPACING;

    private static final double RANK_RADIUS = 3.0D;

    private static final int ARROWS_PER_RANK = 2;
    private static final double ARROW_SCATTER = 2.5D;

    private static final double CATCH_ABOVE = 4.0D;

    private static final double CATCH_BELOW = 1.5D;

    private static final float DAMAGE = 6.0F;

    private static final int RANK_LIMIT = 10;

    private static final double GROUND_SCAN_UP = 3.0D;
    private static final double GROUND_SCAN_DOWN = 8.0D;

    private static final int LAUNCH_ARROWS = 6;
    private static final int LAUNCH_INTERVAL_TICKS = 2;

    private PiercingSkyfall() {
    }

    public static void fire(ServerLevel level, ServerPlayer archer, @Nullable CompoundTag hex) {
        Vec3 origin = archer.position();
        float yaw = archer.getYRot();
        float yawRad = yaw * Mth.DEG_TO_RAD;
        Vec3 heading = new Vec3(-Mth.sin(yawRad), 0.0D, Mth.cos(yawRad));

        loose(level, archer, yaw);

        Set<Integer> struck = new HashSet<>();
        for (int rank = 0; rank < RANKS; rank++) {
            double distance = FIRST_RANK + rank * RANK_SPACING;
            Vec3 along = origin.add(heading.scale(distance));
            int landsAt = LOFT_TICKS + rank * RANK_TICKS;
            Vec3[] centre = new Vec3[1];
            SlamWindUp.schedule(level.getServer(), landsAt - FALL_TICKS, () -> {
                centre[0] = onGround(level, archer, along);
                rain(level, centre[0], yaw);
            });
            SlamWindUp.schedule(level.getServer(), landsAt,
                () -> land(level, archer, hex, centre[0] != null ? centre[0] : along, struck));
        }
    }

    private static void loose(ServerLevel level, ServerPlayer archer, float yaw) {
        HexwrightNetworking.sendSkyfallVfx(level, VFX_LOOSE, archer.position(), yaw, 0);
        for (int shot = 0; shot < LAUNCH_ARROWS; shot++) {
            SlamWindUp.schedule(level.getServer(), shot * LAUNCH_INTERVAL_TICKS, () -> {
                if (archer.isRemoved() || archer.level() != level) {
                    return;
                }
                Vec3 at = archer.position();
                level.playSound(null, at.x, at.y, at.z, StarfallSounds.ARROW_SHOOT.get(),
                    SoundSource.PLAYERS, 0.7F, pitch(level.getRandom(), 0.9F, 1.1F));
            });
        }
    }

    private static void rain(ServerLevel level, Vec3 centre, float yaw) {
        RandomSource random = level.getRandom();
        for (int arrow = 0; arrow < ARROWS_PER_RANK; arrow++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = Math.sqrt(random.nextDouble()) * ARROW_SCATTER;
            Vec3 at = centre.add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
            HexwrightNetworking.sendSkyfallVfx(level, VFX_RAIN, at, yaw, random.nextInt(RAIN_VARIANTS));
        }
        level.playSound(null, centre.x, centre.y, centre.z, StarfallSounds.ARROW_WHOOSH.get(),
            SoundSource.PLAYERS, 0.7F, pitch(random, 0.9F, 1.1F));
        SlamWindUp.schedule(level.getServer(), FALL_TICKS + 1, () -> level.playSound(null,
            centre.x, centre.y, centre.z, StarfallSounds.ARROW_GROUND.get(),
            SoundSource.PLAYERS, 0.4F, pitch(level.getRandom(), 0.8F, 1.1F)));
    }

    private static void land(ServerLevel level, ServerPlayer archer, @Nullable CompoundTag hex,
                             Vec3 centre, Set<Integer> struck) {
        AABB caught = new AABB(
            centre.x - RANK_RADIUS, centre.y - CATCH_BELOW, centre.z - RANK_RADIUS,
            centre.x + RANK_RADIUS, centre.y + CATCH_ABOVE, centre.z + RANK_RADIUS);
        List<LivingEntity> victims = new ArrayList<>(
            level.getEntitiesOfClass(LivingEntity.class, caught,
                victim -> victim.isAlive() && victim != archer && !victim.isSpectator()
                    && !struck.contains(victim.getId())));
        if (victims.isEmpty()) {
            return;
        }
        if (victims.size() > RANK_LIMIT) {
            victims.subList(RANK_LIMIT, victims.size()).clear();
        }

        DamageSource source = level.damageSources().playerAttack(archer);
        boolean casts = hex != null && archer.isAlive() && !archer.isRemoved()
            && archer.level() == level;
        for (LivingEntity victim : victims) {
            struck.add(victim.getId());
            victim.hurt(source, DAMAGE);
            Vec3 body = victim.getBoundingBox().getCenter();
            HexwrightNetworking.sendSkyfallVfx(level, VFX_HIT, body, 0.0F, 0);
            level.playSound(null, body.x, body.y, body.z, StarfallSounds.HIT_THUD.get(),
                SoundSource.PLAYERS, 0.7F, pitch(level.getRandom(), 1.1F, 1.2F));
            if (casts) {
                WeaponHexCasting.castOnTarget(archer, hex, victim, victim.position());
            }
        }
    }

    private static Vec3 onGround(ServerLevel level, ServerPlayer archer, Vec3 along) {
        Vec3 from = new Vec3(along.x, along.y + GROUND_SCAN_UP, along.z);
        Vec3 to = new Vec3(along.x, along.y - GROUND_SCAN_DOWN, along.z);
        HitResult floor = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, archer));
        return floor.getType() == HitResult.Type.MISS ? along
            : new Vec3(along.x, floor.getLocation().y, along.z);
    }

    private static float pitch(RandomSource random, float low, float high) {
        return low + random.nextFloat() * (high - low);
    }
}
