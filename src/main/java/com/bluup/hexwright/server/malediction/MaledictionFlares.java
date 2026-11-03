package com.bluup.hexwright.server.malediction;

import com.bluup.hexwright.common.malediction.MaledictionFinish;
import com.bluup.hexwright.server.region.Region;
import com.bluup.hexwright.server.region.RegionDistance;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class MaledictionFlares {

    private static final double FULL_VOLUME = 4.0 / 3.0 * Math.PI * 8.0;
    private static final int MIN_WARNING_TICKS = 10;
    private static final int MAX_WARNING_TICKS = 30;
    private static final double WARNING_PER_BLOCK = 0.1;
    private static final int WARNING_LINGER_TICKS = 20;

    public static final int MAX_SNARES = 2;
    private static final int SNARE_ARMING_TICKS = 20;
    private static final int SNARE_LIFE_TICKS = 600;
    private static final int SNARE_GLINT_INTERVAL = 20;

    private record Flare(ResourceKey<Level> dimension, Region region, MaledictionFinish finish, float damage,
                         UUID caster, int warning, long landAt, int pulses, int interval) {
    }

    private record Snare(ResourceKey<Level> dimension, Region region, MaledictionFinish finish, float damage,
                         UUID caster, int warning, long armedAt, long expiresAt) {
    }

    private static final List<Flare> FLARES = new ArrayList<>();
    private static final List<Snare> SNARES = new ArrayList<>();

    private MaledictionFlares() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(MaledictionFlares::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            FLARES.clear();
            SNARES.clear();
        });
    }

    public static float density(Region region) {
        double volume = RegionDistance.measure(region).volume();
        return volume <= FULL_VOLUME ? 1.0F : (float) Math.sqrt(FULL_VOLUME / volume);
    }

    private static int warningTicks(Region region) {
        double volume = RegionDistance.measure(region).volume();
        return (int) Math.min(MAX_WARNING_TICKS, MIN_WARNING_TICKS + volume * WARNING_PER_BLOCK);
    }

    public static void flare(ServerLevel level, ServerPlayer caster, Region region, MaledictionFinish finish,
                             float damage, int pulses, int interval) {
        int warningTicks = warningTicks(region);
        int warning = MaledictionFx.warn(level, region, finish, warningTicks,
            warningTicks + (pulses - 1) * interval + WARNING_LINGER_TICKS);
        MaledictionFx.sound(level, center(region), SoundEvents.EVOKER_PREPARE_ATTACK, 0.6F, 1.6F);
        long now = level.getServer().overworld().getGameTime();
        FLARES.add(new Flare(level.dimension(), region, finish, damage, caster.getUUID(), warning, now + warningTicks,
            pulses, interval));
    }

    public static void snare(ServerLevel level, ServerPlayer caster, Region region, MaledictionFinish finish, float damage) {
        long now = level.getServer().overworld().getGameTime();
        List<Snare> own = new ArrayList<>();
        for (Snare snare : SNARES) {
            if (snare.caster.equals(caster.getUUID())) {
                own.add(snare);
            }
        }
        for (int i = 0; i <= own.size() - MAX_SNARES; i++) {
            Snare replaced = own.get(i);
            SNARES.remove(replaced);
            ServerLevel replacedLevel = level.getServer().getLevel(replaced.dimension);
            if (replacedLevel != null) {
                MaledictionFx.landed(replacedLevel, replaced.region, replaced.warning, false, 0);
            }
        }
        int warning = MaledictionFx.warn(level, region, finish, SNARE_ARMING_TICKS, SNARE_LIFE_TICKS);
        SNARES.add(new Snare(level.dimension(), region, finish, damage, caster.getUUID(), warning,
            now + SNARE_ARMING_TICKS, now + SNARE_LIFE_TICKS));
        MaledictionFx.sound(level, center(region), SoundEvents.TRIPWIRE_ATTACH, 0.8F, 0.7F);
    }

    private static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (!FLARES.isEmpty()) {
            List<Flare> again = new ArrayList<>();
            for (Iterator<Flare> it = FLARES.iterator(); it.hasNext(); ) {
                Flare flare = it.next();
                if (now < flare.landAt) {
                    continue;
                }
                it.remove();
                ServerLevel level = server.getLevel(flare.dimension);
                ServerPlayer caster = server.getPlayerList().getPlayer(flare.caster);
                if (level == null) {
                    continue;
                }
                if (caster == null) {
                    MaledictionFx.landed(level, flare.region, flare.warning, false, 0);
                    continue;
                }
                land(level, caster, flare.region, flare.finish, flare.damage);
                boolean more = flare.pulses > 1;
                MaledictionFx.landed(level, flare.region, flare.warning, true, more ? flare.interval : 0);
                if (more) {
                    again.add(new Flare(flare.dimension, flare.region, flare.finish, flare.damage, flare.caster,
                        flare.warning, now + flare.interval, flare.pulses - 1, flare.interval));
                }
            }
            FLARES.addAll(again);
        }
        for (Iterator<Snare> it = SNARES.iterator(); it.hasNext(); ) {
            Snare snare = it.next();
            ServerLevel level = server.getLevel(snare.dimension);
            ServerPlayer caster = server.getPlayerList().getPlayer(snare.caster);
            if (level == null || caster == null || now >= snare.expiresAt) {
                it.remove();
                if (level != null && caster == null) {
                    MaledictionFx.landed(level, snare.region, snare.warning, false, 0);
                }
                continue;
            }
            if (now < snare.armedAt) {
                continue;
            }
            if (!caught(level, caster, snare.region).isEmpty()) {
                it.remove();
                land(level, caster, snare.region, snare.finish, snare.damage);
                MaledictionFx.landed(level, snare.region, snare.warning, true, 0);
            } else if ((now - snare.armedAt) % SNARE_GLINT_INTERVAL == 0) {
                Vec3 glint = center(snare.region);
                MaledictionFx.burst(level, glint, snare.finish, 2, 0.3);
            }
        }
    }

    private static void land(ServerLevel level, ServerPlayer caster, Region region, MaledictionFinish finish, float damage) {
        Vec3 center = center(region);
        MaledictionFx.fill(level, region, finish);
        MaledictionFx.sound(level, center, SoundEvents.EVOKER_CAST_SPELL, 0.9F, 1.3F);
        for (LivingEntity target : caught(level, caster, region)) {
            MaledictionStrike.strike(caster, target, damage, finish, null, center);
        }
    }

    private static List<LivingEntity> caught(ServerLevel level, ServerPlayer caster, Region region) {
        AABB bounds = region.bounds();
        return level.getEntitiesOfClass(LivingEntity.class, bounds, entity -> MaledictionStrike.mayHarm(caster, entity)
            && (region.contains(entity.getBoundingBox().getCenter()) || region.contains(entity.position())
            || region.contains(entity.getEyePosition())));
    }

    private static Vec3 center(Region region) {
        return region.bounds().getCenter();
    }
}
