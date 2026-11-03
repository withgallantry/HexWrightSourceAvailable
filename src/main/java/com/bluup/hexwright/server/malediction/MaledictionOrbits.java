package com.bluup.hexwright.server.malediction;

import com.bluup.hexwright.common.malediction.MaledictionFinish;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class MaledictionOrbits {

    public static final int MOTES = 3;
    private static final int LIFE_TICKS = 200;
    private static final double RADIUS = 1.4;
    private static final double REACH = 3.0;
    private static final int STRIKE_INTERVAL = 10;
    private static final int DRAW_INTERVAL = 2;
    private static final double SPIN = 0.25;

    private static final class Orbit {
        final ResourceKey<Level> dimension;
        final @Nullable UUID anchor;
        final Vec3 point;
        final MaledictionFinish finish;
        final float damage;
        final long expiresAt;
        int motes = MOTES;
        long nextStrike;

        Orbit(ResourceKey<Level> dimension, @Nullable UUID anchor, Vec3 point, MaledictionFinish finish, float damage,
              long now) {
            this.dimension = dimension;
            this.anchor = anchor;
            this.point = point;
            this.finish = finish;
            this.damage = damage;
            this.expiresAt = now + LIFE_TICKS;
            this.nextStrike = now;
        }
    }

    private static final Map<UUID, Orbit> ORBITS = new HashMap<>();

    private MaledictionOrbits() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(MaledictionOrbits::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ORBITS.clear());
    }

    public static void orbit(ServerLevel level, ServerPlayer caster, @Nullable Entity anchor, Vec3 point,
                             MaledictionFinish finish, float damage) {
        long now = level.getServer().overworld().getGameTime();
        ORBITS.put(caster.getUUID(), new Orbit(level.dimension(), anchor == null ? null : anchor.getUUID(), point,
            finish, damage, now));
        MaledictionFx.sound(level, point, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.4F);
    }

    private static void tick(MinecraftServer server) {
        if (ORBITS.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        for (Iterator<Map.Entry<UUID, Orbit>> it = ORBITS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Orbit> entry = it.next();
            Orbit orbit = entry.getValue();
            ServerLevel level = server.getLevel(orbit.dimension);
            ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
            Entity anchor = level == null || orbit.anchor == null ? null : level.getEntity(orbit.anchor);
            if (level == null || caster == null || now >= orbit.expiresAt || orbit.motes <= 0
                || orbit.anchor != null && (anchor == null || !anchor.isAlive())) {
                it.remove();
                continue;
            }
            Vec3 center = anchor == null ? orbit.point : anchor.position().add(0.0, anchor.getBbHeight() * 0.6, 0.0);
            if (now % DRAW_INTERVAL == 0) {
                for (int i = 0; i < orbit.motes; i++) {
                    Vec3 mote = mote(center, now, i, orbit.motes);
                    level.sendParticles(MaledictionFx.dust(orbit.finish, 1.0F), mote.x, mote.y, mote.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            if (now < orbit.nextStrike) {
                continue;
            }
            LivingEntity target = nearestHostile(level, caster, anchor, center);
            if (target != null) {
                Vec3 from = mote(center, now, 0, orbit.motes);
                MaledictionFx.line(level, from, target.getBoundingBox().getCenter(), orbit.finish);
                MaledictionStrike.strike(caster, target, orbit.damage, orbit.finish, null, from);
                orbit.motes--;
                orbit.nextStrike = now + STRIKE_INTERVAL;
            }
        }
    }

    private static Vec3 mote(Vec3 center, long now, int index, int count) {
        double angle = now * SPIN + index * Math.PI * 2.0 / count;
        return center.add(Math.cos(angle) * RADIUS, 0.0, Math.sin(angle) * RADIUS);
    }

    private static @Nullable LivingEntity nearestHostile(ServerLevel level, ServerPlayer caster, @Nullable Entity anchor,
                                                         Vec3 center) {
        LivingEntity nearest = null;
        double best = REACH * REACH;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(REACH))) {
            if (candidate == anchor || !MaledictionStrike.mayHarm(caster, candidate)) {
                continue;
            }
            boolean hostile = candidate instanceof Enemy
                || candidate instanceof Mob mob && (mob.getTarget() == caster || anchor != null && mob.getTarget() == anchor);
            double distance = candidate.getBoundingBox().getCenter().distanceToSqr(center);
            if (hostile && distance <= best) {
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }
}
