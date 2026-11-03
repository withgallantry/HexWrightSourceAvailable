package com.bluup.hexwright.server.portal;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.sound.HexwrightSoundEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VoidTearManager {

    public static final ResourceKey<DamageType> VOID_TEAR_DAMAGE =
        ResourceKey.create(Registries.DAMAGE_TYPE, Hexwright.id("void_tear"));

    private static final double SPEED_THRESHOLD = 0.10;

    private static final double DAMAGE_PER_SPEED = 30.0;

    private static final float DAMAGE_CAP = 8.0f;

    private static final int CUT_REFRACTORY_TICKS = 10;

    public static final int MAX_TEARS_PER_CASTER = 4;

    private static final double MAX_CROSSING_STEP = 8.0;

    private static final double MAX_TILT = Math.toRadians(25.0);

    private static final double TEAR_LENGTH = 2.0;

    private static final double APERTURE_RATIO = 0.28;

    private static final Map<ResourceKey<Level>, VoidTearManager> MANAGERS = new ConcurrentHashMap<>();

    private final List<VoidTear> tears = new ArrayList<>();

    private final Map<Integer, Long> lastCut = new HashMap<>();

    private VoidTearManager() {
    }

    public static VoidTearManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level.dimension(), key -> new VoidTearManager());
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(level -> get(level).tick(level));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> MANAGERS.clear());
    }

    public int countFor(UUID caster) {
        int count = 0;
        for (VoidTear tear : tears) {
            if (tear.caster().equals(caster)) {
                count++;
            }
        }
        return count;
    }

    public static double tearLength() {
        return TEAR_LENGTH;
    }

    public VoidTear open(ServerLevel level, Entity caster, Vec3 center) {
        RandomSource random = level.random;
        long seed = random.nextLong();

        Vec3 towardCaster = caster.getEyePosition().subtract(center);
        Vec3[] basis = orient(random, towardCaster);

        Vec3 u = basis[0].scale(TEAR_LENGTH * 0.5);
        Vec3 v = basis[1].scale(TEAR_LENGTH * APERTURE_RATIO * 0.5);

        VoidTear tear = new VoidTear(UUID.randomUUID(), caster.getUUID(), center, u, v, seed,
            level.getGameTime());
        tears.add(tear);
        HexwrightNetworking.broadcastVoidTear(level, tear);
        level.playSound(null, center.x, center.y, center.z,
            HexwrightSoundEvents.voidTearOpen(), SoundSource.PLAYERS, 0.9f,
            1.0f + (random.nextFloat() - 0.5f) * 0.2f);
        return tear;
    }

    private static Vec3[] orient(RandomSource random, Vec3 towardCaster) {
        Vec3 normal = towardCaster.lengthSqr() > 1.0e-6
            ? towardCaster.normalize()
            : new Vec3(0.0, 1.0, 0.0);

        Vec3 p = perpendicular(normal);
        Vec3 q = normal.cross(p).normalize();

        double tilt = random.nextDouble() * MAX_TILT;
        double tiltPhase = random.nextDouble() * Math.PI * 2.0;
        Vec3 lean = p.scale(Math.cos(tiltPhase)).add(q.scale(Math.sin(tiltPhase)));
        normal = normal.scale(Math.cos(tilt)).add(lean.scale(Math.sin(tilt))).normalize();

        Vec3 basisA = perpendicular(normal);
        Vec3 basisB = normal.cross(basisA).normalize();
        double roll = random.nextDouble() * Math.PI * 2.0;
        Vec3 uHat = basisA.scale(Math.cos(roll)).add(basisB.scale(Math.sin(roll))).normalize();
        Vec3 vHat = normal.cross(uHat).normalize();
        return new Vec3[]{uHat, vHat};
    }

    private static Vec3 perpendicular(Vec3 axis) {
        Vec3 seed = Math.abs(axis.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        return axis.cross(seed).normalize();
    }


    private void tick(ServerLevel level) {
        if (tears.isEmpty()) {
            if (!lastCut.isEmpty()) {
                lastCut.clear();
            }
            return;
        }
        long gameTime = level.getGameTime();
        lastCut.entrySet().removeIf(entry -> gameTime - entry.getValue() > CUT_REFRACTORY_TICKS);

        Iterator<VoidTear> iterator = tears.iterator();
        while (iterator.hasNext()) {
            VoidTear tear = iterator.next();
            if (gameTime - tear.openedAt() >= VoidTear.LIFETIME_TICKS) {
                iterator.remove();
                continue;
            }
            cutCrossings(level, tear, gameTime);
        }
    }

    private void cutCrossings(ServerLevel level, VoidTear tear, long gameTime) {
        for (Entity entity : level.getEntities(null, tear.gatherBox(MAX_CROSSING_STEP))) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }
            Long last = lastCut.get(living.getId());
            if (last != null && gameTime - last < CUT_REFRACTORY_TICKS) {
                continue;
            }
            float damage = crossingDamage(tear, living);
            if (damage <= 0.0f) {
                continue;
            }
            living.hurt(source(level, tear), damage);
            lastCut.put(living.getId(), gameTime);
            Vec3 at = living.position().add(0.0, living.getBbHeight() * 0.5, 0.0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK,
                SoundSource.PLAYERS, 0.8f, 1.4f + level.random.nextFloat() * 0.3f);
        }
    }

    private static float crossingDamage(VoidTear tear, LivingEntity entity) {
        double lift = entity.getBbHeight() * 0.5;
        Vec3 previous = new Vec3(entity.xOld, entity.yOld + lift, entity.zOld);
        Vec3 now = entity.position().add(0.0, lift, 0.0);
        Vec3 step = now.subtract(previous);
        if (step.lengthSqr() > MAX_CROSSING_STEP * MAX_CROSSING_STEP) {
            return 0.0f;
        }

        double before = tear.signedDistance(previous);
        double after = tear.signedDistance(now);
        if (before == after) {
            return 0.0f;
        }
        if ((before > 0.0 && after > 0.0) || (before < 0.0 && after < 0.0)) {
            return 0.0f;
        }
        double t = before / (before - after);
        Vec3 hit = previous.add(step.scale(t));
        if (!tear.containsProjected(hit, entity.getBbWidth() * 0.5)) {
            return 0.0f;
        }

        double normalSpeed = Math.abs(before - after);
        double damage = (normalSpeed - SPEED_THRESHOLD) * DAMAGE_PER_SPEED;
        return (float) Mth.clamp(damage, 0.0, DAMAGE_CAP);
    }

    private static DamageSource source(ServerLevel level, VoidTear tear) {
        var holder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(VOID_TEAR_DAMAGE);
        return new DamageSource(holder, null, casterOf(level, tear));
    }

    private static @Nullable Entity casterOf(ServerLevel level, VoidTear tear) {
        return level.getEntity(tear.caster());
    }
}
