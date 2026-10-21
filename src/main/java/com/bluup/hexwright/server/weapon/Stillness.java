package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.inits.HexwrightNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class Stillness {

    public static final int VFX_SPEAR_GATHER = 0;
    public static final int VFX_SPEAR_FALL = 1;
    public static final int VFX_SPEAR_PLANTED = 2;
    public static final int VFX_IMPACT = 3;
    public static final int VFX_ZONE = 4;

    public static final int SPEARS = 3;
    public static final double RADIUS = 8.0D;
    public static final double SPEAR_HEIGHT = 8.0D;

    public static final int FALL_TICKS = 13;
    public static final int LAND_TICKS = 16;
    public static final int ZONE_TICKS = 300;

    private static final double ZONE_CEILING = 4.0D;
    private static final double ZONE_FLOOR = 1.0D;
    private static final int CHECK_INTERVAL_TICKS = 5;
    private static final int AURA_INTERVAL_TICKS = 40;

    public static final int SPENT_TICKS = 160;

    private static final List<Zone> ZONES = new ArrayList<>();

    private Stillness() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Stillness::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ZONES.clear());
    }

    public static void start(ServerLevel level, ServerPlayer wielder, Item blade) {
        Vec3 centre = Hailpiercer.onSurface(level, wielder.position());
        int startTick = level.getServer().getTickCount();
        Vec3[] spears = new Vec3[SPEARS];
        float[] facings = new float[SPEARS];
        for (int i = 0; i < SPEARS; i++) {
            float bearing = wielder.getYRot() + i * 360.0F / SPEARS;
            Vec3 rim = centre.add(Vec3.directionFromRotation(0.0F, bearing).scale(RADIUS));
            spears[i] = Hailpiercer.onSurface(level, rim);
            facings[i] = bearing + 90.0F;
            HexwrightNetworking.sendStillnessVfx(level, VFX_SPEAR_GATHER, spears[i], facings[i]);
            level.playSound(null, spears[i].x, spears[i].y + SPEAR_HEIGHT, spears[i].z, StillnessSounds.SUMMON.get(),
                SoundSource.PLAYERS, 1.0F, pitch(level.getRandom(), 0.95F, 1.05F));
        }

        SlamWindUp.schedule(level.getServer(), FALL_TICKS, () -> {
            for (int i = 0; i < SPEARS; i++) {
                HexwrightNetworking.sendStillnessVfx(level, VFX_SPEAR_FALL, spears[i], facings[i]);
            }
        });
        SlamWindUp.schedule(level.getServer(), LAND_TICKS - 2, () -> {
            for (Vec3 spear : spears) {
                level.playSound(null, spear.x, spear.y, spear.z, StillnessSounds.SMASH.get(), SoundSource.PLAYERS,
                    1.0F, pitch(level.getRandom(), 0.9F, 1.1F));
            }
        });
        SlamWindUp.schedule(level.getServer(), LAND_TICKS, () -> land(level, wielder, blade, centre, spears,
            facings, startTick));
        HexwrightDebug.log(HexwrightDebug.WEAPON, "[weapon] stillness from {} at {}", wielder.getScoreboardName(),
            centre);
    }

    private static void land(ServerLevel level, ServerPlayer wielder, Item blade, Vec3 centre, Vec3[] spears,
                             float[] facings, int startTick) {
        for (int i = 0; i < SPEARS; i++) {
            HexwrightNetworking.sendStillnessVfx(level, VFX_SPEAR_PLANTED, spears[i], facings[i]);
            HexwrightNetworking.sendStillnessVfx(level, VFX_IMPACT, spears[i], facings[i]);
        }
        HexwrightNetworking.sendStillnessVfx(level, VFX_ZONE, centre, 0.0F);
        if (!wielder.isRemoved() && wielder.isAlive()) {
            wielder.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SPENT_TICKS, 1));
            wielder.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SPENT_TICKS, 1));
        }
        ZONES.add(new Zone(level, wielder, blade, centre, startTick, level.getServer().getTickCount() + ZONE_TICKS));
    }

    private static void tick(MinecraftServer server) {
        if (ZONES.isEmpty()) {
            return;
        }
        int now = server.getTickCount();
        Iterator<Zone> zones = ZONES.iterator();
        while (zones.hasNext()) {
            Zone zone = zones.next();
            if (now >= zone.endTick || server.getLevel(zone.level.dimension()) != zone.level) {
                zones.remove();
                HexwrightDebug.log(HexwrightDebug.WEAPON, "[weapon] stillness from {} gone, {} silenced",
                    zone.wielder.getScoreboardName(), zone.silenced.size());
                continue;
            }
            int age = now - (zone.endTick - ZONE_TICKS);
            if (age % AURA_INTERVAL_TICKS == 0) {
                zone.level.playSound(null, zone.centre.x, zone.centre.y, zone.centre.z, StillnessSounds.AURA.get(),
                    SoundSource.PLAYERS, 1.2F, 1.0F);
            }
            if (age % CHECK_INTERVAL_TICKS == 0) {
                zone.sweep();
            }
        }
    }

    private static float pitch(RandomSource random, float low, float high) {
        return low + random.nextFloat() * (high - low);
    }

    private static final class Zone {
        private final ServerLevel level;
        private final ServerPlayer wielder;
        private final Item blade;
        private final Vec3 centre;
        private final int startTick;
        private final int endTick;
        private final Set<UUID> silenced = new HashSet<>();

        private Zone(ServerLevel level, ServerPlayer wielder, Item blade, Vec3 centre, int startTick, int endTick) {
            this.level = level;
            this.wielder = wielder;
            this.blade = blade;
            this.centre = centre;
            this.startTick = startTick;
            this.endTick = endTick;
        }

        private ServerPlayer wielder() {
            ServerPlayer current = this.level.getServer().getPlayerList().getPlayer(this.wielder.getUUID());
            return current != null ? current : this.wielder;
        }

        private void sweep() {
            ServerPlayer wielder = wielder();
            double radiusSq = RADIUS * RADIUS;
            List<LivingEntity> inside = this.level.getEntitiesOfClass(LivingEntity.class,
                new AABB(this.centre.x - RADIUS, this.centre.y - ZONE_FLOOR, this.centre.z - RADIUS,
                    this.centre.x + RADIUS, this.centre.y + ZONE_CEILING, this.centre.z + RADIUS),
                candidate -> TeleportStrike.strikeable(wielder, candidate)
                    && !(candidate instanceof ServerPlayer player && !mayTouch(wielder, player))
                    && horizontalDistanceSq(candidate.position(), this.centre) <= radiusSq
                    && !this.silenced.contains(candidate.getUUID()));
            for (LivingEntity victim : inside) {
                if (!Silence.strike(victim, wielder)) {
                    continue;
                }
                if (this.silenced.isEmpty()) {
                    landed(wielder);
                }
                this.silenced.add(victim.getUUID());
            }
        }

        private static boolean mayTouch(ServerPlayer wielder, ServerPlayer player) {
            return !player.getAbilities().invulnerable && wielder.canHarmPlayer(player);
        }

        private void landed(ServerPlayer wielder) {
            int elapsed = this.level.getServer().getTickCount() - this.startTick;
            wielder.getCooldowns().addCooldown(this.blade,
                Math.max(1, AbsolutionItem.LANDED_COOLDOWN_TICKS - elapsed));
        }

        private static double horizontalDistanceSq(Vec3 a, Vec3 b) {
            double dx = a.x - b.x;
            double dz = a.z - b.z;
            return dx * dx + dz * dz;
        }
    }
}
